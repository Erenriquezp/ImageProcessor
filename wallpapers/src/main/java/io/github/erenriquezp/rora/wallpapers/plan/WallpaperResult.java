package io.github.erenriquezp.rora.wallpapers.plan;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Resultado de una tanda: un desenlace por destino, en el orden del plan. */
public record WallpaperResult(List<Outcome> outcomes) {

  public WallpaperResult {
    outcomes = List.copyOf(outcomes);
  }

  /** Qué pasó con un destino. */
  public sealed interface Outcome permits Written, Failed, Cancelled {
    Target target();
  }

  /** Escrito. */
  public record Written(Target target, Path file, long bytes) implements Outcome {}

  /** Falló; los demás siguieron. */
  public record Failed(Target target, String reason) implements Outcome {
    public Failed {
      Objects.requireNonNull(reason, "reason");
    }
  }

  /** No llegó a escribirse porque se canceló la tanda. */
  public record Cancelled(Target target) implements Outcome {}

  public long written() {
    return outcomes.stream().filter(o -> o instanceof Written).count();
  }

  public long failed() {
    return outcomes.stream().filter(o -> o instanceof Failed).count();
  }

  public long cancelled() {
    return outcomes.stream().filter(o -> o instanceof Cancelled).count();
  }
}
