package io.github.erenriquezp.rora.app.session;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Reloj y temporizador inyectables. En producción, {@link #system()}; en los tests, un planificador
 * manual con tiempo virtual (sin {@code sleep} ni reloj real).
 */
public interface Scheduler {

  /** Tarea programada que se puede anular. */
  interface Cancellable {
    void cancel();
  }

  /** Ejecuta {@code task} dentro de {@code delayNanos} (en el hilo del planificador). */
  Cancellable schedule(Runnable task, long delayNanos);

  /** Tiempo monótono en nanosegundos. */
  long nanoTime();

  /** Planificador real: un hilo daemon {@code rora-scheduler}. */
  static Scheduler system() {
    return SystemScheduler.INSTANCE;
  }

  /** Implementación con un {@link ScheduledExecutorService} compartido. */
  final class SystemScheduler implements Scheduler {
    static final SystemScheduler INSTANCE = new SystemScheduler();

    private final ScheduledExecutorService exec =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "rora-scheduler");
              t.setDaemon(true);
              return t;
            });

    private SystemScheduler() {}

    @Override
    public Cancellable schedule(Runnable task, long delayNanos) {
      ScheduledFuture<?> f = exec.schedule(task, Math.max(0, delayNanos), TimeUnit.NANOSECONDS);
      return () -> f.cancel(false);
    }

    @Override
    public long nanoTime() {
      return System.nanoTime();
    }
  }
}
