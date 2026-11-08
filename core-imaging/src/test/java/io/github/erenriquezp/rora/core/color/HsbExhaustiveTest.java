package io.github.erenriquezp.rora.core.color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.awt.Color;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Equivalencia bit a bit de {@link Hsb} con {@code java.awt.Color} sobre los 2^24 colores RGB, y de
 * {@code hsbToRgb} sobre las ternas que producen las operaciones heredadas (saturación, HSV y
 * rotación de tono) con varios factores.
 */
@Tag("exhaustive")
class HsbExhaustiveTest {

  /** Factores de saturación/valor usados por las operaciones (incluye extremos de la UI). */
  private static final float[] FACTORS = {0f, 0.5f, 0.9f, 1f, 1.2f, 1.3f, 2f};

  /** Desplazamientos de tono: grados / 360 para −180, −90, 30, 180. */
  private static final float[] HUE_DELTAS = {-0.5f, -0.25f, 30f / 360f, 0.5f};

  @Test
  void rgbToHsbMatchesAwtForAllColors() {
    float[] mine = new float[3];
    float[] awt = new float[3];
    long checked = 0;
    for (int rgb = 0; rgb < (1 << 24); rgb++) {
      int r = (rgb >> 16) & 0xFF;
      int g = (rgb >> 8) & 0xFF;
      int b = rgb & 0xFF;
      Hsb.rgbToHsb(r, g, b, mine);
      Color.RGBtoHSB(r, g, b, awt);
      for (int c = 0; c < 3; c++) {
        if (Float.floatToRawIntBits(mine[c]) != Float.floatToRawIntBits(awt[c])) {
          fail("rgbToHsb(%d,%d,%d)[%d]: %s vs awt %s", r, g, b, c, mine[c], awt[c]);
        }
      }
      checked++;
    }
    assertThat(checked).isEqualTo(1L << 24);
  }

  @Test
  void hsbToRgbMatchesAwtOnRoundTripAndOperationInputs() {
    float[] hsb = new float[3];
    long checked = 0;
    for (int rgb = 0; rgb < (1 << 24); rgb++) {
      Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsb);
      float h = hsb[0];
      float s = hsb[1];
      float v = hsb[2];
      checked += check(h, s, v);
      for (float f : FACTORS) {
        checked += check(h, clamp01(s * f), v); // saturación
        checked += check(h, clamp01(s * f), clamp01(v * f)); // HSV
      }
      for (float d : HUE_DELTAS) {
        float nh = h + d;
        checked += check(nh - (float) Math.floor(nh), s, v); // rotación de tono
      }
    }
    assertThat(checked).isEqualTo((1L << 24) * (1 + 2L * FACTORS.length + HUE_DELTAS.length));
  }

  @Test
  void hsbToRgbMatchesAwtOnEdgeValues() {
    float[] values = {
      0f,
      -0f,
      1f,
      0.5f,
      1e-7f,
      0.99999994f,
      1.00000012f,
      -0.25f,
      2.75f,
      -3.5f,
      5.9999995f,
      Float.MIN_VALUE
    };
    for (float h : values) {
      for (float s : new float[] {0f, 1e-7f, 0.5f, 1f}) {
        for (float v : new float[] {0f, 0.5f, 1f}) {
          check(h, s, v);
        }
      }
    }
  }

  private static int check(float h, float s, float v) {
    int mine = Hsb.hsbToRgb(h, s, v);
    int awt = Color.HSBtoRGB(h, s, v);
    if (mine != awt) {
      fail(
          "hsbToRgb(%s,%s,%s): %08x vs awt %08x",
          Float.toHexString(h), Float.toHexString(s), Float.toHexString(v), mine, awt);
    }
    return 1;
  }

  private static float clamp01(float x) {
    return Math.max(0f, Math.min(1f, x));
  }
}
