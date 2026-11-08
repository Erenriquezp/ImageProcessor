package io.github.erenriquezp.rora.app.viewmodel;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** Avisos no bloqueantes. Los errores permanecen hasta cerrarlos; los demás se van solos. */
public final class NotificationsViewModel<F> {

  /** Gravedad del aviso. */
  public enum Kind {
    INFO,
    WARNING,
    ERROR
  }

  /** Botón opcional de un aviso. */
  public record Action(String label, Runnable run) {}

  /** Aviso. {@code details} puede estar vacío; {@code action} puede ser nula. */
  public record Notice(Kind kind, String message, String details, Action action) {}

  private static final long INFO_NANOS = 4_000_000_000L;
  private static final long WARNING_NANOS = 9_000_000_000L;
  private static final int MAX_VISIBLE = 4;

  private final Env<F> env;
  private final ObservableList<Notice> notices = FXCollections.observableArrayList();

  NotificationsViewModel(Env<F> env) {
    this.env = env;
  }

  public ObservableList<Notice> notices() {
    return notices;
  }

  void error(String message, String details) {
    add(new Notice(Kind.ERROR, message, details == null ? "" : details, null));
  }

  void info(String message) {
    timed(new Notice(Kind.INFO, message, "", null), INFO_NANOS);
  }

  /** Aviso con un botón, que dura algo más para dar tiempo a usarlo. */
  void info(String message, Action action) {
    timed(new Notice(Kind.INFO, message, "", action), WARNING_NANOS);
  }

  void warning(String message) {
    timed(new Notice(Kind.WARNING, message, "", null), WARNING_NANOS);
  }

  private void timed(Notice n, long nanos) {
    add(n);
    env.scheduler().schedule(() -> env.publisher().execute(() -> notices.remove(n)), nanos);
  }

  private void add(Notice n) {
    notices.add(n);
    while (notices.size() > MAX_VISIBLE) {
      notices.remove(0);
    }
  }

  public void dismiss(Notice n) {
    notices.remove(n);
  }
}
