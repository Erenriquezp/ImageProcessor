package io.github.erenriquezp.rora.app.session;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Detector de bloqueos del hilo de interfaz (solo desarrollo: {@code -Drora.fxStallMonitor=true}).
 * Cada {@code tickNanos} envía un latido al hilo; si tarda más de {@code thresholdNanos} en
 * ejecutarse, informa una vez con la traza del hilo (mientras sigue bloqueado) y otra con la
 * duración total (al volver).
 */
public final class StallMonitor implements AutoCloseable {

  /** Intervalo de los latidos. */
  public static final long DEFAULT_TICK_NANOS = 50_000_000L;

  /** Una pausa mayor que esto se registra. */
  public static final long DEFAULT_THRESHOLD_NANOS = 100_000_000L;

  private final Scheduler scheduler;
  private final Executor uiThread;
  private final Supplier<String> uiStack;
  private final Consumer<String> report;
  private final long tickNanos;
  private final long thresholdNanos;

  private Scheduler.Cancellable timer;
  private boolean waiting;
  private boolean reported;
  private long postedAt;
  private boolean closed;

  public StallMonitor(
      Scheduler scheduler,
      Executor uiThread,
      Supplier<String> uiStack,
      Consumer<String> report,
      long tickNanos,
      long thresholdNanos) {
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.uiThread = Objects.requireNonNull(uiThread, "uiThread");
    this.uiStack = Objects.requireNonNull(uiStack, "uiStack");
    this.report = Objects.requireNonNull(report, "report");
    this.tickNanos = tickNanos;
    this.thresholdNanos = thresholdNanos;
  }

  public synchronized void start() {
    if (timer == null && !closed) {
      timer = scheduler.schedule(this::tick, tickNanos);
    }
  }

  private void tick() {
    String message = null;
    synchronized (this) {
      if (closed) {
        return;
      }
      long now = scheduler.nanoTime();
      if (waiting) {
        if (!reported && now - postedAt > thresholdNanos) {
          reported = true;
          message = "Hilo FX bloqueado desde hace " + (now - postedAt) / 1_000_000 + " ms";
        }
      } else {
        waiting = true;
        reported = false;
        postedAt = now;
        uiThread.execute(this::beat);
      }
      timer = scheduler.schedule(this::tick, tickNanos);
    }
    if (message != null) {
      report.accept(message + "\n" + uiStack.get());
    }
  }

  private void beat() {
    long elapsed;
    synchronized (this) {
      waiting = false;
      elapsed = scheduler.nanoTime() - postedAt;
    }
    if (elapsed > thresholdNanos) {
      report.accept("Hilo FX bloqueado " + elapsed / 1_000_000 + " ms");
    }
  }

  @Override
  public synchronized void close() {
    closed = true;
    if (timer != null) {
      timer.cancel();
      timer = null;
    }
  }
}
