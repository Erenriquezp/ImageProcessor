package io.github.erenriquezp.rora.wallpapers.plan;

import io.github.erenriquezp.rora.wallpapers.subject.Subject;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Todos los destinos resueltos contra una fuente.
 *
 * @param requiredEnhanceScale mejora con IA sugerida (2 o 4), si algún destino amplía demasiado
 */
public record WallpaperPlan(
    int sourceWidth,
    int sourceHeight,
    List<Subject> subjects,
    List<PlannedTarget> targets,
    OptionalInt requiredEnhanceScale) {

  public WallpaperPlan {
    subjects = List.copyOf(subjects);
    targets = List.copyOf(targets);
    Objects.requireNonNull(requiredEnhanceScale, "requiredEnhanceScale");
  }
}
