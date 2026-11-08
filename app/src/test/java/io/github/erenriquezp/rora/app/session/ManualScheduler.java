package io.github.erenriquezp.rora.app.session;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** {@link Scheduler} con tiempo virtual: las tareas solo corren al llamar a {@link #advance}. */
final class ManualScheduler implements Scheduler {

  private record Task(long due, long seq, Runnable r, boolean[] cancelled) {}

  private long now;
  private long seq;
  private final List<Task> tasks = new ArrayList<>();

  @Override
  public synchronized Cancellable schedule(Runnable task, long delayNanos) {
    boolean[] cancelled = {false};
    tasks.add(new Task(now + Math.max(0, delayNanos), seq++, task, cancelled));
    return () -> {
      synchronized (ManualScheduler.this) {
        cancelled[0] = true;
      }
    };
  }

  @Override
  public synchronized long nanoTime() {
    return now;
  }

  /** Avanza el reloj y ejecuta, en orden, las tareas que vencen. */
  void advance(long nanos) {
    long target;
    synchronized (this) {
      target = now + nanos;
    }
    while (true) {
      Task next;
      synchronized (this) {
        tasks.removeIf(t -> t.cancelled()[0]);
        next =
            tasks.stream()
                .filter(t -> t.due() <= target)
                .min(Comparator.comparingLong(Task::due).thenComparingLong(Task::seq))
                .orElse(null);
        if (next == null) {
          now = target;
          return;
        }
        tasks.remove(next);
        now = next.due();
      }
      next.r().run();
    }
  }

  synchronized int pendingTasks() {
    tasks.removeIf(t -> t.cancelled()[0]);
    return tasks.size();
  }

  static long ms(long m) {
    return m * 1_000_000L;
  }
}
