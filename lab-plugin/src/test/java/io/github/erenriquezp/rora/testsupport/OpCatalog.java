package io.github.erenriquezp.rora.testsupport;

import io.github.erenriquezp.rora.domain.ColorSpaceType;
import io.github.erenriquezp.rora.domain.ConvolutionKernel;
import io.github.erenriquezp.rora.domain.DepthSource;
import io.github.erenriquezp.rora.domain.EqualizeMode;
import io.github.erenriquezp.rora.domain.FragmentBlendMode;
import io.github.erenriquezp.rora.domain.LogicOpType;
import io.github.erenriquezp.rora.domain.StencilPattern;
import io.github.erenriquezp.rora.domain.StretchMode;
import io.github.erenriquezp.rora.domain.TextureFilterMode;
import io.github.erenriquezp.rora.service.BufferAcumulacion_LOAD;
import io.github.erenriquezp.rora.service.ImageProcessor;
import java.util.ArrayList;
import java.util.List;

/**
 * Catálogo de operaciones por nivel (ver docs/TESTING.md). Los rangos de parámetros salen de los
 * controles de {@code EditorFilterPane}.
 *
 * <ul>
 *   <li>A: el cálculo sobrevivirá igual; se fija con golden exacto.
 *   <li>B: el comportamiento cambiará a propósito; solo propiedades.
 *   <li>C: se eliminará o pasará al Laboratorio; solo humo.
 * </ul>
 */
public final class OpCatalog {

  /**
   * Operaciones que alteran el alpha de la entrada (observado: promedian, convolucionan o
   * descartan).
   */
  private static final java.util.Set<String> ALPHA_CHANGING =
      java.util.Set.of(
          "bitmapPixelate", "bufferAccumulation", "convolution", "multisample", "textureSample");

  private static List<OpCase> mark(List<OpCase> l) {
    l.replaceAll(oc -> ALPHA_CHANGING.contains(oc.op()) ? oc.changesAlpha() : oc);
    return l;
  }

  private OpCatalog() {}

  private static OpCase c(
      String op, String name, java.util.function.UnaryOperator<java.awt.image.BufferedImage> fn) {
    return OpCase.of(op, name, fn);
  }

  // ── Nivel A ───────────────────────────────────────────────────────────────

  public static List<OpCase> levelA() {
    List<OpCase> l = new ArrayList<>();
    l.add(c("negative", "default", ImageProcessor::negative));
    l.add(c("toLuminance", "default", ImageProcessor::toLuminance));

    l.add(c("retro1", "neutral-256", i -> ImageProcessor.retro1(i, 256)).neutral(0));
    l.add(c("retro1", "typical-4", i -> ImageProcessor.retro1(i, 4)));
    l.add(c("retro1", "extreme-2", i -> ImageProcessor.retro1(i, 2)));

    l.add(c("recolor", "white-tint", i -> ImageProcessor.recolor(i, 255, 255, 255)));
    l.add(c("recolor", "typical", i -> ImageProcessor.recolor(i, 200, 120, 60)));
    l.add(c("recolor", "extreme-black", i -> ImageProcessor.recolor(i, 0, 0, 0)));

    l.add(c("bitmapPixelate", "min-2", i -> ImageProcessor.bitmapPixelate(i, 2)));
    l.add(c("bitmapPixelate", "typical-8", i -> ImageProcessor.bitmapPixelate(i, 8)));
    l.add(c("bitmapPixelate", "max-64", i -> ImageProcessor.bitmapPixelate(i, 64)));

    l.add(c("colorAdjust", "neutral", i -> ImageProcessor.colorAdjust(i, 1f, 1f, 1f)).neutral(0));
    l.add(c("colorAdjust", "typical", i -> ImageProcessor.colorAdjust(i, 1.2f, 0.9f, 0.8f)));
    l.add(c("colorAdjust", "extreme", i -> ImageProcessor.colorAdjust(i, 0f, 2f, 2f)));

    l.add(c("scaleBias", "neutral", i -> ImageProcessor.scaleBias(i, 1f, 0f)).neutral(0));
    l.add(c("scaleBias", "typical", i -> ImageProcessor.scaleBias(i, 1.5f, -20f)));
    l.add(c("scaleBias", "extreme-max", i -> ImageProcessor.scaleBias(i, 3f, 128f)));
    l.add(c("scaleBias", "extreme-min", i -> ImageProcessor.scaleBias(i, 0f, -128f)));

    l.add(c("pointThreshold", "hard-128", i -> ImageProcessor.pointThreshold(i, 128, 0)));
    l.add(c("pointThreshold", "soft-127-16", i -> ImageProcessor.pointThreshold(i, 127, 16)));
    l.add(c("pointThreshold", "extreme-0-64", i -> ImageProcessor.pointThreshold(i, 0, 64)));
    l.add(c("pointThreshold", "extreme-255-0", i -> ImageProcessor.pointThreshold(i, 255, 0)));

    l.add(c("pointSaturation", "neutral", i -> ImageProcessor.pointSaturation(i, 1f)).neutral(1));
    l.add(c("pointSaturation", "typical", i -> ImageProcessor.pointSaturation(i, 1.2f)));
    l.add(c("pointSaturation", "extreme-0", i -> ImageProcessor.pointSaturation(i, 0f)));
    l.add(c("pointSaturation", "extreme-2", i -> ImageProcessor.pointSaturation(i, 2f)));
    return mark(l);
  }

  // ── Nivel B ───────────────────────────────────────────────────────────────

  public static List<OpCase> levelB() {
    List<OpCase> l = new ArrayList<>();
    l.add(c("grayscale", "default", ImageProcessor::grayscale));

    for (int v : new int[] {-100, 0, 40, 100}) {
      OpCase oc = c("brightness", "v" + v, i -> ImageProcessor.brightness(i, v));
      l.add(v == 0 ? oc.neutral(0) : oc);
    }

    float[][] hsv = {{1f, 1f}, {1.3f, 0.9f}, {0f, 0f}, {2f, 2f}};
    for (float[] p : hsv) {
      OpCase oc = c("hsv", "s" + p[0] + "-v" + p[1], i -> ImageProcessor.hsv(i, p[0], p[1]));
      l.add(p[0] == 1f && p[1] == 1f ? oc.neutral(1) : oc);
    }

    for (int t : new int[] {0, 127, 255}) {
      l.add(c("bwThreshold", "t" + t, i -> ImageProcessor.bwThreshold(i, t)));
    }
    for (int n : new int[] {2, 8, 64}) {
      l.add(c("grayscaleQuantized", "n" + n, i -> ImageProcessor.grayscaleQuantized(i, n)));
    }

    l.add(c("sepia", "default", ImageProcessor::sepia));
    l.add(c("coolTone", "default", ImageProcessor::coolTone));
    l.add(c("warmTone", "default", ImageProcessor::warmTone));
    l.add(c("polaroid", "default", ImageProcessor::polaroid));
    l.add(c("kodachrome", "default", ImageProcessor::kodachrome));

    l.add(c("circularFade", "default", ImageProcessor::circularFade).changesAlpha());

    l.add(c("retro2", "all-channels-4", i -> ImageProcessor.retro2(i, 4, true, true, true)));
    l.add(c("retro2", "r-only-2", i -> ImageProcessor.retro2(i, 2, true, false, false)));
    // levels=255 NO es identidad (paso 255/254); el spinner de la UI llega a 255 como maximo
    l.add(c("retro2", "max-255", i -> ImageProcessor.retro2(i, 255, true, true, true)));

    for (ConvolutionKernel k : ConvolutionKernel.values()) {
      l.add(c("convolution", k.name().toLowerCase(), i -> ImageProcessor.convolution(i, k)));
    }
    for (BufferAcumulacion_LOAD.Mode m : BufferAcumulacion_LOAD.Mode.values()) {
      l.add(
          c(
              "bufferAccumulation",
              m.name().toLowerCase(),
              i -> ImageProcessor.bufferAccumulation(i, m)));
    }
    for (DepthSource s : DepthSource.values()) {
      l.add(c("depthMap", s.name().toLowerCase(), i -> ImageProcessor.depthMap(i, s)));
    }
    for (TextureFilterMode m : TextureFilterMode.values()) {
      for (float sc : new float[] {0.25f, 1.5f, 4f}) {
        l.add(
            c(
                "textureSample",
                m.name().toLowerCase() + "-s" + sc,
                i -> ImageProcessor.textureSample(i, sc, m)));
      }
    }
    for (FragmentBlendMode m : FragmentBlendMode.values()) {
      for (float a : new float[] {0f, 0.5f, 1f}) {
        l.add(
            c(
                "fragmentBlend",
                m.name().toLowerCase() + "-a" + a,
                i -> ImageProcessor.fragmentBlend(i, m, 200, 100, 50, a)));
      }
    }
    for (EqualizeMode m : EqualizeMode.values()) {
      OpCase neutral =
          c(
              "histogramEqualize",
              m.name().toLowerCase() + "-f0",
              i -> ImageProcessor.histogramEqualize(i, m, 0f, false, false));
      l.add(neutral.neutral(1));
      l.add(
          c(
              "histogramEqualize",
              m.name().toLowerCase() + "-f1",
              i -> ImageProcessor.histogramEqualize(i, m, 1f, false, false)));
      l.add(
          c(
              "histogramEqualize",
              m.name().toLowerCase() + "-f1-marks",
              i -> ImageProcessor.histogramEqualize(i, m, 1f, true, true)));
    }
    for (float d : new float[] {-180f, 0f, 30f, 180f}) {
      OpCase oc = c("hueRotate", "d" + d, i -> ImageProcessor.hueRotate(i, d));
      l.add(d == 0f ? oc.neutral(1) : oc);
    }
    return mark(l);
  }

  // ── Nivel C (solo humo) ───────────────────────────────────────────────────

  public static List<OpCase> levelC() {
    List<OpCase> l = new ArrayList<>();
    for (int a : new int[] {0, 128, 255}) {
      l.add(c("alphaGlobal", "a" + a, i -> ImageProcessor.alphaGlobal(i, a)).changesAlpha());
    }
    l.add(c("frosted", "default", ImageProcessor::frosted).changesAlpha());
    for (StretchMode m : StretchMode.values()) {
      l.add(c("stretch4Bits", m.name().toLowerCase(), i -> ImageProcessor.stretch4Bits(i, m)));
    }
    for (DepthSource s : DepthSource.values()) {
      l.add(
          c("zBuffer", s.name().toLowerCase(), i -> ImageProcessor.zBuffer(i, s, 128, 10, 20, 30)));
      l.add(
          c("wBuffer", s.name().toLowerCase(), i -> ImageProcessor.wBuffer(i, s, 128, 10, 20, 30)));
      l.add(
          c(
              "depthInterpolate",
              s.name().toLowerCase(),
              i -> ImageProcessor.depthInterpolate(i, s, 255, 0, 0, 0, 0, 255)));
    }
    for (int b : new int[] {2, 8, 64}) {
      l.add(c("rasterGrid", "b" + b, i -> ImageProcessor.rasterGrid(i, b)));
    }
    for (int n : new int[] {2, 3, 8}) {
      l.add(c("multisample", "n" + n, i -> ImageProcessor.multisample(i, n)));
    }
    for (boolean lum : new boolean[] {false, true}) {
      for (int t : new int[] {0, 128, 255}) {
        l.add(
            c(
                    "alphaTest",
                    (lum ? "lum" : "alpha") + "-t" + t,
                    i -> ImageProcessor.alphaTest(i, t, lum))
                .changesAlpha());
      }
    }
    for (StencilPattern p : StencilPattern.values()) {
      l.add(
          c("stencilTest", p.name().toLowerCase(), i -> ImageProcessor.stencilTest(i, p, 128, 8))
              .changesAlpha());
    }
    for (LogicOpType o : LogicOpType.values()) {
      l.add(c("logicOp", o.name().toLowerCase(), i -> ImageProcessor.logicOp(i, o, 128)));
    }
    for (float t : new float[] {0f, 0.5f, 1f}) {
      l.add(c("pointInterpolate", "t" + t, i -> ImageProcessor.pointInterpolate(i, t, 255, 0, 0)));
    }
    for (float t : new float[] {-1f, 1.5f, 2f}) {
      l.add(c("pointExtrapolate", "t" + t, i -> ImageProcessor.pointExtrapolate(i, t, 255, 0, 0)));
    }
    for (ColorSpaceType t : ColorSpaceType.values()) {
      l.add(
          c(
              "colorSpaceConvert",
              t.name().toLowerCase(),
              i -> ImageProcessor.colorSpaceConvert(i, t)));
    }
    return mark(l);
  }

  public static List<OpCase> all() {
    List<OpCase> l = new ArrayList<>(levelA());
    l.addAll(levelB());
    l.addAll(levelC());
    return l;
  }
}
