package io.github.erenriquezp.rora.app.session;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Destino de una exportación.
 *
 * @param overwriteConfirmed el usuario confirmó sobrescribir
 * @param originalConfirmed el usuario confirmó sobrescribir el archivo original
 * @param original archivo del que procede la imagen
 */
public record ExportTarget(
    Path folder,
    String fileName,
    Collision collision,
    boolean overwriteConfirmed,
    boolean originalConfirmed,
    Optional<Path> original) {

  /** Qué hacer si el destino existe. */
  public enum Collision {
    ASK,
    NUMBER,
    OVERWRITE
  }

  public ExportTarget {
    Objects.requireNonNull(folder, "folder");
    Objects.requireNonNull(fileName, "fileName");
    Objects.requireNonNull(collision, "collision");
    Objects.requireNonNull(original, "original");
  }
}
