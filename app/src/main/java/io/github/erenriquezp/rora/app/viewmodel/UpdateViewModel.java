package io.github.erenriquezp.rora.app.viewmodel;

import io.github.erenriquezp.rora.app.diag.AppInfo;
import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.update.ReleaseFeed;
import io.github.erenriquezp.rora.app.update.UpdateChecker;
import io.github.erenriquezp.rora.app.update.UpdateResult;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * "Buscar actualizaciones": una consulta solo cuando el usuario la pide, y el resultado en un
 * diálogo. Si hay versión nueva, abre su página en el navegador; nunca descarga ni instala.
 */
public final class UpdateViewModel {

  /** Estado del diálogo. */
  public enum State {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    NEW_VERSION,
    FAILED
  }

  private final Messages m;
  private final Executor publisher;
  private final Executor background;
  private final UpdateChecker checker;
  private final AppInfo info;
  private final Consumer<String> browser;

  private final BooleanProperty visible = new SimpleBooleanProperty(false);
  private final ObjectProperty<State> state = new SimpleObjectProperty<>(State.IDLE);
  private final StringProperty message = new SimpleStringProperty("");
  private final BooleanProperty majorChange = new SimpleBooleanProperty(false);
  private String page = ReleaseFeed.RELEASES_PAGE;
  private long generation;

  public UpdateViewModel(
      Messages messages,
      Executor publisher,
      Executor background,
      UpdateChecker checker,
      AppInfo info,
      Consumer<String> browser) {
    this.m = messages;
    this.publisher = publisher;
    this.background = background;
    this.checker = checker;
    this.info = info;
    this.browser = browser;
  }

  /** Con {@code -Drora.updates=off} la entrada no aparece y {@link #check()} no hace nada. */
  public boolean enabled() {
    return checker.enabled();
  }

  public BooleanProperty visibleProperty() {
    return visible;
  }

  public ObjectProperty<State> stateProperty() {
    return state;
  }

  public StringProperty messageProperty() {
    return message;
  }

  public BooleanProperty majorChangeProperty() {
    return majorChange;
  }

  /** Lanza la consulta en segundo plano; una a la vez. */
  public void check() {
    if (!checker.enabled() || state.get() == State.CHECKING) {
      return;
    }
    long g = ++generation;
    visible.set(true);
    state.set(State.CHECKING);
    majorChange.set(false);
    message.set(m.get("ui.update.checking"));
    String current = info.version();
    background.execute(
        () -> {
          UpdateResult r = checker.check(current);
          publisher.execute(
              () -> {
                if (g == generation) {
                  apply(r);
                }
              });
        });
  }

  private void apply(UpdateResult r) {
    switch (r) {
      case UpdateResult.UpToDate u -> {
        state.set(State.UP_TO_DATE);
        message.set(m.format("ui.update.upToDate", u.current().toString()));
      }
      case UpdateResult.NewVersion n -> {
        state.set(State.NEW_VERSION);
        page = n.page();
        majorChange.set(n.majorChange());
        String current = info.numericVersion().isEmpty() ? info.version() : info.numericVersion();
        if (n.majorChange()) {
          message.set(m.format("ui.update.major", n.version().toString(), current));
        } else if (n.published().isEmpty()) {
          message.set(m.format("ui.update.availableUndated", n.version().toString(), current));
        } else {
          message.set(
              m.format("ui.update.available", n.version().toString(), current, n.published()));
        }
      }
      case UpdateResult.Failed f -> {
        state.set(State.FAILED);
        message.set(
            switch (f.reason()) {
              case NO_NETWORK -> m.get("ui.update.noNetwork");
              case TIMEOUT -> m.get("ui.update.timeout");
              case RATE_LIMITED -> m.get("ui.update.rateLimited");
              case INVALID_RESPONSE, DISABLED -> m.get("ui.update.invalid");
            });
      }
    }
  }

  /** Abre la página de la release nueva (validada: solo del propio proyecto). */
  public void openRelease() {
    browser.accept(page);
  }

  /** Abre la lista de releases del proyecto. */
  public void openReleasesPage() {
    browser.accept(ReleaseFeed.RELEASES_PAGE);
  }

  /** Cierra el diálogo; una consulta en curso se descarta al terminar. */
  public void close() {
    generation++;
    visible.set(false);
    state.set(State.IDLE);
  }
}
