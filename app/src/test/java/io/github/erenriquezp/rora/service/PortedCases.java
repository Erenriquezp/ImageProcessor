package io.github.erenriquezp.rora.service;

import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.domain.EqualizeMode;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Casos de las operaciones portadas al núcleo: cada uno fija el legacy, la fachada y el id y los
 * parámetros del núcleo, con un nombre reproducible. Se amplía lote a lote.
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
   */
  record Case(
      String facadeOp,
      String name,
      UnaryOperator<BufferedImage> legacy,
      UnaryOperator<BufferedImage> facade,
      String coreId,
      UnaryOperator<Params.Builder> params) {
    @Override
    public String toString() {
      return facadeOp + "/" + name;
    }
  }

  static List<Case> all() {
    List<Case> l = new ArrayList<>();
    pilots(l);
    return l;
  }

  // ── Pilotos ────────────────────────────────────────────────────────────────

  private static void pilots(List<Case> l) {
    for (float f : new float[] {0f, 0.5f, 1f, 1.2f, 1.3f, 2f, -1f, 3f, Float.NaN}) {
      l.add(
          new Case(
              "pointSaturation",
              "factor=" + f,
              i -> PointOpsFilters.pointSaturation(i, f),
              i -> ImageProcessor.pointSaturation(i, f),
              "point.saturation",
              p -> p.set("factor", f)));
    }
    for (int n : new int[] {0, 1, 2, 3, 7, 8, 64, 100}) {
      l.add(
          new Case(
              "bitmapPixelate",
              "block=" + n,
              i -> RasterPipelineFilters.bitmapPixelate(i, n),
              i -> ImageProcessor.bitmapPixelate(i, n),
              "raster.pixelate",
              p -> p.set("block", n)));
    }
    EqualizeMode[] modes = {EqualizeMode.RGB, EqualizeMode.LUMINANCE, null};
    for (EqualizeMode m : modes) {
      for (float f : new float[] {0f, 0.35f, 1f}) {
        equalize(l, m, f, false, false);
        equalize(l, m, f, true, true);
      }
    }
    equalize(l, EqualizeMode.RGB, 0.5f, true, false);
    equalize(l, EqualizeMode.LUMINANCE, 1f, false, true);
    equalize(l, EqualizeMode.RGB, 0f, true, false);
    equalize(l, EqualizeMode.RGB, 1.5f, false, false);
    equalize(l, EqualizeMode.LUMINANCE, -0.5f, false, true);
    equalize(l, EqualizeMode.LUMINANCE, Float.NaN, false, false);
  }

  private static void equalize(
      List<Case> l, EqualizeMode m, float f, boolean burned, boolean dark) {
    String modeId = m == EqualizeMode.LUMINANCE ? "luminance" : "rgb";
    l.add(
        new Case(
            "histogramEqualize",
            (m == null ? "null" : m.name())
                + ",factor="
                + f
                + ",burned="
                + burned
                + ",dark="
                + dark,
            i -> PointOpsFilters.histogramEqualize(i, m, f, burned, dark),
            i -> ImageProcessor.histogramEqualize(i, m, f, burned, dark),
            "tone.equalize",
            p ->
                p.set("mode", modeId)
                    .set("factor", f)
                    .set("markBurned", burned)
                    .set("markDark", dark)));
  }
}
