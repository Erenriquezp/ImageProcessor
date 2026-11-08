package com.example.imageprocessor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.imageprocessor.domain.ExposureDiagnostic;
import com.example.imageprocessor.domain.GradientType;
import com.example.imageprocessor.testsupport.ImageAssert;
import com.example.imageprocessor.testsupport.TestImages;
import com.example.imageprocessor.testsupport.TestImages.Kind;
import com.example.imageprocessor.testsupport.TestImages.Pattern;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Degradados, histograma, blending 2/3 y diagnóstico de exposición (no encajan en el catálogo). */
class SpecialOperationsTest {

  private static BufferedImage solid(int w, int h, int argb) {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        img.setRGB(x, y, argb);
      }
    }
    return img;
  }

  // ── Diagnóstico de exposición (nivel A: porcentajes exactos) ──────────────

  @Test
  @Tag("legacy-pin")
  void exposureDiagnosticPercentages() {
    ExposureDiagnostic white = ImageProcessor.diagnoseExposure(solid(4, 4, 0xFFFFFFFF));
    assertThat(white.pctBurned()).isEqualTo(100.0);
    assertThat(white.pctDark()).isEqualTo(0.0);

    ExposureDiagnostic black = ImageProcessor.diagnoseExposure(solid(4, 4, 0xFF000000));
    assertThat(black.pctBurned()).isEqualTo(0.0);
    assertThat(black.pctDark()).isEqualTo(100.0);

    // Umbrales: 240 cuenta como quemado, 239 no; 15 cuenta como oscuro, 16 no.
    assertThat(ImageProcessor.diagnoseExposure(solid(2, 2, 0xFFF0F0F0)).pctBurned()).isEqualTo(100.0);
    assertThat(ImageProcessor.diagnoseExposure(solid(2, 2, 0xFFEFEFEF)).pctBurned()).isEqualTo(0.0);
    assertThat(ImageProcessor.diagnoseExposure(solid(2, 2, 0xFF0F0F0F)).pctDark()).isEqualTo(100.0);
    assertThat(ImageProcessor.diagnoseExposure(solid(2, 2, 0xFF101010)).pctDark()).isEqualTo(0.0);

    ExposureDiagnostic mid = ImageProcessor.diagnoseExposure(solid(4, 4, 0xFF808080));
    assertThat(mid.text()).isEqualTo("Exposición adecuada");
  }

  @Test
  @Tag("legacy-pin")
  void exposureDiagnosticOnOnePixelImage() {
    assertThat(ImageProcessor.diagnoseExposure(solid(1, 1, 0xFFFFFFFF)).pctBurned()).isEqualTo(100.0);
  }

  // ── Degradados (nivel B) ─────────────────────────────────────────────────

  @ParameterizedTest
  @EnumSource(GradientType.class)
  @Tag("property")
  void gradientHasRequestedSizeAndIsDeterministic(GradientType type) {
    for (int[] s : TestImages.SIZES) {
      BufferedImage a = ImageProcessor.generateGradient(type, s[0], s[1], 0xFF0000, 0x0000FF);
      BufferedImage b = ImageProcessor.generateGradient(type, s[0], s[1], 0xFF0000, 0x0000FF);
      assertThat(a.getWidth()).isEqualTo(s[0]);
      assertThat(a.getHeight()).isEqualTo(s[1]);
      ImageAssert.assertEqualExactly(a, b);
    }
  }

  @Test
  @Tag("property")
  void linearGradientEndpointsMatchColors() {
    BufferedImage g = ImageProcessor.generateGradient(GradientType.LEFT_TO_RIGHT, 16, 4, 0xFF0000, 0x0000FF);
    assertThat(g.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0xFF0000);
    assertThat(g.getRGB(15, 3) & 0xFFFFFF).isEqualTo(0x0000FF);
    BufferedImage r = ImageProcessor.generateGradient(GradientType.RIGHT_TO_LEFT, 16, 4, 0xFF0000, 0x0000FF);
    assertThat(r.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0x0000FF);
  }

  @Test
  @Tag("property")
  void equalColorsGiveFlatImage() {
    BufferedImage g = ImageProcessor.generateGradient(GradientType.RADIAL, 33, 17, 0x336699, 0x336699);
    for (int y = 0; y < 17; y++) {
      for (int x = 0; x < 33; x++) {
        assertThat(g.getRGB(x, y) & 0xFFFFFF).isEqualTo(0x336699);
      }
    }
  }

  @Test
  @Tag("property")
  void oneByOneGradientDoesNotThrow() {
    for (GradientType t : GradientType.values()) {
      assertThatCode(() -> ImageProcessor.generateGradient(t, 1, 1, 0xFF0000, 0x0000FF))
          .doesNotThrowAnyException();
    }
  }

  // ── Histograma (nivel B) ─────────────────────────────────────────────────

  @Test
  @Tag("property")
  void histogramIs800x600AndDeterministic() {
    for (int[] s : TestImages.SIZES) {
      BufferedImage in = TestImages.create(Pattern.NOISE, Kind.ARGB, s[0], s[1]);
      BufferedImage a = ImageProcessor.generateHistogram(in);
      assertThat(a.getWidth()).isEqualTo(800);
      assertThat(a.getHeight()).isEqualTo(600);
      ImageAssert.assertEqualExactly(ImageProcessor.generateHistogram(in), a);
    }
  }

  @Test
  @Tag("property")
  void histogramOfSolidImageDrawsCurvesOnBlackBackground() {
    BufferedImage h = ImageProcessor.generateHistogram(solid(8, 8, 0xFFFF0000));
    assertThat(h.getRGB(400, 300) & 0xFFFFFF).as("fondo negro en el centro").isEqualTo(0);
    boolean drawn = false;
    for (int y = 0; y < 600 && !drawn; y++) {
      for (int x = 0; x < 800; x++) {
        if ((h.getRGB(x, y) & 0xFFFFFF) != 0) {
          drawn = true;
          break;
        }
      }
    }
    assertThat(drawn).as("alguna curva dibujada").isTrue();
  }

  // ── Blending (nivel B) ───────────────────────────────────────────────────

  @Test
  @Tag("property")
  void blendExtremesReturnForegroundOrBackground() {
    BufferedImage fg = TestImages.create(Pattern.GRADIENT, Kind.ARGB, 33, 17);
    BufferedImage bg = TestImages.create(Pattern.CHECKER, Kind.RGB, 33, 17);
    BufferedImage zero = ImageProcessor.blend(fg, bg, 0f);
    BufferedImage one = ImageProcessor.blend(fg, bg, 1f);
    ImageAssert.assertAlphaPreserved(zero, fg);
    ImageAssert.assertAlphaPreserved(one, fg);
    for (int y = 0; y < 17; y++) {
      for (int x = 0; x < 33; x++) {
        assertThat(zero.getRGB(x, y) & 0xFFFFFF).isEqualTo(fg.getRGB(x, y) & 0xFFFFFF);
        assertThat(one.getRGB(x, y) & 0xFFFFFF).isEqualTo(bg.getRGB(x, y) & 0xFFFFFF);
      }
    }
  }

  @Test
  @Tag("property")
  void blendKeepsForegroundSizeWhenBackgroundDiffers() {
    BufferedImage fg = TestImages.create(Pattern.GRADIENT, Kind.ARGB, 16, 16);
    BufferedImage bg = TestImages.create(Pattern.CHECKER, Kind.RGB, 64, 48);
    BufferedImage out = ImageProcessor.blend(fg, bg, 0.5f);
    ImageAssert.assertSameSize(out, fg);
    ImageAssert.assertEqualExactly(out, ImageProcessor.blend(fg, bg, 0.5f));
  }

  @Test
  @Tag("property")
  void tripleBlendWithUnitWeightOnFirstReturnsFirst() {
    BufferedImage a = TestImages.create(Pattern.GRADIENT, Kind.ARGB, 33, 17);
    BufferedImage b = TestImages.create(Pattern.CHECKER, Kind.RGB, 16, 16);
    BufferedImage c = TestImages.create(Pattern.NOISE, Kind.RGB, 64, 48);
    BufferedImage out = ImageProcessor.tripleBlend(a, b, c, 1f, 0f, 0f);
    ImageAssert.assertSameSize(out, a);
    ImageAssert.assertAlphaPreserved(out, a);
    for (int y = 0; y < 17; y++) {
      for (int x = 0; x < 33; x++) {
        assertThat(out.getRGB(x, y) & 0xFFFFFF).isEqualTo(a.getRGB(x, y) & 0xFFFFFF);
      }
    }
  }

  /** Smoke de nivel C: pesos sin normalizar saturan a 255 sin lanzar. */
  @Test
  @Tag("property")
  void tripleBlendWithLargeWeightsClampsAndDoesNotThrow() {
    BufferedImage a = TestImages.create(Pattern.NOISE, Kind.ARGB, 2, 2);
    BufferedImage out = ImageProcessor.tripleBlend(a, a, a, 3f, 3f, 3f);
    ImageAssert.assertChannelsInRange(out);
  }
}
