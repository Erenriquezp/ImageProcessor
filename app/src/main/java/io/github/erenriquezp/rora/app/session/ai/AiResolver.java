package io.github.erenriquezp.rora.app.session.ai;

import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.RenderLevel;
import io.github.erenriquezp.rora.core.op.Renderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Escribe en la receta efectiva qué resultado usa cada paso de mejora, igual que {@code
 * SourceFormats} rellena el formato. La receta del usuario no cambia: el parámetro solo existe en
 * la copia que se ejecuta.
 */
public final class AiResolver {

  public static final String ENHANCE = "ai.enhance";

  private final Renderer renderer;
  private final AiJobService jobs;

  public AiResolver(Renderer renderer, AiJobService jobs) {
    this.renderer = renderer;
    this.jobs = jobs;
  }

  /** Un paso de mejora de la receta efectiva. */
  public record Located(int index, ArtifactKey key, List<OpSpec> prefix) {}

  /** El paso de mejora activo, si lo hay. */
  public Optional<Located> locate(Renderer.SourceImage source, List<OpSpec> steps) {
    for (int i = 0; i < steps.size(); i++) {
      if (steps.get(i).enabled() && steps.get(i).opId().equals(ENHANCE)) {
        List<OpSpec> prefix = steps.subList(0, i).stream().filter(OpSpec::enabled).toList();
        return Optional.of(new Located(i, keyOf(source, prefix, steps.get(i)), prefix));
      }
    }
    return Optional.empty();
  }

  private ArtifactKey keyOf(Renderer.SourceImage source, List<OpSpec> prefix, OpSpec step) {
    return new ArtifactKey(
        renderer.keyOf(new Renderer.Request(source, prefix, prefix.size(), RenderLevel.FULL)),
        jobs.modelInfo().sha256(),
        "X4".equals(step.params().getEnum("scale")) ? 4 : 2,
        ArtifactKey.CPU);
  }

  /** {@code steps} con el resultado asignado a su paso de mejora. */
  public List<OpSpec> apply(Renderer.SourceImage source, List<OpSpec> steps) {
    Optional<Located> found = locate(source, steps);
    if (found.isEmpty()) {
      return steps;
    }
    Located l = found.get();
    int id = jobs.artifactFor(l.key());
    if (id == 0) {
      return steps;
    }
    OpSpec step = steps.get(l.index());
    List<OpSpec> out = new ArrayList<>(steps);
    out.set(l.index(), step.withParams(step.params().toBuilder().set("artifact", id).build()));
    return List.copyOf(out);
  }
}
