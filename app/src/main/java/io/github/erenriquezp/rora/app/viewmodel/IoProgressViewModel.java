package io.github.erenriquezp.rora.app.viewmodel;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/** Progreso de abrir o exportar; solo aparece si la operación tarda. */
public final class IoProgressViewModel<F> {

  private static final long SHOW_AFTER_NANOS = 300_000_000L;

  private final Env<F> env;
  private final BooleanProperty visible = new SimpleBooleanProperty(false);
  private final StringProperty title = new SimpleStringProperty("");
  private final DoubleProperty fraction = new SimpleDoubleProperty(0);
  private Runnable onCancel = () -> {};
  private long generation;

  IoProgressViewModel(Env<F> env) {
    this.env = env;
  }

  public BooleanProperty visibleProperty() {
    return visible;
  }

  public StringProperty titleProperty() {
    return title;
  }

  public DoubleProperty fractionProperty() {
    return fraction;
  }

  /** Empieza una operación; {@code cancel} se llama si el usuario la cancela. */
  public void begin(String text, Runnable cancel) {
    long g = ++generation;
    title.set(text);
    fraction.set(0);
    onCancel = cancel;
    visible.set(false);
    env.scheduler()
        .schedule(
            () ->
                env.publisher()
                    .execute(
                        () -> {
                          if (g == generation) {
                            visible.set(true);
                          }
                        }),
            SHOW_AFTER_NANOS);
  }

  /** Avance entre 0 y 1. */
  public void update(double value) {
    fraction.set(value);
  }

  /** La operación terminó, bien o mal. */
  public void end() {
    generation++;
    visible.set(false);
    onCancel = () -> {};
  }

  public void cancel() {
    Runnable cancel = onCancel;
    end();
    cancel.run();
  }
}
