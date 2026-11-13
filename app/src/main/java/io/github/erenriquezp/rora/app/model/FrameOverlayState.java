package io.github.erenriquezp.rora.app.model;

import java.util.List;
import java.util.Objects;

/**
 * Lo que dibuja la superposición de encuadre del visor: el rectángulo (normalizado a la imagen),
 * las zonas seguras (normalizadas al rectángulo) y si se puede editar. Sin JavaFX.
 *
 * @param editable si se muestran las asas y se puede arrastrar
 * @param thirds si se dibuja la regla de tercios
 */
public record FrameOverlayState(
    CropModel frame, List<Zone> zones, boolean editable, boolean thirds) {

  /** Zona segura dentro del rectángulo, en {@code [0, 1]} respecto a él. */
  public record Zone(String label, double x, double y, double w, double h) {
    public Zone {
      Objects.requireNonNull(label, "label");
    }
  }

  public FrameOverlayState {
    Objects.requireNonNull(frame, "frame");
    zones = List.copyOf(zones);
  }

  /** Recorte editable con tercios y sin zonas (la herramienta Recortar). */
  public static FrameOverlayState editable(CropModel frame) {
    return new FrameOverlayState(frame, List.of(), true, true);
  }
}
