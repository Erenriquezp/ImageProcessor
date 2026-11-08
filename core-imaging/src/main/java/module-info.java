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
  exports io.github.erenriquezp.rora.core.io;

  uses io.github.erenriquezp.rora.core.op.Operation;

  // Añadir una operación = 1 clase + 1 línea aquí.
  provides io.github.erenriquezp.rora.core.op.Operation with
      io.github.erenriquezp.rora.core.ops.accum.MotionAccumulationOp,
      io.github.erenriquezp.rora.core.ops.adjust.AdjustBasicOp,
      io.github.erenriquezp.rora.core.ops.blur.GaussianBlurOp,
      io.github.erenriquezp.rora.core.ops.detail.UnsharpMaskOp,
      io.github.erenriquezp.rora.core.ops.geometry.CropOp,
      io.github.erenriquezp.rora.core.ops.geometry.FlipOp,
      io.github.erenriquezp.rora.core.ops.geometry.ResizeOp,
      io.github.erenriquezp.rora.core.ops.geometry.Rotate90Op,
      io.github.erenriquezp.rora.core.ops.geometry.StraightenOp,
      io.github.erenriquezp.rora.core.ops.analysis.ExposureOp,
      io.github.erenriquezp.rora.core.ops.analysis.HistogramOp,
      io.github.erenriquezp.rora.core.ops.artistic.CircularFadeOp,
      io.github.erenriquezp.rora.core.ops.color.BrightnessOp,
      io.github.erenriquezp.rora.core.ops.color.GrayscaleAverageOp,
      io.github.erenriquezp.rora.core.ops.color.GrayscaleQuantizedOp,
      io.github.erenriquezp.rora.core.ops.color.HsvOp,
      io.github.erenriquezp.rora.core.ops.color.MatrixPresetOp,
      io.github.erenriquezp.rora.core.ops.color.NegativeOp,
      io.github.erenriquezp.rora.core.ops.color.RecolorOp,
      io.github.erenriquezp.rora.core.ops.color.ThresholdBwOp,
      io.github.erenriquezp.rora.core.ops.fragment.BlendColorOp,
      io.github.erenriquezp.rora.core.ops.point.ColorGainOp,
      io.github.erenriquezp.rora.core.ops.point.HueRotateOp,
      io.github.erenriquezp.rora.core.ops.point.LuminanceOp,
      io.github.erenriquezp.rora.core.ops.point.SaturationOp,
      io.github.erenriquezp.rora.core.ops.point.ScaleBiasOp,
      io.github.erenriquezp.rora.core.ops.point.SoftThresholdOp,
      io.github.erenriquezp.rora.core.ops.raster.DepthMapOp,
      io.github.erenriquezp.rora.core.ops.raster.PixelateOp,
      io.github.erenriquezp.rora.core.ops.raster.TextureSampleOp,
      io.github.erenriquezp.rora.core.ops.tone.EqualizeOp,
      io.github.erenriquezp.rora.core.ops.tone.PosterizeChannelsOp,
      io.github.erenriquezp.rora.core.ops.tone.PosterizeOp;
}
