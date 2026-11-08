package io.github.erenriquezp.rora.app.session;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;

/** Ejecutor que encola tareas; el test decide cuándo ejecutarlas (en su propio hilo). */
final class ManualExecutor implements Executor {

  private final Deque<Runnable> queue = new ArrayDeque<>();

  @Override
  public synchronized void execute(Runnable command) {
    queue.addLast(command);
  }

  synchronized int size() {
    return queue.size();
  }

  /** Ejecuta la siguiente tarea; {@code false} si no había. */
  boolean runNext() {
    Runnable r;
    synchronized (this) {
      r = queue.pollFirst();
    }
    if (r == null) {
      return false;
    }
    r.run();
    return true;
  }

  /** Ejecuta hasta vaciar la cola (incluidas las tareas que se encolen mientras tanto). */
  int runAll() {
    int n = 0;
    while (runNext()) {
      n++;
    }
    return n;
  }
}
