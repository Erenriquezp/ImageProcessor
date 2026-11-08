package io.github.erenriquezp.rora.app.session;

import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.domain.EqualizeMode;
import io.github.erenriquezp.rora.domain.FilterType;
import io.github.erenriquezp.rora.domain.TextureFilterMode;
import io.github.erenriquezp.rora.service.LegacySteps;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Traduce un filtro de la lista actual y sus parámetros a un paso de receta. Es la misma
 * correspondencia que el {@code switch processFilter} de {@code ImageProcessorApp} con la fachada
 * {@code ImageProcessor}: mismos ids, mismos valores y <b>mismos tipos</b> (los decimales se pasan
 * como el {@code float} que recibía la fachada, así dos posiciones de slider que acaban en el mismo
 * {@code float} producen el mismo paso). {@code FilterSpecsTest} lo comprueba contra la fachada.
 */
public final class FilterSpecs {

  private final OpRegistry registry;

  public FilterSpecs(OpRegistry registry) {
    this.registry = registry;
  }

  /** Paso para {@code type}, o vacío para {@link FilterType#NONE}. */
  public Optional<OpSpec> of(FilterType type, FilterParams p) {
    return switch (type) {
      case NONE -> Optional.empty();
      case GRAYSCALE -> spec("color.grayscale-average", b -> b);
      case NEGATIVE -> spec("color.negative", b -> b);
      case BRIGHTNESS -> spec("color.brightness", b -> b.set("value", p.brightness()));
      case HSV ->
          spec("color.hsv", b -> b.set("saturation", p.saturation()).set("value", p.valueFactor()));
      case FROSTED -> spec("legacy.frosted", b -> b);
      case CIRCULAR_FADE -> spec("artistic.circular-fade", b -> b);
      case ALPHA_GLOBAL -> spec("legacy.alpha-global", b -> b.set("alpha", p.alpha()));
      case RETRO1 -> spec("tone.posterize", b -> b.set("levels", p.retroLevels()));
      case RETRO2 -> {
        List<String> channels = new ArrayList<>(3);
        if (p.r2r()) {
          channels.add("r");
        }
        if (p.r2g()) {
          channels.add("g");
        }
        if (p.r2b()) {
          channels.add("b");
        }
        yield spec(
            "tone.posterize-channels",
            b -> b.set("levels", p.retro2Levels()).set("channels", channels));
      }
      case GRAYSCALE_QUANTIZED ->
          spec("color.grayscale-quantized", b -> b.set("levels", p.grayQuantLevels()));
      case BW_THRESHOLD -> spec("color.threshold-bw", b -> b.set("threshold", p.threshold()));
      case RECOLOR ->
          spec("color.recolor", b -> b.set("tone", rgb8(p.tintR(), p.tintG(), p.tintB())));
      case BUFFER_ACUMULATION_ADD -> motion("add");
      case BUFFER_ACUMULATION_ACUM -> motion("accum");
      case BUFFER_ACUMULATION_MULT -> motion("mult");
      case STRETCH_4_BITS -> spec("legacy.stretch4", b -> b.set("mode", id(p.stretchMode())));
      case CONVOLUTION -> spec(LegacySteps.CONVOLUTION, b -> b.set("kernel", id(p.kernel())));
      case SEPIA -> preset("sepia");
      case COOL_TONE -> preset("cool");
      case WARM_TONE -> preset("warm");
      case POLAROID -> preset("polaroid");
      case KODACHROME -> preset("kodachrome");
      case DEPTH_MAP -> spec("raster.depth-map", b -> b.set("source", id(p.depthSource())));
      case Z_BUFFER ->
          spec(
              "legacy.z-buffer",
              b ->
                  b.set("source", id(p.depthSource()))
                      .set("threshold", p.depthThreshold())
                      .set("plane", rgb8(p.planeR(), p.planeG(), p.planeB())));
      case BITMAP_PIXELATE -> spec("raster.pixelate", b -> b.set("block", p.pixelBlockSize()));
      case RASTER_GRID -> spec("legacy.raster-grid", b -> b.set("block", p.pixelBlockSize()));
      case TEXTURE_SAMPLE ->
          spec(
              "raster.texture-sample",
              b ->
                  b.set("scale", p.textureScale())
                      .set(
                          "filter",
                          p.textureFilter() == TextureFilterMode.BILINEAR
                              ? "bilinear"
                              : "nearest"));
      case DEPTH_INTERPOLATE ->
          spec(
              "legacy.depth-interpolate",
              b ->
                  b.set("source", id(p.depthSource()))
                      .set("near", rgb8(p.planeR(), p.planeG(), p.planeB()))
                      .set("far", rgb8(p.fogR(), p.fogG(), p.fogB())));
      case W_BUFFER ->
          spec(
              "legacy.w-buffer",
              b ->
                  b.set("source", id(p.depthSource()))
                      .set("threshold", p.depthThreshold())
                      .set("plane", rgb8(p.planeR(), p.planeG(), p.planeB())));
      case MULTISAMPLE -> spec("legacy.multisample", b -> b.set("samples", p.multisampleSize()));
      case ALPHA_TEST ->
          spec(
              "legacy.alpha-test",
              b ->
                  b.set("threshold", p.alphaTestThreshold())
                      .set("luminance", p.alphaTestLuminance()));
      case STENCIL_TEST ->
          spec(
              "legacy.stencil-test",
              b ->
                  b.set("pattern", id(p.stencilPattern()))
                      .set("threshold", p.depthThreshold())
                      .set("cell", p.pixelBlockSize()));
      case FRAGMENT_BLEND ->
          spec(
              "fragment.blend-color",
              b ->
                  b.set("mode", id(p.fragmentBlendMode()))
                      .set("color", rgb8(p.planeR(), p.planeG(), p.planeB()))
                      .set("alpha", p.fragmentBlendAlpha()));
      case LOGIC_OP ->
          spec("legacy.logic-op", b -> b.set("op", id(p.logicOp())).set("mask", p.logicMask()));
      case HISTOGRAM_EQUALIZE ->
          spec(
              "tone.equalize",
              b ->
                  b.set("mode", p.equalizeMode() == EqualizeMode.LUMINANCE ? "luminance" : "rgb")
                      .set("factor", p.equalizeIntensity())
                      .set("markBurned", p.equalizeMarkBurned())
                      .set("markDark", p.equalizeMarkDark()));
      case COLOR_ADJUST ->
          spec(
              "point.color-gain",
              b -> b.set("gainR", p.gainR()).set("gainG", p.gainG()).set("gainB", p.gainB()));
      case POINT_INTERPOLATE ->
          spec(
              "legacy.point-interpolate",
              b ->
                  b.set("t", p.lerpFactor())
                      .set("target", rgb8(p.planeR(), p.planeG(), p.planeB())));
      case POINT_EXTRAPOLATE ->
          spec(
              "legacy.point-extrapolate",
              b ->
                  b.set("t", p.extrapolateFactor())
                      .set("target", rgb8(p.planeR(), p.planeG(), p.planeB())));
      case SCALE_BIAS ->
          spec("point.scale-bias", b -> b.set("scale", p.scale()).set("bias", p.bias()));
      case POINT_THRESHOLD ->
          spec(
              "point.soft-threshold",
              b -> b.set("threshold", p.pointThreshold()).set("softWidth", p.softThreshold()));
      case TO_LUMINANCE -> spec("point.luminance", b -> b);
      case POINT_SATURATION -> spec("point.saturation", b -> b.set("factor", p.pointSaturation()));
      case HUE_ROTATE -> spec("point.hue-rotate", b -> b.set("degrees", p.hueDegrees()));
      case COLOR_SPACE -> spec("legacy.color-space", b -> b.set("type", id(p.colorSpace())));
    };
  }

  private Optional<OpSpec> spec(String id, UnaryOperator<Params.Builder> values) {
    Operation op = registry.require(id);
    return Optional.of(OpSpec.of(op, values.apply(Params.builder(op.schema())).build()));
  }

  private Optional<OpSpec> motion(String mode) {
    return spec("accum.motion", b -> b.set("mode", mode).set("samples", 25).set("displacement", 8));
  }

  private Optional<OpSpec> preset(String preset) {
    return spec("color.matrix-preset", b -> b.set("preset", preset));
  }

  private static String id(Enum<?> e) {
    return LegacySteps.enumId(e);
  }

  /**
   * {@code 0xFFRRGGBB}. La UI solo produce canales en 0..255 (de un {@code Color} redondeado); la
   * fachada desviaba al legacy fuera de ese rango, caso que la receta no admite.
   */
  static int rgb8(int r, int g, int b) {
    if (((r | g | b) & ~0xFF) != 0) {
      throw new IllegalArgumentException("Canal fuera de 0..255: " + r + "," + g + "," + b);
    }
    return 0xFF000000 | (r << 16) | (g << 8) | b;
  }
}
