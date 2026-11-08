package io.github.erenriquezp.rora.app.fx;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Parte pura de {@link FxImages} (la escritura en {@code WritableImage} necesita el toolkit). */
class FxImagesTest {

  @Test
  void premultiplyIsExactRoundingForAllAlphaAndChannelValues() {
    int[] px = new int[256 * 256];
    for (int a = 0; a < 256; a++) {
      for (int c = 0; c < 256; c++) {
        px[a * 256 + c] = (a << 24) | (c << 16) | ((255 - c) << 8) | (c / 2);
      }
    }
    int[] copy = px.clone();
    FxImages.premultiply(px, px.length);
    for (int i = 0; i < px.length; i++) {
      int a = copy[i] >>> 24;
      for (int s = 0; s < 24; s += 8) {
        int c = (copy[i] >> s) & 0xFF;
        int expected = (int) Math.round(c * a / 255.0);
        assertThat((px[i] >> s) & 0xFF).as("a=%d c=%d", a, c).isEqualTo(expected);
      }
      assertThat(px[i] >>> 24).isEqualTo(a == 0 ? 0 : a);
    }
  }

  @Test
  void premultiplyTouchesOnlyTheFirstNPixels() {
    int[] px = {0x80FF0000, 0x80FF0000};
    FxImages.premultiply(px, 1);
    assertThat(px[0]).isEqualTo(0x80800000);
    assertThat(px[1]).isEqualTo(0x80FF0000);
  }

  @Test
  void configuredFormatDefaultsToPremultiplied() {
    assertThat(FxImages.configuredFormat()).isEqualTo(FxImages.Format.INT_ARGB_PRE);
  }
}
