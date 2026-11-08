package io.github.erenriquezp.rora.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.op.ChannelHistogram;
import io.github.erenriquezp.rora.core.op.ExposureStats;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpResult;
import io.github.erenriquezp.rora.domain.ExposureDiagnostic;
import io.github.erenriquezp.rora.testsupport.EquivalenceImages;
import io.github.erenriquezp.rora.testsupport.EquivalenceImages.Sample;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Equivalencia exacta de las operaciones de análisis (lote 3): {@code diagnoseExposure} y {@code
 * generateHistogram}. El núcleo devuelve recuentos; la fachada calcula porcentajes/texto y dibuja
 * el gráfico como el legacy.
 */
@Tag("equivalence")
@SuppressWarnings("removal") // el legacy portado está @Deprecated(forRemoval) a propósito
class AnalysisEquivalenceTest {

  private static final TileExecutor SEVEN = TileExecutor.create(7);
  private static final TileExecutor TINY_BANDS = TileExecutor.create(3, 1);

  @AfterAll
  static void closePools() {
    SEVEN.close();
    TINY_BANDS.close();
  }

  static Stream<Object[]> types() {
    return EquivalenceImages.TYPES.stream()
        .map(t -> new Object[] {EquivalenceImages.typeName(t), t});
  }

  private static List<OpContext> contexts() {
    return List.of(OpContext.sequential(), OpContext.of(SEVEN), OpContext.of(TINY_BANDS));
  }

  @ParameterizedTest(name = "diagnoseExposure sobre {0}")
  @MethodSource("types")
  void exposureMatchesLegacy(String typeName, int type) {
    for (Sample s : EquivalenceImages.samples(type)) {
      ExposureDiagnostic expected = PointOpsFilters.diagnoseExposure(s.image());
      assertThat(ImageProcessor.diagnoseExposure(s.image())).as("%s", s).isEqualTo(expected);
      for (OpContext ctx : contexts()) {
        OpResult r =
            CoreBridge.run(
                "analysis.exposure",
                AwtImg.toImg(s.image()),
                CoreBridge.params("analysis.exposure", p -> p),
                ctx);
        ExposureStats st = r.aux(ExposureStats.class).orElseThrow();
        assertThat((st.burned() * 100.0) / st.total()).as("%s", s).isEqualTo(expected.pctBurned());
        assertThat((st.dark() * 100.0) / st.total()).as("%s", s).isEqualTo(expected.pctDark());
      }
    }
  }

  /** El texto depende del locale por defecto, igual que en el legacy: deben coincidir en todos. */
  @Test
  void exposureTextMatchesLegacyUnderSeveralLocales() {
    Locale original = Locale.getDefault();
    BufferedImage burned = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    for (int i = 0; i < 37; i++) {
      burned.setRGB(i % 10, i / 10, 0xFFFFFF);
    }
    for (int i = 50; i < 100; i++) {
      burned.setRGB(i % 10, i / 10, 0x000000);
    }
    try {
      for (Locale l : new Locale[] {Locale.ROOT, Locale.forLanguageTag("es-ES"), Locale.GERMANY}) {
        Locale.setDefault(l);
        assertThat(ImageProcessor.diagnoseExposure(burned))
            .as("locale %s", l)
            .isEqualTo(PointOpsFilters.diagnoseExposure(burned));
      }
    } finally {
      Locale.setDefault(original);
    }
  }

  @ParameterizedTest(name = "generateHistogram sobre {0}")
  @MethodSource("types")
  void histogramMatchesLegacy(String typeName, int type) {
    for (Sample s : EquivalenceImages.samples(type)) {
      BufferedImage expected = HistogramService.generateHistogram(s.image());
      CoreEquivalenceTest.assertSameImage(
          "generateHistogram [fachada] sobre " + s,
          expected,
          ImageProcessor.generateHistogram(s.image()));
      int[] px = EquivalenceImages.pixels(s.image());
      int[] r = new int[256];
      int[] g = new int[256];
      int[] b = new int[256];
      for (int p : px) {
        r[(p >> 16) & 0xFF]++;
        g[(p >> 8) & 0xFF]++;
        b[p & 0xFF]++;
      }
      for (OpContext ctx : contexts()) {
        ChannelHistogram h =
            CoreBridge.run(
                    "analysis.histogram",
                    AwtImg.toImg(s.image()),
                    CoreBridge.params("analysis.histogram", p -> p),
                    ctx)
                .aux(ChannelHistogram.class)
                .orElseThrow();
        assertThat(h.red()).as("%s", s).isEqualTo(r);
        assertThat(h.green()).as("%s", s).isEqualTo(g);
        assertThat(h.blue()).as("%s", s).isEqualTo(b);
      }
    }
  }

  @Test
  @Tag("exhaustive")
  void analysisMatchesLegacyOnAllColors() {
    BufferedImage all = EquivalenceImages.allColors(0xA11C0105L);
    assertThat(ImageProcessor.diagnoseExposure(all))
        .isEqualTo(PointOpsFilters.diagnoseExposure(all));
    CoreEquivalenceTest.assertSameImage(
        "generateHistogram sobre 2^24 colores",
        HistogramService.generateHistogram(all),
        ImageProcessor.generateHistogram(all));
  }
}
