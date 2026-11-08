package io.github.erenriquezp.rora.app.viewmodel;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** Avisos no bloqueantes. Los errores permanecen hasta cerrarlos; los demás se van solos. */
public final class NotificationsViewModel<F> {

  /** Gravedad del aviso. */
  public enum Kind {
    INFO,
    ERROR
  }

  /** Aviso. {@code details} puede estar vacío. */
  public record Notice(Kind kind, String message, String details) {}

  private static final long INFO_NANOS = 4_000_000_000L;
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
    add(new Notice(Kind.ERROR, message, details == null ? "" : details));
  }

  void info(String message) {
    Notice n = new Notice(Kind.INFO, message, "");
    add(n);
    env.scheduler().schedule(() -> env.publisher().execute(() -> notices.remove(n)), INFO_NANOS);
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
