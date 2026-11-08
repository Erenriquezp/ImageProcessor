package io.github.erenriquezp.rora.app.session;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.CancelledException;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Trabajo en segundo plano "último gana" sin JavaFX (blending, histograma, degradado, E/S): como
 * máximo uno en curso y uno pendiente; uno nuevo sustituye al pendiente y cancela (por token) el
 * que está en curso. Un resultado sustituido nunca se publica; los errores sí (y se registran).
 *
 * @param <I> entrada (inmutable)
 * @param <O> resultado (inmutable)
 */
public final class LatestWins<I, O> implements AutoCloseable {

  private static final System.Logger LOG = System.getLogger("rora.work");

  private final String name;
  private final Executor worker;
  private final Executor publisher;
  private final BiFunction<I, CancellationToken, O> work;

  private Job running;
  private Job pending;
  private boolean closed;

  /** Último trabajo pedido: solo él puede publicar (aunque otro ya haya terminado). */
  private volatile Job latest;

  private final class Job {
    final I input;
    final Consumer<O> onResult;
    final Consumer<Throwable> onError;
    final CancellationToken token = CancellationToken.create();
    volatile boolean superseded;

    Job(I input, Consumer<O> onResult, Consumer<Throwable> onError) {
      this.input = input;
      this.onResult = onResult;
      this.onError = onError;
    }

    void supersede() {
      superseded = true;
      token.cancel();
    }
  }

  public LatestWins(
      String name, Executor worker, Executor publisher, BiFunction<I, CancellationToken, O> work) {
    this.name = Objects.requireNonNull(name, "name");
    this.worker = Objects.requireNonNull(worker, "worker");
    this.publisher = Objects.requireNonNull(publisher, "publisher");
    this.work = Objects.requireNonNull(work, "work");
  }

  public synchronized void submit(I input, Consumer<O> onResult, Consumer<Throwable> onError) {
    if (closed) {
      throw new IllegalStateException(name + " cerrado");
    }
    Job job = new Job(input, onResult, onError);
    latest = job;
    if (pending != null) {
      pending.supersede();
    }
    pending = job;
    if (running != null) {
      running.supersede();
    } else {
      startNext();
    }
  }

  private void startNext() {
    Job job = pending;
    pending = null;
    if (job == null) {
      return;
    }
    running = job;
    worker.execute(() -> run(job));
  }

  private void run(Job job) {
    O out = null;
    Throwable error = null;
    if (!job.superseded) {
      try {
        out = work.apply(job.input, job.token);
      } catch (CancelledException e) {
        // sustituido
      } catch (RuntimeException | Error e) {
        error = e;
      }
    }
    synchronized (this) {
      running = null;
      if (!closed) {
        startNext();
      }
    }
    if (error != null) {
      Throwable e = error;
      LOG.log(
          job.superseded ? System.Logger.Level.DEBUG : System.Logger.Level.WARNING,
          () -> name + ": fallo en segundo plano",
          e);
    }
    if (job.superseded || job != latest || (out == null && error == null)) {
      return;
    }
    O result = out;
    Throwable failure = error;
    publisher.execute(
        () -> {
          if (job.superseded || job != latest) {
            return;
          }
          if (failure == null) {
            job.onResult.accept(result);
          } else {
            job.onError.accept(failure);
          }
        });
  }

  /** Abandona el trabajo pendiente y el que está en curso (su resultado no se publicará). */
  public synchronized void cancelAll() {
    if (pending != null) {
      pending.supersede();
      pending = null;
    }
    if (running != null) {
      running.supersede();
    }
  }

  @Override
  public synchronized void close() {
    closed = true;
    cancelAll();
  }
}
