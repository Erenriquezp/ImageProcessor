package io.github.erenriquezp.rora.app.viewmodel;

import io.github.erenriquezp.rora.app.model.ControlSpec;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

/** Valor actual de un control; la vista lo enlaza, y los cambios del usuario llegan al paso. */
public final class ControlState {

  /** Quien aplica los cambios del usuario a la receta. */
  public interface Sink {
    void changed(ControlState control, boolean keyboard);

    void gestureEnded();
  }

  private final ControlSpec spec;
  private final Sink sink;
  private final ObjectProperty<Object> value;
  private final BooleanProperty modified = new SimpleBooleanProperty(false);

  ControlState(ControlSpec spec, Object initial, Sink sink) {
    this.spec = Objects.requireNonNull(spec, "spec");
    this.sink = Objects.requireNonNull(sink, "sink");
    this.value = new SimpleObjectProperty<>(normalize(initial));
    updateModified();
  }

  public ControlSpec spec() {
    return spec;
  }

  public ObjectProperty<Object> valueProperty() {
    return value;
  }

  public Object value() {
    return value.get();
  }

  public ReadOnlyBooleanProperty modifiedProperty() {
    return modified;
  }

  /** Cambio del usuario con el puntero: se agrupa en un gesto hasta {@link #endGesture()}. */
  public void userSet(Object raw) {
    set(raw, false);
  }

  private void set(Object raw, boolean keyboard) {
    Object next = normalize(raw);
    if (!Objects.equals(next, value.get())) {
      value.set(next);
      updateModified();
      sink.changed(this, keyboard);
    }
  }

  public void endGesture() {
    sink.gestureEnded();
  }

  /** Vuelve al valor neutro, como una sola entrada de deshacer. */
  public void reset() {
    set(spec.resetValue(), false);
    sink.gestureEnded();
  }

  /** Paso de teclado en un deslizador; {@code fine} con Mayús. */
  public void nudge(int direction, boolean fine) {
    if (spec instanceof ControlSpec.Slider s) {
      set(s.stepped(((Number) value.get()).doubleValue(), direction, fine), true);
    }
  }

  /** Actualiza el valor desde la receta, sin generar cambios. */
  void sync(Object v) {
    Object next = normalize(v);
    if (!Objects.equals(next, value.get())) {
      value.set(next);
    }
    updateModified();
  }

  private void updateModified() {
    modified.set(!Objects.equals(value.get(), normalize(spec.resetValue())));
  }

  private Object normalize(Object raw) {
    if (spec instanceof ControlSpec.Slider s && raw instanceof Number n) {
      double v = s.normalize(n.doubleValue());
      return s.integer() ? (Object) (int) Math.round(v) : (Object) v;
    }
    return raw;
  }
}
