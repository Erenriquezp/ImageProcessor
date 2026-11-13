package io.github.erenriquezp.rora.wallpapers.plan;

import io.github.erenriquezp.rora.wallpapers.geom.FrameRect;
import io.github.erenriquezp.rora.wallpapers.geom.IntRect;
import java.util.Objects;
import java.util.Set;

/**
 * Un destino ya resuelto contra una fuente concreta.
 *
 * @param resolved modo efectivo (nunca {@code AUTO})
 * @param frame encuadre en píxeles de la fuente
 * @param content rectángulo de la salida que ocupa el encuadre
 * @param effectiveScale factor de ampliación ({@code > 1} amplía)
 * @param lostArea fracción de la fuente que queda fuera
 */
public record PlannedTarget(
    Target target,
    Target.FitMode resolved,
    FrameRect frame,
    IntRect content,
    double effectiveScale,
    QualityBadge badge,
    double lostArea,
    Set<Warning> warnings) {

  /** Avisos para la interfaz. */
  public enum Warning {
    /** Un rostro queda cortado por el encuadre. */
    SUBJECT_CUT,
    /** Se amplía tanto que se verá borroso. */
    BLURRY,
    /** Lleva relleno en los márgenes. */
    FILLED,
    /** La resolución del preset no está verificada en una fuente oficial. */
    APPROXIMATE_PRESET,
    /** Una cara cae en buena parte bajo una zona segura (reloj, dock...). */
    SAFE_ZONE_OVERLAP,
    /** El encuadre manual no encajaba con la fuente actual y se volvió a automático. */
    MANUAL_RESET
  }

  public PlannedTarget {
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(resolved, "resolved");
    Objects.requireNonNull(frame, "frame");
    Objects.requireNonNull(content, "content");
    Objects.requireNonNull(badge, "badge");
    warnings = Set.copyOf(warnings);
    if (resolved == Target.FitMode.AUTO) {
      throw new IllegalArgumentException("el modo resuelto no puede ser AUTO");
    }
  }

  public boolean filled() {
    return content.w() != target.width() || content.h() != target.height();
  }
}
