package io.github.erenriquezp.rora.core.op;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import java.util.Objects;

/** Entorno de ejecución de una operación: hilos, cancelación, progreso y artefactos. */
public record OpContext(
    TileExecutor executor, CancellationToken token, Progress progress, ArtifactLookup artifacts) {

  public OpContext {
    Objects.requireNonNull(executor, "executor");
    Objects.requireNonNull(token, "token");
    Objects.requireNonNull(progress, "progress");
    Objects.requireNonNull(artifacts, "artifacts");
  }

  /** Sin artefactos. */
  public OpContext(TileExecutor executor, CancellationToken token, Progress progress) {
    this(executor, token, progress, ArtifactLookup.NONE);
  }

  /** Un hilo (el llamante), sin cancelación ni progreso. */
  public static OpContext sequential() {
    return new OpContext(TileExecutor.sequential(), CancellationToken.none(), Progress.NONE);
  }

  /** El ejecutor dado, sin cancelación ni progreso. */
  public static OpContext of(TileExecutor executor) {
    return new OpContext(executor, CancellationToken.none(), Progress.NONE);
  }
}
