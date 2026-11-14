package io.github.erenriquezp.rora.app.session;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.CancelledException;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.RenderKey;
import io.github.erenriquezp.rora.core.op.Renderer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Carril de render en segundo plano (sin JavaFX). Ejecuta como máximo <b>un</b> trabajo a la vez.
 *
 * <ul>
 *   <li><b>Último gana por propósito</b> ({@link Slot}): una petición sustituye a la anterior de su
 *       misma ranura y la cancela si estaba en curso. Ranuras distintas (zoom, diagnóstico,
 *       cuentagotas…) no se sustituyen entre sí: esperan su turno, por prioridad. La cola está
 *       acotada por el número de ranuras: nunca hay duplicados.
 *   <li>Peticiones con la misma clave <b>se unen</b> al mismo render, sea cual sea su ranura; cada
 *       oyente conserva su ranura, así que sustituir una no afecta a las demás.
 *   <li>Peticiones <b>protegidas</b> (exportación): no las sustituye nada y van primero.
 *   <li><b>Debounce con arranque inmediato</b>: un trabajo no empieza antes de {@code debounce}
 *       desde el arranque anterior; si el carril está libre y ya pasó ese tiempo, empieza ya.
 *   <li><b>Nunca obsoleto</b>: un oyente sustituido no recibe nada; se comprueba antes de preparar
 *       (en el worker) y dentro de la tarea publicada.
 *   <li><b>Errores</b>: se publican al oyente ({@link RenderFailure}) y se registran; un {@link
 *       OutOfMemoryError} vacía la caché. Las cancelaciones por sustitución no se publican.
 * </ul>
 *
 * Tiempo ({@link Scheduler}), ejecutor del worker y publicador ({@code Platform::runLater} en la
 * app) son inyectables: los tests son deterministas.
 */
public final class RenderLane implements AutoCloseable {

  private static final System.Logger LOG = System.getLogger("rora.render");

  /**
   * Propósito de una petición interactiva.
   *
   * @param name identidad de la ranura
   * @param priority las de mayor prioridad arrancan antes
   */
  public record Slot(String name, int priority) {
    /** Ranura única de las peticiones sin propósito declarado. */
    public static final Slot DEFAULT = new Slot("default", 0);
  }

  /** Recibe el resultado en el hilo del publicador. */
  public interface Listener {
    void onDone(Renderer.Result result);

    /**
     * Se ejecuta en el <b>worker</b>, tras el render y antes de publicar (p. ej. convertir a una
     * imagen de JavaFX fuera del hilo FX). Su valor llega a {@link #onDone(Renderer.Result,
     * Object)}. No se llama si el resultado ya es obsoleto.
     */
    default Object prepare(Renderer.Result result) {
      return null;
    }

    /**
     * Resultado y lo preparado en el worker; por defecto llama a {@link #onDone(Renderer.Result)}.
     */
    default void onDone(Renderer.Result result, Object prepared) {
      onDone(result);
    }

    default void onFailed(RenderFailure failure) {}

    /** Fracción completada en [0, 1], a ≤ 30 Hz. */
    default void onProgress(double fraction) {}
  }

  /** Permite al llamante abandonar una petición (p. ej. un FULL que ya no necesita). */
  public interface Ticket {
    void cancel();
  }

  private static final long PROGRESS_MIN_NANOS = 33_000_000L;

  private final String name;
  private final Renderer renderer;
  private final TileExecutor tiles;
  private final Executor worker;
  private final Scheduler scheduler;
  private final Executor publisher;
  private final long debounceNanos;

  // Estado protegido por "this".
  private Job running;
  private final Map<Slot, Job> pendingBySlot = new LinkedHashMap<>();
  private final Deque<Job> pendingProtected = new ArrayDeque<>();
  private Scheduler.Cancellable timer;
  private long lastStart = Long.MIN_VALUE / 2;
  private boolean closed;
  private long seqCounter;

  /**
   * Secuencia de la última petición de cada ranura: un oyente solo recibe el resultado si sigue
   * siendo la última de la suya (así un resultado ya terminado pero aún sin publicar no se muestra
   * tras una petición más nueva). Números, no trabajos: no retiene oyentes ni resultados.
   */
  private final Map<Slot, Long> latestSeq = new ConcurrentHashMap<>();

  public RenderLane(
      String name,
      Renderer renderer,
      TileExecutor tiles,
      Executor worker,
      Scheduler scheduler,
      Executor publisher,
      long debounceNanos) {
    this.name = Objects.requireNonNull(name, "name");
    this.renderer = Objects.requireNonNull(renderer, "renderer");
    this.tiles = Objects.requireNonNull(tiles, "tiles");
    this.worker = Objects.requireNonNull(worker, "worker");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.publisher = Objects.requireNonNull(publisher, "publisher");
    if (debounceNanos < 0) {
      throw new IllegalArgumentException("debounce=" + debounceNanos);
    }
    this.debounceNanos = debounceNanos;
  }

  /** Un oyente con su ranura ({@code null} si es protegido) y su secuencia. */
  private record Entry(Listener listener, Slot slot, long seq) {}

  private final class Job {
    final Renderer.Request request;
    final RenderKey key;
    final Slot slot;
    final CancellationToken token = CancellationToken.create();
    final List<Entry> entries = new CopyOnWriteArrayList<>();
    volatile boolean superseded;
    final AtomicBoolean progressQueued = new AtomicBoolean();
    volatile long lastProgress = Long.MIN_VALUE / 2;

    Job(Renderer.Request request, RenderKey key, Slot slot) {
      this.request = request;
      this.key = key;
      this.slot = slot;
    }
  }

  /** Quita un oyente; si no queda ninguno, el trabajo se abandona. */
  private void removeEntry(Job job, Entry entry) {
    job.entries.remove(entry);
    if (job.entries.isEmpty() && !job.superseded) {
      supersede(job);
      if (job.slot != null && pendingBySlot.get(job.slot) == job) {
        pendingBySlot.remove(job.slot);
      }
      pendingProtected.remove(job);
    }
  }

  /** Petición interactiva en la ranura por defecto, o protegida. */
  public Ticket submit(Renderer.Request request, Listener listener, boolean protectedJob) {
    return submitInternal(request, listener, protectedJob ? null : Slot.DEFAULT);
  }

  /**
   * Petición interactiva en {@code slot}. Devuelve un ticket para abandonarla (solo para este
   * oyente: otros unidos a la misma clave siguen recibiendo el resultado).
   */
  public Ticket submit(Renderer.Request request, Listener listener, Slot slot) {
    return submitInternal(request, listener, Objects.requireNonNull(slot, "slot"));
  }

  private Ticket submitInternal(Renderer.Request request, Listener listener, Slot slot) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(listener, "listener");
    RenderKey key = renderer.keyOf(request);
    synchronized (this) {
      if (closed) {
        throw new IllegalStateException("Carril " + name + " cerrado");
      }
      if (slot != null) {
        // Sustituye lo anterior de esta ranura (salvo que sea la misma clave: se une abajo).
        dropSlot(running, slot, key);
        dropSlot(pendingBySlot.get(slot), slot, key);
      }
      Job target = findSameKey(key);
      long seq = ++seqCounter;
      if (target == null) {
        target = new Job(request, key, slot);
        if (slot == null) {
          pendingProtected.addLast(target);
        } else {
          pendingBySlot.put(slot, target);
        }
      } else if (slot != null) {
        // Misma clave y misma ranura: ambos oyentes son actuales, comparten secuencia.
        for (Entry e : target.entries) {
          if (slot.equals(e.slot())) {
            seq = e.seq();
          }
        }
      }
      if (slot != null) {
        latestSeq.put(slot, seq);
      }
      Entry entry = new Entry(listener, slot, seq);
      target.entries.add(entry);
      scheduleStart();
      Job j = target;
      return () -> {
        synchronized (RenderLane.this) {
          removeEntry(j, entry);
        }
      };
    }
  }

  private void dropSlot(Job job, Slot slot, RenderKey newKey) {
    if (job == null || job.key.equals(newKey)) {
      return;
    }
    for (Entry e : job.entries) {
      if (slot.equals(e.slot())) {
        removeEntry(job, e);
      }
    }
  }

  private Job findSameKey(RenderKey key) {
    if (running != null && !running.superseded && running.key.equals(key)) {
      return running;
    }
    for (Job j : pendingBySlot.values()) {
      if (j.key.equals(key)) {
        return j;
      }
    }
    for (Job j : pendingProtected) {
      if (j.key.equals(key)) {
        return j;
      }
    }
    return null;
  }

  private void supersede(Job j) {
    j.superseded = true;
    j.token.cancel();
  }

  private void scheduleStart() {
    if (closed || running != null || timer != null) {
      return;
    }
    if (pendingProtected.isEmpty() && pendingBySlot.isEmpty()) {
      return;
    }
    long now = scheduler.nanoTime();
    long due = lastStart + debounceNanos;
    if (now >= due) {
      startNext(now);
    } else {
      timer = scheduler.schedule(this::onTimer, due - now);
    }
  }

  private void onTimer() {
    synchronized (this) {
      timer = null;
      scheduleStart();
    }
  }

  private void startNext(long now) {
    Job job = pendingProtected.pollFirst();
    if (job == null) {
      Slot best = null;
      for (Slot s : pendingBySlot.keySet()) {
        if (best == null || s.priority() > best.priority()) {
          best = s;
        }
      }
      if (best == null) {
        return;
      }
      job = pendingBySlot.remove(best);
    }
    running = job;
    lastStart = now;
    Job started = job;
    worker.execute(() -> run(started));
  }

  private void run(Job job) {
    Renderer.Result result = null;
    Throwable error = null;
    if (!job.superseded) {
      try {
        Progress progress = (done, total) -> progress(job, done, total);
        result =
            renderer.render(
                job.request, new OpContext(tiles, job.token, progress, renderer.artifacts()));
      } catch (CancelledException e) {
        // Sustituido o abandonado: nada que publicar.
      } catch (OutOfMemoryError e) {
        renderer.cache().clear();
        error = e;
      } catch (RuntimeException | Error e) {
        error = e;
      }
    }
    synchronized (this) {
      if (running == job) {
        running = null;
      }
      scheduleStart();
    }
    if (error != null) {
      Throwable logged = error;
      LOG.log(
          job.superseded ? System.Logger.Level.DEBUG : System.Logger.Level.WARNING,
          () -> name + ": fallo en el render " + describe(job.key),
          logged);
    }
    if (job.superseded) {
      return;
    }
    Renderer.Result r = result;
    RenderFailure f = error == null ? null : RenderFailure.of(error);
    if (r == null && f == null) {
      return;
    }
    List<Entry> targets = new ArrayList<>();
    for (Entry e : job.entries) {
      if (isCurrent(e)) {
        targets.add(e);
      }
    }
    Object[] prepared = new Object[targets.size()];
    RenderFailure[] prepareFailed = new RenderFailure[targets.size()];
    if (r != null) {
      for (int i = 0; i < targets.size(); i++) {
        try {
          prepared[i] = targets.get(i).listener().prepare(r);
        } catch (RuntimeException | Error e) {
          LOG.log(
              System.Logger.Level.WARNING,
              () -> name + ": fallo al preparar " + describe(job.key),
              e);
          prepareFailed[i] = RenderFailure.of(e);
        }
      }
    }
    publisher.execute(
        () -> {
          for (int i = 0; i < targets.size(); i++) {
            Entry e = targets.get(i);
            if (job.superseded || !job.entries.contains(e) || !isCurrent(e)) {
              continue;
            }
            if (f != null) {
              e.listener().onFailed(f);
            } else if (prepareFailed[i] != null) {
              e.listener().onFailed(prepareFailed[i]);
            } else {
              e.listener().onDone(r, prepared[i]);
            }
          }
        });
  }

  /** ¿Sigue siendo la última petición de su ranura? (las protegidas siempre) */
  private boolean isCurrent(Entry e) {
    return e.slot() == null || Long.valueOf(e.seq()).equals(latestSeq.get(e.slot()));
  }

  private void progress(Job job, long done, long total) {
    long now = scheduler.nanoTime();
    if (total <= 0 || now - job.lastProgress < PROGRESS_MIN_NANOS) {
      return;
    }
    if (!job.progressQueued.compareAndSet(false, true)) {
      return;
    }
    job.lastProgress = now;
    double fraction = Math.min(1.0, (double) done / total);
    publisher.execute(
        () -> {
          job.progressQueued.set(false);
          if (job.superseded) {
            return;
          }
          for (Entry e : job.entries) {
            if (isCurrent(e)) {
              e.listener().onProgress(fraction);
            }
          }
        });
  }

  /** ¿Sin trabajo en curso ni pendiente? (para tests y diagnóstico). */
  public synchronized boolean isIdle() {
    return running == null && pendingBySlot.isEmpty() && pendingProtected.isEmpty();
  }

  /** Deja de aceptar peticiones y cancela las pendientes y la que está en curso. */
  @Override
  public void close() {
    synchronized (this) {
      if (closed) {
        return;
      }
      closed = true;
      if (timer != null) {
        timer.cancel();
        timer = null;
      }
      if (running != null) {
        supersede(running);
      }
      for (Job j : pendingBySlot.values()) {
        supersede(j);
      }
      pendingBySlot.clear();
      for (Job j : pendingProtected) {
        supersede(j);
      }
      pendingProtected.clear();
    }
  }

  /**
   * Lo que se puede registrar de una clave: nivel e ids de los pasos. Nunca los parámetros, que son
   * contenido de la receta del usuario.
   */
  static String describe(RenderKey key) {
    if (key == null) {
      return "[]";
    }
    return key.level().getClass().getSimpleName()
        + " "
        + key.steps().stream().map(OpSpec::opId).toList();
  }
}
