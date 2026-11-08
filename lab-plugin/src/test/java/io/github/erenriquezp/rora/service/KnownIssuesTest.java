package io.github.erenriquezp.rora.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.domain.ConvolutionKernel;
import io.github.erenriquezp.rora.domain.StretchMode;
import io.github.erenriquezp.rora.testsupport.ImageAssert;
import io.github.erenriquezp.rora.testsupport.TestImages;
import io.github.erenriquezp.rora.testsupport.TestImages.Kind;
import io.github.erenriquezp.rora.testsupport.TestImages.Pattern;
import java.awt.image.BufferedImage;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Defectos documentados de la auditoría (PROPUESTA_PRODUCTO.md §1.3), verificados con evidencia.
 * Cada test documenta el comportamiento ACTUAL; si una fase posterior lo corrige, el test debe
 * actualizarse o eliminarse de forma deliberada. El defecto n.º 1 (JPG) está en {@code
 * ImageIOServiceTest}.
 */
@Tag("known-issue")
class KnownIssuesTest {

  /** n.º 2: Interpolación y Extrapolación llaman a la misma función. */
  @Test
  void knownIssue_interpolationEqualsExtrapolation() {
    BufferedImage in = TestImages.create(Pattern.GRADIENT, Kind.ARGB, 16, 16);
    for (float t : new float[] {-1f, 0f, 0.5f, 1f, 1.5f, 2f}) {
      ImageAssert.assertEqualExactly(
          ImageProcessor.pointExtrapolate(in, t, 200, 30, 90),
          ImageProcessor.pointInterpolate(in, t, 200, 30, 90));
    }
  }

  /** n.º 3: BINARIO y HEXADECIMAL son idénticos (y DECIMAL también: (r*255)/15 == r*17). */
  @Test
  void knownIssue_stretch4BitsModesAreAllIdentical() {
    BufferedImage in = TestImages.create(Pattern.NOISE, Kind.ARGB, 33, 17);
    BufferedImage bin = ImageProcessor.stretch4Bits(in, StretchMode.BINARIO);
    ImageAssert.assertEqualExactly(ImageProcessor.stretch4Bits(in, StretchMode.HEXADECIMAL), bin);
    ImageAssert.assertEqualExactly(ImageProcessor.stretch4Bits(in, StretchMode.DECIMAL), bin);
  }

  /** n.º 4: la convolución 3x3 deja sin procesar el marco de 1 px (EDGE_NO_OP). */
  @ParameterizedTest
  @ValueSource(strings = {"SHARPEN", "EDGES", "EMBOSS", "BLUR"})
  void knownIssue_convolutionLeavesOnePixelFrameUntouched(String kernel) {
    BufferedImage in = TestImages.create(Pattern.NOISE, Kind.RGB, 16, 16);
    BufferedImage out = ImageProcessor.convolution(in, ConvolutionKernel.valueOf(kernel));
    int w = in.getWidth();
    int h = in.getHeight();
    boolean interiorChanged = false;
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        boolean frame = x == 0 || y == 0 || x == w - 1 || y == h - 1;
        if (frame) {
          assertThat(out.getRGB(x, y)).as("marco (%d,%d)", x, y).isEqualTo(in.getRGB(x, y));
        } else if (out.getRGB(x, y) != in.getRGB(x, y)) {
          interiorChanged = true;
        }
      }
    }
    assertThat(interiorChanged).as("el interior sí se procesa").isTrue();
  }

  /**
   * n.º 5: Blending 2 imágenes estira el fondo sin conservar la proporción. Un cuadrado blanco de
   * 10x10 centrado en un fondo 40x10 acaba convertido en un rectángulo alto sobre un primer plano
   * cuadrado de 40x40.
   */
  @Test
  void knownIssue_blendStretchesBackgroundAspectRatio() {
    BufferedImage fg = TestImages.create(Pattern.CHECKER, Kind.RGB, 40, 40);
    BufferedImage bg = new BufferedImage(40, 10, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < 10; y++) {
      for (int x = 15; x < 25; x++) {
        bg.setRGB(x, y, 0xFFFFFF);
      }
    }
    BufferedImage out = ImageProcessor.blend(fg, bg, 1f); // alpha=1 -> solo fondo
    int widthRun = 0;
    for (int x = 0; x < 40; x++) {
      if ((out.getRGB(x, 20) & 0xFF) > 200) {
        widthRun++;
      }
    }
    int heightRun = 0;
    for (int y = 0; y < 40; y++) {
      if ((out.getRGB(20, y) & 0xFF) > 200) {
        heightRun++;
      }
    }
    assertThat(widthRun).as("ancho del cuadrado tras el blend").isBetween(8, 12);
    assertThat(heightRun).as("alto: el cuadrado se estiró").isGreaterThanOrEqualTo(36);
  }

  /** n.º 6: Escala de grises usa promedio simple; "A luminancia" usa Rec.709. */
  @Test
  void knownIssue_grayscaleUsesAverageNotLuminance() {
    BufferedImage green = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    green.setRGB(0, 0, 0xFF00FF00);
    int avg = ImageProcessor.grayscale(green).getRGB(0, 0) & 0xFF;
    int lum = ImageProcessor.toLuminance(green).getRGB(0, 0) & 0xFF;
    assertThat(avg).as("promedio (0+255+0)/3").isEqualTo(85);
    assertThat(lum).as("Rec.709 ≈ 0.7152*255").isBetween(181, 183);
  }

  /**
   * n.º 7: Sepia/Polaroid/Kodachrome (y Frío/Cálido) usan matriz 3x3 sin offsets: el negro puro
   * siempre sigue siendo negro (los looks originales, 4x5, levantan negros).
   */
  @Test
  void knownIssue_colorMatricesHaveNoOffsets() {
    BufferedImage black = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    black.setRGB(0, 0, 0xFF000000);
    for (UnaryOperator<BufferedImage> f :
        java.util.List.<UnaryOperator<BufferedImage>>of(
            ImageProcessor::sepia,
            ImageProcessor::polaroid,
            ImageProcessor::kodachrome,
            ImageProcessor::coolTone,
            ImageProcessor::warmTone)) {
      assertThat(f.apply(black).getRGB(0, 0)).isEqualTo(0xFF000000);
    }
  }
}
