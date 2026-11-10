package io.github.erenriquezp.rora.app.session.ai;

import io.github.erenriquezp.rora.ai.api.AiEngine;
import io.github.erenriquezp.rora.ai.api.AiEngines;
import io.github.erenriquezp.rora.ai.api.ModelInfo;
import io.github.erenriquezp.rora.ai.api.ModelSource;
import io.github.erenriquezp.rora.ai.api.TileRunner;
import io.github.erenriquezp.rora.ai.store.ArtifactStore;
import io.github.erenriquezp.rora.ai.tile.Tiler;
import io.github.erenriquezp.rora.app.session.Scheduler;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.CancelledException;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure.Reason;
import io.github.erenriquezp.rora.core.op.ArtifactLookup;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/**
 * Único punto desde el que se ejecuta el motor, y solo por una petición explícita. Un trabajo a la
 * vez, en su propio hilo: los carriles de render no comparten nada con él.
 *
 * <p>Hilos: los métodos públicos (salvo {@link #full} y {@link #proxy}, que se llaman desde los
 * workers de render) van en el hilo del publicador. El motor y su sesión solo se tocan en el hilo
 * {@code rora-ai}.
 */
public final class AiJobService implements ArtifactLookup, AutoCloseable {

  /** Estado de un paso de mejora. Se deriva de la clave: no se guarda en ningún sitio. */
  public enum State {
    NOT_RUN,
    RUNNING,
    READY,
    STALE
  }

  /**
   * @param etaSeconds tiempo restante, cuando ya hay tiles medidos
   */
  public record Progress(int done, int total, OptionalDouble etaSeconds) {}

  /** Notificaciones en el hilo del publicador. */
  public interface Listener {
    default void stateChanged() {}

    default void progress(Progress progress) {}

    default void failed(AiFailure failure) {}
  }

  /** Calcula la imagen que entra al motor, en el hilo {@code rora-ai}. */
  @FunctionalInterface
  public interface Input {
    Img render(CancellationToken token);
  }

  /** Recuerda el último tiempo por tile medido en este equipo. */
  public interface Timing {
    OptionalDouble load();

    void save(double secondsPerTile);
  }

  /** Abre el almacén la primera vez que hace falta. */
  @FunctionalInterface
  public interface StoreFactory {
    ArtifactStore open() throws IOException;
  }

  private static final int ETA_WINDOW = 8;
  private static final long IDLE_NANOS = TimeUnit.SECONDS.toNanos(120);

  private final AiEngine engine;
  private final ModelSource model;
  private final ModelInfo info;
  private final StoreFactory storeFactory;
  private final Executor worker;
  private final Executor publisher;
  private final Scheduler scheduler;
  private final Timing timing;
  private final AiLimits.Memory memory;
  private final AutoCloseable workerResource;
  private final List<Listener> listeners = new CopyOnWriteArrayList<>();

  // Estado del hilo publicador.
  private final Map<ArtifactKey, Integer> artifacts = new HashMap<>();
  private Job current;
  private ArtifactKey lastKey;
  private Scheduler.Cancellable idleTimer;
  private int threads;
  private double secondsPerTile;
  private boolean closed;

  // Estado del hilo rora-ai.
  private TileRunner runner;
  private int runnerThreads;

  private volatile ArtifactStore store;

  private static final class Job {
    final ArtifactKey key;
    final CancellationToken token = CancellationToken.create();

    Job(ArtifactKey key) {
      this.key = key;
    }
  }

  public AiJobService(
      AiEngine engine,
      ModelSource model,
      StoreFactory storeFactory,
      Executor worker,
      Executor publisher,
      Scheduler scheduler,
      Timing timing,
      AiLimits.Memory memory,
      int threads,
      AutoCloseable workerResource) {
    this.engine = Objects.requireNonNull(engine, "engine");
    this.model = Objects.requireNonNull(model, "model");
    this.info = model.info();
    this.storeFactory = Objects.requireNonNull(storeFactory, "storeFactory");
    this.worker = Objects.requireNonNull(worker, "worker");
    this.publisher = Objects.requireNonNull(publisher, "publisher");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.timing = Objects.requireNonNull(timing, "timing");
    this.memory = Objects.requireNonNull(memory, "memory");
    this.threads = threads;
    this.workerResource = workerResource;
    this.secondsPerTile = timing.load().orElse(AiLimits.DEFAULT_SECONDS_PER_TILE);
  }

  /** Servicio real: hilo propio, caché de la app y orfandades barridas en segundo plano. */
  public static AiJobService create(Executor publisher, Scheduler scheduler) {
    ExecutorService exec =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "rora-ai");
              t.setDaemon(true);
              return t;
            });
    long cacheMb = Long.getLong("rora.ai.cache.mb", 2048L);
    StoreFactory factory =
        () -> ArtifactStore.open(ArtifactStore.defaultRoot(), cacheMb << 20, 64L << 20);
    exec.execute(() -> ArtifactStore.sweepOrphans(ArtifactStore.defaultRoot()));
    return new AiJobService(
        AiEngines.onnx(),
        AiEngines.bundledModel(),
        factory,
        exec,
        publisher,
        scheduler,
        preferencesTiming(),
        systemMemory(),
        AiEngines.defaultThreads(Runtime.getRuntime().availableProcessors()),
        () -> {
          exec.shutdown();
          exec.awaitTermination(5, TimeUnit.SECONDS);
        });
  }

  private static Timing preferencesTiming() {
    Preferences prefs = Preferences.userNodeForPackage(AiJobService.class);
    return new Timing() {
      @Override
      public OptionalDouble load() {
        double v = prefs.getDouble("secondsPerTile", -1);
        return v > 0 ? OptionalDouble.of(v) : OptionalDouble.empty();
      }

      @Override
      public void save(double seconds) {
        prefs.putDouble("secondsPerTile", seconds);
      }
    };
  }

  private static AiLimits.Memory systemMemory() {
    return new AiLimits.Memory() {
      @Override
      public long freeHeapBytes() {
        Runtime r = Runtime.getRuntime();
        return r.maxMemory() - (r.totalMemory() - r.freeMemory());
      }

      @Override
      public long freePhysicalBytes() {
        return ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class)
            .getFreeMemorySize();
      }
    };
  }

  public void addListener(Listener l) {
    listeners.add(Objects.requireNonNull(l, "listener"));
  }

  public void removeListener(Listener l) {
    listeners.remove(l);
  }

  public ModelInfo modelInfo() {
    return info;
  }

  /** Identificador del modelo, para la procedencia; la interfaz no ve el motor. */
  public String modelId() {
    return info.id();
  }

  /** Versión del modelo, para la procedencia. */
  public String modelVersion() {
    return info.version();
  }

  /** Hilos de la próxima sesión del motor. */
  public int threads() {
    return threads;
  }

  public void setThreads(int value) {
    threads = Math.max(1, value);
  }

  // ── Consultas ─────────────────────────────────────────────────────────────

  /** Identificador del resultado para {@code key}, o 0 si no hay. */
  public int artifactFor(ArtifactKey key) {
    Integer id = artifacts.get(key);
    return id == null ? 0 : id;
  }

  public State state(ArtifactKey key) {
    if (current != null && current.key.equals(key)) {
      return State.RUNNING;
    }
    if (artifactFor(key) != 0) {
      return State.READY;
    }
    return lastKey != null ? State.STALE : State.NOT_RUN;
  }

  public boolean isRunning() {
    return current != null;
  }

  /** Tiempo previsto para una entrada de este tamaño. No depende de la escala de salida. */
  public double estimateSeconds(int width, int height) {
    return tiler().tileCount(width, height) * secondsPerTile;
  }

  public int tiles(int width, int height) {
    return tiler().tileCount(width, height);
  }

  public AiLimits.Verdict verdict(int width, int height, int scale, boolean fullIntensity) {
    return AiLimits.check(width, height, scale, fullIntensity, false, memory);
  }

  private Tiler tiler() {
    return new Tiler(info.tile(), info.overlap(), info.scale());
  }

  // ── Órdenes ───────────────────────────────────────────────────────────────

  /**
   * Lanza la mejora. No hace nada si ya hay un trabajo o si el resultado existe.
   *
   * @return si se ha lanzado
   */
  public boolean start(ArtifactKey key, boolean fullIntensity, Input input) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(input, "input");
    if (closed || current != null || artifactFor(key) != 0) {
      return false;
    }
    if (idleTimer != null) {
      idleTimer.cancel();
      idleTimer = null;
    }
    Job job = new Job(key);
    current = job;
    int sessionThreads = threads;
    worker.execute(() -> run(job, fullIntensity, input, sessionThreads));
    fireState();
    return true;
  }

  public void cancel() {
    if (current != null) {
      current.token.cancel();
    }
  }

  /** La clave vigente ha cambiado: un trabajo sobre otra clave ya no sirve. */
  public void keyChanged(Optional<ArtifactKey> key) {
    if (current != null && !key.filter(current.key::equals).isPresent()) {
      current.token.cancel();
    }
  }

  /** Artefactos que usa la receta actual: no se expulsan. */
  public void retain(Set<ArtifactKey> keys) {
    Set<Integer> ids =
        keys.stream()
            .map(artifacts::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toUnmodifiableSet());
    worker.execute(
        () -> {
          ArtifactStore s = store;
          if (s != null) {
            s.setInUse(ids);
          }
        });
  }

  /** Se cierra la imagen: fuera su trabajo y todos sus resultados. */
  public void clear() {
    cancel();
    current = null;
    lastKey = null;
    artifacts.clear();
    worker.execute(
        () -> {
          ArtifactStore s = store;
          if (s != null) {
            s.clear();
          }
        });
    fireState();
  }

  // ── Búsqueda desde el renderer ─────────────────────────────────────────────

  @Override
  public Optional<Img> full(int id) {
    ArtifactStore s = store;
    return s == null ? Optional.empty() : s.full(id);
  }

  @Override
  public Optional<Img> proxy(int id, int width, int height) {
    ArtifactStore s = store;
    return s == null ? Optional.empty() : s.proxy(id, width, height);
  }

  // ── Hilo rora-ai ──────────────────────────────────────────────────────────

  private void run(Job job, boolean fullIntensity, Input input, int sessionThreads) {
    try {
      Img src = input.render(job.token);
      job.token.throwIfCancelled();
      checkMemory(src, job.key.scale(), fullIntensity);
      TileRunner r = runner(sessionThreads);
      Eta eta = new Eta();
      Img out =
          tiler()
              .enhance(
                  src,
                  job.key.scale(),
                  r,
                  job.token,
                  (done, total) -> {
                    long now = scheduler.nanoTime();
                    OptionalDouble seconds = eta.tick(now, (int) (total - done));
                    publisher.execute(
                        () -> fireProgress(job, new Progress((int) done, (int) total, seconds)));
                  });
      job.token.throwIfCancelled();
      int id = openStore().put(out);
      double perTile = eta.tiles() > 0 ? eta.seconds() / eta.tiles() : secondsPerTile;
      publisher.execute(() -> finished(job, id, perTile));
    } catch (CancelledException e) {
      publisher.execute(() -> ended(job));
    } catch (AiFailure e) {
      failed(job, e);
    } catch (IOException e) {
      failed(job, new AiFailure(Reason.FAILURE, "No se pudo guardar el resultado", e));
    } catch (OutOfMemoryError e) {
      closeRunner();
      failed(job, new AiFailure(Reason.OUT_OF_MEMORY, "Sin memoria", e));
    } catch (RuntimeException e) {
      failed(job, new AiFailure(Reason.FAILURE, "Fallo inesperado", e));
    }
  }

  private void checkMemory(Img src, int scale, boolean fullIntensity) throws AiFailure {
    boolean alpha = false;
    for (int y = 0; y < src.height() && !alpha; y++) {
      for (int x = 0; x < src.width() && !alpha; x++) {
        alpha = src.argb(x, y) >>> 24 != 255;
      }
    }
    AiLimits.Verdict v =
        AiLimits.check(src.width(), src.height(), scale, fullIntensity, alpha, memory);
    if (v != AiLimits.Verdict.OK) {
      throw new AiFailure(Reason.OUT_OF_MEMORY, "Límite superado: " + v, null);
    }
  }

  private TileRunner runner(int sessionThreads) throws AiFailure {
    if (runner == null || runnerThreads != sessionThreads) {
      closeRunner();
      runner = engine.open(model, sessionThreads);
      runnerThreads = sessionThreads;
    }
    return runner;
  }

  private void closeRunner() {
    if (runner != null) {
      runner.close();
      runner = null;
    }
  }

  private ArtifactStore openStore() throws IOException {
    if (store == null) {
      store = storeFactory.open();
    }
    return store;
  }

  private void failed(Job job, AiFailure failure) {
    publisher.execute(
        () -> {
          boolean mine = current == job;
          ended(job);
          if (mine) {
            for (Listener l : listeners) {
              l.failed(failure);
            }
          }
        });
  }

  /** Media móvil de los últimos tiles. */
  private static final class Eta {
    private final Deque<Long> window = new ArrayDeque<>();
    private long start = -1;
    private long last = -1;
    private int tiles;

    /** La primera llamada solo fija el origen: aún no se ha medido nada. */
    OptionalDouble tick(long now, int remaining) {
      if (last >= 0) {
        window.addLast(now - last);
        if (window.size() > ETA_WINDOW) {
          window.removeFirst();
        }
        tiles++;
      } else {
        start = now;
      }
      last = now;
      if (window.isEmpty()) {
        return OptionalDouble.empty();
      }
      double sum = 0;
      for (long d : window) {
        sum += d;
      }
      return OptionalDouble.of(sum / window.size() * remaining / 1e9);
    }

    long total() {
      return last < 0 ? 0 : last - start;
    }

    double seconds() {
      return total() / 1e9;
    }

    int tiles() {
      return tiles;
    }
  }

  // ── Publicador ────────────────────────────────────────────────────────────

  private void fireProgress(Job job, Progress p) {
    if (current == job) {
      for (Listener l : listeners) {
        l.progress(p);
      }
    }
  }

  private void finished(Job job, int id, double perTile) {
    if (current != job) {
      discard(id);
      return;
    }
    artifacts.put(job.key, id);
    lastKey = job.key;
    secondsPerTile = perTile;
    timing.save(perTile);
    ended(job);
  }

  private void discard(int id) {
    worker.execute(
        () -> {
          ArtifactStore s = store;
          if (s != null) {
            s.remove(id);
          }
        });
  }

  private void ended(Job job) {
    if (current != job) {
      return;
    }
    current = null;
    if (!closed) {
      idleTimer = scheduler.schedule(() -> worker.execute(this::releaseIfIdle), IDLE_NANOS);
    }
    fireState();
  }

  private void releaseIfIdle() {
    publisher.execute(
        () -> {
          if (current == null) {
            worker.execute(this::closeRunner);
          }
        });
  }

  private void fireState() {
    for (Listener l : listeners) {
      l.stateChanged();
    }
  }

  /** Cancela, libera el motor y borra los resultados; espera al hilo si es el real. */
  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    cancel();
    current = null;
    if (idleTimer != null) {
      idleTimer.cancel();
    }
    worker.execute(
        () -> {
          closeRunner();
          ArtifactStore s = store;
          if (s != null) {
            s.close();
          }
        });
    if (workerResource != null) {
      try {
        workerResource.close();
      } catch (Exception e) {
        // Lo que quede se limpiará como huérfano en el próximo arranque.
      }
    }
  }
}
