package io.github.erenriquezp.rora.wallpapers.plan;

import io.github.erenriquezp.rora.wallpapers.geom.FrameRect;
import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset;
import io.github.erenriquezp.rora.wallpapers.preset.SafeZone;
import io.github.erenriquezp.rora.wallpapers.render.FillSpec;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Un destino: dispositivo (o tamaño personalizado), modo de encuadre, relleno y encuadre manual.
 *
 * @param id identificador único en la tanda (el del preset, o el del preset girado)
 * @param manualFrame encuadre manual normalizado a la fuente ({@code [0, 1]}); vacío = automático
 */
public record Target(
    String id,
    String name,
    DevicePreset.Group group,
    int width,
    int height,
    FitMode fit,
    FillSpec fill,
    Optional<FrameRect> manualFrame,
    List<SafeZone> safeZones,
    boolean approximate) {

  /** Cómo se lleva la fuente al destino. */
  public enum FitMode {
    /** Cubrir salvo que se corte una cara o se pierda más del 80 % del área. */
    AUTO,
    /** Recortar para llenar el destino. */
    COVER,
    /** Fuente entera y relleno en los márgenes. */
    CONTAIN
  }

  public Target {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(group, "group");
    Objects.requireNonNull(fit, "fit");
    Objects.requireNonNull(fill, "fill");
    Objects.requireNonNull(manualFrame, "manualFrame");
    safeZones = List.copyOf(safeZones);
    if (width < DevicePreset.MIN_SIDE
        || height < DevicePreset.MIN_SIDE
        || width > DevicePreset.MAX_SIDE
        || height > DevicePreset.MAX_SIDE) {
      throw new IllegalArgumentException("dimensiones fuera de rango: " + width + "x" + height);
    }
  }

  /** Destino de un preset, en modo automático y con el relleno por defecto. */
  public static Target of(DevicePreset p) {
    return new Target(
        p.id(),
        p.name(),
        p.group(),
        p.width(),
        p.height(),
        FitMode.AUTO,
        FillSpec.blur(),
        Optional.empty(),
        p.safeZones(),
        p.status() == DevicePreset.Status.APPROXIMATE);
  }

  /**
   * El mismo destino girado 90°: ancho y alto intercambiados. Las zonas seguras se conservan
   * normalizadas (el sistema vuelve a poner el reloj arriba) y el encuadre manual se descarta.
   */
  public Target rotated() {
    String rid = id.endsWith("@rot") ? id.substring(0, id.length() - 4) : id + "@rot";
    return new Target(
        rid, name, group, height, width, fit, fill, Optional.empty(), safeZones, approximate);
  }

  public Target withFit(FitMode mode) {
    return new Target(
        id, name, group, width, height, mode, fill, manualFrame, safeZones, approximate);
  }

  public Target withFill(FillSpec f) {
    return new Target(id, name, group, width, height, fit, f, manualFrame, safeZones, approximate);
  }

  public Target withManualFrame(Optional<FrameRect> frame) {
    return new Target(id, name, group, width, height, fit, fill, frame, safeZones, approximate);
  }
}
