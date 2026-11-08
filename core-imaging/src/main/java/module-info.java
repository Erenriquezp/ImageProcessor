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

  // Añadir una operación = 1 clase + 1 línea aquí.
  provides io.github.erenriquezp.rora.core.op.Operation with
      io.github.erenriquezp.rora.core.ops.point.SaturationOp,
      io.github.erenriquezp.rora.core.ops.raster.PixelateOp,
      io.github.erenriquezp.rora.core.ops.tone.EqualizeOp;
}
