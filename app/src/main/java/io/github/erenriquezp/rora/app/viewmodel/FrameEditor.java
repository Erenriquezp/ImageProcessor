package io.github.erenriquezp.rora.app.viewmodel;

import io.github.erenriquezp.rora.app.model.CropModel;
import io.github.erenriquezp.rora.app.model.FrameOverlayState;
import javafx.beans.property.ReadOnlyObjectProperty;

/**
 * Herramienta que muestra un rectángulo de encuadre sobre el visor (Recortar, Wallpapers). El visor
 * dibuja el de la primera herramienta cuyo estado no es {@code null}.
 */
public interface FrameEditor {

  /** Estado a dibujar; {@code null} si la herramienta no está abierta. */
  ReadOnlyObjectProperty<FrameOverlayState> overlayProperty();

  /** El usuario movió o redimensionó el rectángulo. */
  void setFrame(CropModel frame);

  /** Terminó un arrastre. */
  default void frameGestureEnded() {}
}
