package io.github.erenriquezp.rora.app.viewmodel;

import io.github.erenriquezp.rora.app.session.EditSession;
import io.github.erenriquezp.rora.app.session.Scheduler;
import java.util.concurrent.Executor;

/**
 * Agrupa cambios en una entrada de deshacer: un arrastre dura hasta soltar, y una ráfaga de teclas
 * hasta {@value #KEYBOARD_IDLE_MILLIS} ms sin pulsar.
 */
final class GestureTracker {

  static final long KEYBOARD_IDLE_MILLIS = 500;

  private final EditSession<?> session;
  private final Scheduler scheduler;
  private final Executor publisher;
  private Object key;
  private Scheduler.Cancellable idle;

  GestureTracker(EditSession<?> session, Scheduler scheduler, Executor publisher) {
    this.session = session;
    this.scheduler = scheduler;
    this.publisher = publisher;
  }

  /** Clave del gesto en curso; lo abre si no hay ninguno. */
  Object key(boolean keyboard) {
    if (key == null) {
      key = new Object();
    }
    if (keyboard) {
      if (idle != null) {
        idle.cancel();
      }
      Object current = key;
      idle =
          scheduler.schedule(
              () -> publisher.execute(() -> endIf(current)), KEYBOARD_IDLE_MILLIS * 1_000_000L);
    }
    return key;
  }

  private void endIf(Object expected) {
    if (key == expected) {
      end();
    }
  }

  void end() {
    if (idle != null) {
      idle.cancel();
      idle = null;
    }
    if (key != null) {
      key = null;
      session.endGesture();
    }
  }
}
