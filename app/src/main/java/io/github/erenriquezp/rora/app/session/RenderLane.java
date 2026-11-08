package io.github.erenriquezp.rora.app.session;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.CancelledException;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.RenderKey;
import io.github.erenriquezp.rora.core.op.Renderer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Carril de render en segundo plano (sin JavaFX). Ejecuta como máximo <b>un</b> trabajo a la vez.
 *
 * <ul>
 *   <li><b>Último gana</b> entre peticiones interactivas: una nueva sustituye a la pendiente y
 *       cancela la que está en curso, salvo que pidan la misma clave (entonces se unen).
 *   <li>Peticiones <b>protegidas</b> (exportación): no las sustituye nada; van antes que las
 *       interactivas; las de la misma clave se unen.
 *   <li><b>Debounce con arranque inmediato</b>: un trabajo no empieza antes de {@code debounce}
 *       desde el arranque anterior; si el carril está libre y ya pasó ese tiempo, empieza ya.
 *   <li><b>Nunca obsoleto</b>: un trabajo sustituido no se publica; se comprueba al empezar, al
 *       terminar (en el worker) y dentro de la tarea publicada.
 *   <li><b>Errores</b>: se publican al oyente ({@link RenderFailure}) y se registran; un {@link
 *       OutOfMemoryError} vacía la caché. Las cancelaciones por sustitución no se publican.
 * </ul>
 *
 * Tiempo ({@link Scheduler}), ejecutor del worker y publicador ({@code Platform::runLater} en la
 * app) son inyectables: los tests son deterministas.
 */
public final class RenderLane implements AutoCloseable {

  private static final System.Logger LOG = System.getLogger("rora.render");

  /** Recibe el resultado en el hilo del publicador. */
  public interface Listener {
    void onDone(Renderer.Result result);

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
  private Job pendingInteractive;
  private final Deque<Job> pendingProtected = new ArrayDeque<>();
  private Scheduler.Cancellable timer;
  private long lastStart = Long.MIN_VALUE / 2;
  private boolean closed;

  /**
   * Última petición interactiva: una interactiva solo publica si sigue siéndolo (así un resultado
   * ya terminado pero aún sin publicar no se muestra tras una petición más nueva).
   */
  private volatile Job latestInteractive;

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

  private final class Job {
    final Renderer.Request request;
    final RenderKey key;
    final boolean protectedJob;
    final CancellationToken token = CancellationToken.create();
    final List<Listener> listeners = new CopyOnWriteArrayList<>();
    volatile boolean superseded;
    final AtomicBoolean progressQueued = new AtomicBoolean();
    volatile long lastProgress = Long.MIN_VALUE / 2;

    Job(Renderer.Request request, RenderKey key, boolean protectedJob, Listener l) {
      this.request = request;
      this.key = key;
      this.protectedJob = protectedJob;
      listeners.add(l);
    }

    /** Quita {@code l}; si no queda ningún oyente, el trabajo se abandona. */
    Ticket ticketFor(Listener l) {
      return () -> {
        synchronized (RenderLane.this) {
          listeners.remove(l);
          if (!listeners.isEmpty() || superseded) {
            return;
          }
          supersede(this);
          if (pendingInteractive == this) {
            pendingInteractive = null;
          }
          pendingProtected.remove(this);
        }
      };
    }
  }

  /**
   * Encola una petición; devuelve un ticket para abandonarla (solo para este oyente: otros que se
   * hayan unido a la misma clave siguen recibiendo el resultado).
   */
  public Ticket submit(Renderer.Request request, Listener listener, boolean protectedJob) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(listener, "listener");
    RenderKey key = renderer.keyOf(request);
    synchronized (this) {
      if (closed) {
        throw new IllegalStateException("Carril " + name + " cerrado");
      }
      Job join = findSameKey(key);
      if (join != null) {
        join.listeners.add(listener);
        return join.ticketFor(listener);
      }
      Job job = new Job(request, key, protectedJob, listener);
      if (protectedJob) {
        pendingProtected.addLast(job);
      } else {
        if (pendingInteractive != null) {
          supersede(pendingInteractive);
        }
        pendingInteractive = job;
        latestInteractive = job;
        if (running != null && !running.protectedJob) {
          supersede(running);
        }
      }
      scheduleStart();
      return job.ticketFor(listener);
    }
  }

  private Job findSameKey(RenderKey key) {
    if (running != null && !running.superseded && running.key.equals(key)) {
      return running;
    }
    if (pendingInteractive != null && pendingInteractive.key.equals(key)) {
      return pendingInteractive;
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
    if (pendingProtected.isEmpty() && pendingInteractive == null) {
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
      job = pendingInteractive;
      pendingInteractive = null;
    }
    if (job == null) {
      return;
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
        result = renderer.render(job.request, new OpContext(tiles, job.token, progress));
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
      LOG.log(
          job.superseded ? System.Logger.Level.DEBUG : System.Logger.Level.WARNING,
          () -> name + ": fallo en el render " + job.key,
          error);
    }
    if (isObsolete(job)) {
      return;
    }
    Renderer.Result r = result;
    RenderFailure f = error == null ? null : RenderFailure.of(error);
    if (r == null && f == null) {
      return;
    }
    publisher.execute(
        () -> {
          if (isObsolete(job)) {
            return;
          }
          for (Listener l : job.listeners) {
            if (f == null) {
              l.onDone(r);
            } else {
              l.onFailed(f);
            }
          }
        });
  }

  private boolean isObsolete(Job job) {
    return job.superseded || (!job.protectedJob && job != latestInteractive);
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
          if (!isObsolete(job)) {
            for (Listener l : job.listeners) {
              l.onProgress(fraction);
            }
          }
        });
  }

  /** ¿Hay trabajo en curso o pendiente? (para tests y diagnóstico). */
  public synchronized boolean isIdle() {
    return running == null && pendingInteractive == null && pendingProtected.isEmpty();
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
      if (pendingInteractive != null) {
        supersede(pendingInteractive);
        pendingInteractive = null;
      }
      for (Job j : pendingProtected) {
        supersede(j);
      }
      pendingProtected.clear();
    }
  }
}
