package io.github.erenriquezp.rora.core.color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class SrgbLutTest {

  @Test
  void roundTripIsIdentityForAll8BitValues() {
    for (int v = 0; v < 256; v++) {
      assertThat(SrgbLut.toSrgb8(SrgbLut.toLinear(v))).as("v=%d", v).isEqualTo(v);
    }
  }

  @Test
  void toLinearIsStrictlyIncreasingWithExactEndpoints() {
    assertThat(SrgbLut.toLinear(0)).isZero();
    assertThat(SrgbLut.toLinear(255)).isEqualTo(1f);
    for (int v = 1; v < 256; v++) {
      assertThat(SrgbLut.toLinear(v)).isGreaterThan(SrgbLut.toLinear(v - 1));
    }
  }

  @Test
  void matchesReferenceFormula() {
    // Valores de referencia: 0.5 sRGB ≈ 0.2140 lineal; 0.5 lineal ≈ 0.7354 sRGB.
    assertThat(SrgbLut.decode(0.5)).isCloseTo(0.21404, within(1e-5));
    assertThat(SrgbLut.encode(0.5)).isCloseTo(0.73536, within(1e-5));
    assertThat(SrgbLut.decode(0.04)).isCloseTo(0.04 / 12.92, within(1e-12));
    assertThat(SrgbLut.encode(0.003)).isCloseTo(0.003 * 12.92, within(1e-12));
    for (int v = 0; v < 256; v++) {
      assertThat(SrgbLut.encode(SrgbLut.decode(v / 255.0))).isCloseTo(v / 255.0, within(1e-12));
    }
  }

  @Test
  void toSrgb8ClampsOutOfRangeAndNaN() {
    assertThat(SrgbLut.toSrgb8(-1f)).isZero();
    assertThat(SrgbLut.toSrgb8(2f)).isEqualTo(255);
    assertThat(SrgbLut.toSrgb8(Float.NaN)).isZero();
    assertThat(SrgbLut.toLinear(256 + 128)).isEqualTo(SrgbLut.toLinear(128));
  }

  @Test
  void toSrgb8IsMonotonic() {
    int prev = 0;
    for (int i = 0; i <= SrgbLut.ENCODE_SIZE; i++) {
      int v = SrgbLut.toSrgb8(i / (float) SrgbLut.ENCODE_SIZE);
      assertThat(v).isGreaterThanOrEqualTo(prev);
      prev = v;
    }
    assertThat(prev).isEqualTo(255);
  }
}
