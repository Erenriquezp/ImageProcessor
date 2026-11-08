package io.github.erenriquezp.rora.service;

import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.ChannelHistogram;
import io.github.erenriquezp.rora.core.op.ExposureStats;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.domain.ExposureDiagnostic;
import java.awt.image.BufferedImage;

/**
 * Análisis sobre {@link Img} para la capa de sesión (sin pasar por {@code BufferedImage}), con el
 * mismo resultado que la fachada: {@link ImageProcessor#diagnoseExposure} y {@link
 * ImageProcessor#generateHistogram} usan este mismo código.
 */
public final class Analysis {

  private Analysis() {}

  /** Texto y porcentajes del diagnóstico: mismas expresiones y locale por defecto que el legacy. */
  public static ExposureDiagnostic describe(ExposureStats s) {
    double pctBurned = (s.burned() * 100.0) / s.total();
    double pctDark = (s.dark() * 100.0) / s.total();

    StringBuilder sb = new StringBuilder();
    if (pctBurned > 5) {
      sb.append(String.format("Imagen quemada (%.1f%% sobreexpuesto)", pctBurned));
    }
    if (pctDark > 5) {
      if (sb.length() > 0) {
        sb.append(" | ");
      }
      sb.append(String.format("Imagen oscura (%.1f%% subexpuesto)", pctDark));
    }
    if (sb.length() == 0) {
      sb.append("Exposición adecuada");
    }
    return new ExposureDiagnostic(pctBurned, pctDark, sb.toString());
  }

  /** Gráfico del histograma RGB (800×600), idéntico a {@link ImageProcessor#generateHistogram}. */
  public static BufferedImage histogramChart(Img img, OpContext ctx) {
    ChannelHistogram h =
        CoreBridge.run(
                "analysis.histogram",
                img,
                CoreBridge.op("analysis.histogram").schema().defaults(),
                ctx)
            .aux(ChannelHistogram.class)
            .orElseThrow();
    return HistogramChart.draw(h);
  }
}
