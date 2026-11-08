package io.github.erenriquezp.rora.service;

import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.domain.DepthSource;
import io.github.erenriquezp.rora.domain.EqualizeMode;
import io.github.erenriquezp.rora.domain.FragmentBlendMode;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Casos de las operaciones portadas al núcleo: cada uno fija el legacy, la fachada y el id y los
 * parámetros del núcleo, con un nombre reproducible. Incluye neutros, valores típicos, extremos de
 * la UI y valores fuera de rango (el núcleo no recorta: debe comportarse como el legacy). Se amplía
 * lote a lote.
 */
@SuppressWarnings("removal") // el legacy portado está @Deprecated(forRemoval) a propósito
final class PortedCases {

  private PortedCases() {}

  /**
   * @param facadeOp método de la fachada (como en {@code OpCatalog})
   * @param name combinación de parámetros
   * @param legacy implementación heredada
   * @param facade llamada a {@code ImageProcessor} (comprueba el mapeo de parámetros)
   * @param coreId operación del núcleo
   * @param params parámetros del núcleo
   * @param exhaustive se ejecuta también sobre los 2^24 colores ({@link AllColorsEquivalenceTest})
   */
  record Case(
      String facadeOp,
      String name,
      UnaryOperator<BufferedImage> legacy,
      UnaryOperator<BufferedImage> facade,
      String coreId,
      UnaryOperator<Params.Builder> params,
      boolean exhaustive) {
    @Override
    public String toString() {
      return facadeOp + "/" + name;
    }
  }

  static List<Case> all() {
    List<Case> l = new ArrayList<>();
    pilots(l);
    pointOps(l);
    return l;
  }

  private static void add(
      List<Case> l,
      String op,
      String name,
      UnaryOperator<BufferedImage> legacy,
      UnaryOperator<BufferedImage> facade,
      String id,
      UnaryOperator<Params.Builder> params,
      boolean exhaustive) {
    l.add(new Case(op, name, legacy, facade, id, params, exhaustive));
  }

  // ── Pilotos ────────────────────────────────────────────────────────────────

  private static void pilots(List<Case> l) {
    for (float f : new float[] {0f, 0.5f, 1f, 1.2f, 1.3f, 2f, -1f, 3f, Float.NaN}) {
      boolean ex = f == 0f || f == 0.5f || f == 1f || f == 1.3f || f == 2f;
      add(
          l,
          "pointSaturation",
          "factor=" + f,
          i -> PointOpsFilters.pointSaturation(i, f),
          i -> ImageProcessor.pointSaturation(i, f),
          "point.saturation",
          p -> p.set("factor", f),
          ex);
    }
    for (int n : new int[] {0, 1, 2, 3, 7, 8, 64, 100}) {
      add(
          l,
          "bitmapPixelate",
          "block=" + n,
          i -> RasterPipelineFilters.bitmapPixelate(i, n),
          i -> ImageProcessor.bitmapPixelate(i, n),
          "raster.pixelate",
          p -> p.set("block", n),
          n == 8);
    }
    EqualizeMode[] modes = {EqualizeMode.RGB, EqualizeMode.LUMINANCE, null};
    for (EqualizeMode m : modes) {
      for (float f : new float[] {0f, 0.35f, 1f}) {
        equalize(l, m, f, false, false, m == EqualizeMode.LUMINANCE && f == 1f);
        equalize(l, m, f, true, true, m != null && f == 0.35f);
      }
    }
    equalize(l, EqualizeMode.RGB, 0.5f, true, false, false);
    equalize(l, EqualizeMode.LUMINANCE, 1f, false, true, false);
    equalize(l, EqualizeMode.RGB, 0f, true, false, false);
    equalize(l, EqualizeMode.RGB, 1.5f, false, false, false);
    equalize(l, EqualizeMode.LUMINANCE, -0.5f, false, true, false);
    equalize(l, EqualizeMode.LUMINANCE, Float.NaN, false, false, false);
  }

  private static void equalize(
      List<Case> l, EqualizeMode m, float f, boolean burned, boolean dark, boolean ex) {
    String modeId = m == EqualizeMode.LUMINANCE ? "luminance" : "rgb";
    add(
        l,
        "histogramEqualize",
        (m == null ? "null" : m.name()) + ",factor=" + f + ",burned=" + burned + ",dark=" + dark,
        i -> PointOpsFilters.histogramEqualize(i, m, f, burned, dark),
        i -> ImageProcessor.histogramEqualize(i, m, f, burned, dark),
        "tone.equalize",
        p -> p.set("mode", modeId).set("factor", f).set("markBurned", burned).set("markDark", dark),
        ex);
  }

  // ── Lote 1: operaciones puntuales ──────────────────────────────────────────

  private static void pointOps(List<Case> l) {
    add(
        l,
        "grayscale",
        "default",
        ColorFilters::grayscale,
        ImageProcessor::grayscale,
        "color.grayscale-average",
        p -> p,
        true);
    add(
        l,
        "negative",
        "default",
        ColorFilters::negative,
        ImageProcessor::negative,
        "color.negative",
        p -> p,
        true);
    add(
        l,
        "toLuminance",
        "default",
        PointOpsFilters::toLuminance,
        ImageProcessor::toLuminance,
        "point.luminance",
        p -> p,
        true);

    for (int v : new int[] {-300, -100, -1, 0, 40, 100, 300}) {
      add(
          l,
          "brightness",
          "v=" + v,
          i -> ColorFilters.brightness(i, v),
          i -> ImageProcessor.brightness(i, v),
          "color.brightness",
          p -> p.set("value", v),
          v == 40);
    }

    float[][] hsv = {{1f, 1f}, {1.3f, 0.9f}, {0f, 0f}, {2f, 2f}, {0.5f, 1.5f}, {-1f, 3f}};
    for (float[] sv : hsv) {
      float s = sv[0];
      float v = sv[1];
      boolean ex = (s == 1.3f && v == 0.9f) || (s == 0f && v == 0f) || (s == 2f && v == 2f);
      add(
          l,
          "hsv",
          "s=" + s + ",v=" + v,
          i -> ColorFilters.hsv(i, s, v),
          i -> ImageProcessor.hsv(i, s, v),
          "color.hsv",
          p -> p.set("saturation", s).set("value", v),
          ex);
    }

    for (int t : new int[] {-1, 0, 127, 254, 255}) {
      add(
          l,
          "bwThreshold",
          "t=" + t,
          i -> ColorFilters.bwThreshold(i, t),
          i -> ImageProcessor.bwThreshold(i, t),
          "color.threshold-bw",
          p -> p.set("threshold", t),
          t == 127);
    }

    for (int n : new int[] {0, 1, 2, 8, 64, 255, 256}) {
      add(
          l,
          "grayscaleQuantized",
          "n=" + n,
          i -> ColorFilters.grayscaleQuantized(i, n),
          i -> ImageProcessor.grayscaleQuantized(i, n),
          "color.grayscale-quantized",
          p -> p.set("levels", n),
          n == 8);
    }

    add(
        l,
        "sepia",
        "default",
        ColorFilters::sepia,
        ImageProcessor::sepia,
        "color.matrix-preset",
        p -> p.set("preset", "sepia"),
        true);
    add(
        l,
        "coolTone",
        "default",
        ColorFilters::coolTone,
        ImageProcessor::coolTone,
        "color.matrix-preset",
        p -> p.set("preset", "cool"),
        true);
    add(
        l,
        "warmTone",
        "default",
        ColorFilters::warmTone,
        ImageProcessor::warmTone,
        "color.matrix-preset",
        p -> p.set("preset", "warm"),
        true);
    add(
        l,
        "polaroid",
        "default",
        ColorFilters::polaroid,
        ImageProcessor::polaroid,
        "color.matrix-preset",
        p -> p.set("preset", "polaroid"),
        true);
    add(
        l,
        "kodachrome",
        "default",
        ColorFilters::kodachrome,
        ImageProcessor::kodachrome,
        "color.matrix-preset",
        p -> p.set("preset", "kodachrome"),
        true);

    for (int n : new int[] {0, 1, 2, 4, 255, 256, 300}) {
      add(
          l,
          "retro1",
          "levels=" + n,
          i -> ArtisticFilters.retro1(i, n),
          i -> ImageProcessor.retro1(i, n),
          "tone.posterize",
          p -> p.set("levels", n),
          n == 4);
    }

    Object[][] retro2 = {
      {4, true, true, true},
      {2, true, false, false},
      {255, true, true, true},
      {8, false, true, true},
      {4, false, false, false},
      {256, true, true, true},
      {1, false, true, false}
    };
    for (Object[] r : retro2) {
      int n = (Integer) r[0];
      boolean qr = (Boolean) r[1];
      boolean qg = (Boolean) r[2];
      boolean qb = (Boolean) r[3];
      List<String> ch = new ArrayList<>();
      if (qr) {
        ch.add("r");
      }
      if (qg) {
        ch.add("g");
      }
      if (qb) {
        ch.add("b");
      }
      add(
          l,
          "retro2",
          "levels=" + n + ",channels=" + ch,
          i -> ArtisticFilters.retro2(i, n, qr, qg, qb),
          i -> ImageProcessor.retro2(i, n, qr, qg, qb),
          "tone.posterize-channels",
          p -> p.set("levels", n).set("channels", ch),
          n == 4 && qr && qg && qb);
    }

    int[][] tints = {{255, 255, 255}, {200, 120, 60}, {0, 0, 0}, {0, 255, 17}, {300, -5, 10}};
    for (int[] t : tints) {
      add(
          l,
          "recolor",
          "tint=" + t[0] + "," + t[1] + "," + t[2],
          i -> ArtisticFilters.recolor(i, t[0], t[1], t[2]),
          i -> ImageProcessor.recolor(i, t[0], t[1], t[2]),
          "color.recolor",
          // Fuera de 0-255 la fachada usa el legacy; el núcleo recibe el color empaquetado.
          p ->
              CoreBridge.isRgb8(t[0], t[1], t[2])
                  ? p.set("tone", CoreBridge.rgb(t[0], t[1], t[2]))
                  : null,
          t[0] == 200);
    }

    float[][] gains = {{1, 1, 1}, {1.2f, 0.9f, 0.8f}, {0, 2, 2}, {0.5f, 1.5f, 1.0001f}, {-1, 3, 1}};
    for (float[] g : gains) {
      add(
          l,
          "colorAdjust",
          "gains=" + g[0] + "," + g[1] + "," + g[2],
          i -> PointOpsFilters.colorAdjust(i, g[0], g[1], g[2]),
          i -> ImageProcessor.colorAdjust(i, g[0], g[1], g[2]),
          "point.color-gain",
          p -> p.set("gainR", g[0]).set("gainG", g[1]).set("gainB", g[2]),
          g[0] == 1.2f);
    }

    float[][] sb = {{1, 0}, {1.5f, -20}, {3, 128}, {0, -128}, {0.7f, 0.5f}, {-1, 300}};
    for (float[] s : sb) {
      add(
          l,
          "scaleBias",
          "scale=" + s[0] + ",bias=" + s[1],
          i -> PointOpsFilters.scaleBias(i, s[0], s[1]),
          i -> ImageProcessor.scaleBias(i, s[0], s[1]),
          "point.scale-bias",
          p -> p.set("scale", s[0]).set("bias", s[1]),
          s[0] == 1.5f);
    }

    int[][] thr = {{128, 0}, {127, 16}, {0, 64}, {255, 0}, {300, -5}, {-10, 10}, {128, 200}};
    for (int[] t : thr) {
      add(
          l,
          "pointThreshold",
          "t=" + t[0] + ",soft=" + t[1],
          i -> PointOpsFilters.pointThreshold(i, t[0], t[1]),
          i -> ImageProcessor.pointThreshold(i, t[0], t[1]),
          "point.soft-threshold",
          p -> p.set("threshold", t[0]).set("softWidth", t[1]),
          t[0] == 127);
    }

    for (float d : new float[] {-180f, -90f, 0f, 30f, 180f, 359.5f, -720f, 12.3f}) {
      add(
          l,
          "hueRotate",
          "d=" + d,
          i -> PointOpsFilters.hueRotate(i, d),
          i -> ImageProcessor.hueRotate(i, d),
          "point.hue-rotate",
          p -> p.set("degrees", d),
          d == 30f || d == -180f || d == 180f);
    }

    for (FragmentBlendMode m : FragmentBlendMode.values()) {
      for (float a : new float[] {0f, 0.5f, 1f}) {
        fragment(l, m, 200, 100, 50, a, a == 0.5f);
      }
    }
    fragment(l, FragmentBlendMode.MULTIPLY, 0, 0, 0, 0.5f, false);
    fragment(l, FragmentBlendMode.SCREEN, 255, 255, 255, 0.5f, false);
    fragment(l, FragmentBlendMode.SRC_OVER, 10, 20, 30, -1f, false);
    fragment(l, FragmentBlendMode.SRC_OVER, 10, 20, 30, 2f, false);
    fragment(l, FragmentBlendMode.ADD, 300, 0, -40, 0.5f, false); // respaldo legacy

    add(
        l,
        "circularFade",
        "default",
        ArtisticFilters::circularFade,
        ImageProcessor::circularFade,
        "artistic.circular-fade",
        p -> p,
        false);

    for (DepthSource s : DepthSource.values()) {
      add(
          l,
          "depthMap",
          s.name(),
          i -> RasterPipelineFilters.depthMap(i, s),
          i -> ImageProcessor.depthMap(i, s),
          "raster.depth-map",
          p -> p.set("source", CoreBridge.enumId(s)),
          false);
    }
  }

  private static void fragment(
      List<Case> l, FragmentBlendMode m, int r, int g, int b, float a, boolean ex) {
    add(
        l,
        "fragmentBlend",
        m.name() + ",dst=" + r + "," + g + "," + b + ",alpha=" + a,
        i -> FragmentOpsFilters.fragmentBlend(i, m, r, g, b, a),
        i -> ImageProcessor.fragmentBlend(i, m, r, g, b, a),
        "fragment.blend-color",
        p ->
            CoreBridge.isRgb8(r, g, b)
                ? p.set("mode", CoreBridge.enumId(m))
                    .set("color", CoreBridge.rgb(r, g, b))
                    .set("alpha", a)
                : null,
        ex);
  }
}
