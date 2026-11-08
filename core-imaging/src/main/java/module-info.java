/**
 * Núcleo de procesamiento de imagen de Rora Studio. Solo depende de {@code java.base}: ni JavaFX ni
 * AWT. Las operaciones se publican como servicios de {@link
 * io.github.erenriquezp.rora.core.op.Operation}.
 */
module io.github.erenriquezp.rora.core {
  exports io.github.erenriquezp.rora.core.image;
  exports io.github.erenriquezp.rora.core.color;
  exports io.github.erenriquezp.rora.core.op;
  exports io.github.erenriquezp.rora.core.exec;

  uses io.github.erenriquezp.rora.core.op.Operation;
}
