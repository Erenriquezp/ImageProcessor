package io.github.erenriquezp.rora.lab.session;

import io.github.erenriquezp.rora.app.session.*;
import io.github.erenriquezp.rora.domain.FilterType;
import io.github.erenriquezp.rora.service.BufferAcumulacion_LOAD;
import io.github.erenriquezp.rora.service.ImageProcessor;
import java.awt.image.BufferedImage;

/**
 * Referencia de exactitud de la fase 2: copia <b>literal</b> del {@code switch processFilter} de
 * {@code ImageProcessorApp} (fin de la fase 1) sobre la fachada {@code ImageProcessor}. El render
 * FULL de una receta debe ser idéntico a encadenar estas llamadas desde el {@code BufferedImage}
 * cargado.
 */
final class ReferenceFilters {

  private ReferenceFilters() {}

  @SuppressWarnings("removal")
  static BufferedImage process(FilterType filter, BufferedImage base, FilterParams p) {
    return switch (filter) {
      case GRAYSCALE -> ImageProcessor.grayscale(base);
      case NEGATIVE -> ImageProcessor.negative(base);
      case BRIGHTNESS -> ImageProcessor.brightness(base, p.brightness());
      case HSV -> ImageProcessor.hsv(base, p.saturation(), p.valueFactor());
      case FROSTED -> ImageProcessor.frosted(base);
      case CIRCULAR_FADE -> ImageProcessor.circularFade(base);
      case ALPHA_GLOBAL -> ImageProcessor.alphaGlobal(base, p.alpha());
      case RETRO1 -> ImageProcessor.retro1(base, p.retroLevels());
      case RETRO2 -> ImageProcessor.retro2(base, p.retro2Levels(), p.r2r(), p.r2g(), p.r2b());
      case GRAYSCALE_QUANTIZED -> ImageProcessor.grayscaleQuantized(base, p.grayQuantLevels());
      case BW_THRESHOLD -> ImageProcessor.bwThreshold(base, p.threshold());
      case RECOLOR -> ImageProcessor.recolor(base, p.tintR(), p.tintG(), p.tintB());
      case BUFFER_ACUMULATION_ADD ->
          ImageProcessor.bufferAccumulation(base, BufferAcumulacion_LOAD.Mode.ADD);
      case BUFFER_ACUMULATION_ACUM ->
          ImageProcessor.bufferAccumulation(base, BufferAcumulacion_LOAD.Mode.ACUM);
      case BUFFER_ACUMULATION_MULT ->
          ImageProcessor.bufferAccumulation(base, BufferAcumulacion_LOAD.Mode.MULT);
      case STRETCH_4_BITS -> ImageProcessor.stretch4Bits(base, p.stretchMode());
      case CONVOLUTION -> ImageProcessor.convolution(base, p.kernel());
      case SEPIA -> ImageProcessor.sepia(base);
      case COOL_TONE -> ImageProcessor.coolTone(base);
      case WARM_TONE -> ImageProcessor.warmTone(base);
      case POLAROID -> ImageProcessor.polaroid(base);
      case KODACHROME -> ImageProcessor.kodachrome(base);
      case DEPTH_MAP -> ImageProcessor.depthMap(base, p.depthSource());
      case Z_BUFFER ->
          ImageProcessor.zBuffer(
              base, p.depthSource(), p.depthThreshold(), p.planeR(), p.planeG(), p.planeB());
      case BITMAP_PIXELATE -> ImageProcessor.bitmapPixelate(base, p.pixelBlockSize());
      case RASTER_GRID -> ImageProcessor.rasterGrid(base, p.pixelBlockSize());
      case TEXTURE_SAMPLE ->
          ImageProcessor.textureSample(base, p.textureScale(), p.textureFilter());
      case DEPTH_INTERPOLATE ->
          ImageProcessor.depthInterpolate(
              base,
              p.depthSource(),
              p.planeR(),
              p.planeG(),
              p.planeB(),
              p.fogR(),
              p.fogG(),
              p.fogB());
      case W_BUFFER ->
          ImageProcessor.wBuffer(
              base, p.depthSource(), p.depthThreshold(), p.planeR(), p.planeG(), p.planeB());
      case MULTISAMPLE -> ImageProcessor.multisample(base, p.multisampleSize());
      case ALPHA_TEST ->
          ImageProcessor.alphaTest(base, p.alphaTestThreshold(), p.alphaTestLuminance());
      case STENCIL_TEST ->
          ImageProcessor.stencilTest(
              base, p.stencilPattern(), p.depthThreshold(), p.pixelBlockSize());
      case FRAGMENT_BLEND ->
          ImageProcessor.fragmentBlend(
              base,
              p.fragmentBlendMode(),
              p.planeR(),
              p.planeG(),
              p.planeB(),
              p.fragmentBlendAlpha());
      case LOGIC_OP -> ImageProcessor.logicOp(base, p.logicOp(), p.logicMask());
      case HISTOGRAM_EQUALIZE ->
          ImageProcessor.histogramEqualize(
              base,
              p.equalizeMode(),
              p.equalizeIntensity(),
              p.equalizeMarkBurned(),
              p.equalizeMarkDark());
      case COLOR_ADJUST -> ImageProcessor.colorAdjust(base, p.gainR(), p.gainG(), p.gainB());
      case POINT_INTERPOLATE ->
          ImageProcessor.pointInterpolate(base, p.lerpFactor(), p.planeR(), p.planeG(), p.planeB());
      case POINT_EXTRAPOLATE ->
          ImageProcessor.pointExtrapolate(
              base, p.extrapolateFactor(), p.planeR(), p.planeG(), p.planeB());
      case SCALE_BIAS -> ImageProcessor.scaleBias(base, p.scale(), p.bias());
      case POINT_THRESHOLD ->
          ImageProcessor.pointThreshold(base, p.pointThreshold(), p.softThreshold());
      case TO_LUMINANCE -> ImageProcessor.toLuminance(base);
      case POINT_SATURATION -> ImageProcessor.pointSaturation(base, p.pointSaturation());
      case HUE_ROTATE -> ImageProcessor.hueRotate(base, p.hueDegrees());
      case COLOR_SPACE -> ImageProcessor.colorSpaceConvert(base, p.colorSpace());
      case NONE -> base;
    };
  }
}
