package io.github.erenriquezp.rora.core.ops.adjust;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.color.SrgbLut;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.Params;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("property")
class AdjustBasicOpTest {

  private static final AdjustBasicOp OP = new AdjustBasicOp();

  private static Params params(Object... kv) {
    Params.Builder b = OP.schema().defaults().toBuilder();
    for (int i = 0; i < kv.length; i += 2) {
      b.set((String) kv[i], kv[i + 1]);
    }
    return b.build();
  }

  private static Img run(Img src, Object... kv) {
    return OP.apply(src, params(kv), OpContext.sequential()).image();
  }

  private static Img noise(int w, int h, long seed) {
    SplittableRandom rnd = new SplittableRandom(seed);
    int[] px = new int[w * h];
    for (int i = 0; i < px.length; i++) {
      px[i] = rnd.nextInt();
    }
    return Img.adopt(w, h, px);
  }

  private static Img solid(int argb) {
    return Img.copyOf(1, 1, new int[] {argb});
  }

  private static int gray(int v) {
    return 0xFF000000 | v * 0x010101;
  }

  /** Lo que garantiza que mover un slider una centésima no provoque un salto. */
  @Test
  void linearRoundTripIsExactForEveryLevel() {
    for (int v = 0; v < 256; v++) {
      assertThat(SrgbLut.toSrgb8(SrgbLut.toLinear(v))).as("nivel %d", v).isEqualTo(v);
    }
  }

  @Test
  void neutralParametersLeaveThePixelsExactlyAlone() {
    Img src = noise(64, 64, 1);
    assertThat(run(src).sameContent(src)).isTrue();
  }

  @Test
  void alphaIsPreservedByEveryAdjustment() {
    Img src = noise(32, 32, 2);
    Img out = run(src, "exposure", 1.5, "contrast", 40, "temperature", 30, "saturation", -20);
    for (int y = 0; y < 32; y++) {
      for (int x = 0; x < 32; x++) {
        assertThat(out.argb(x, y) >>> 24).isEqualTo(src.argb(x, y) >>> 24);
      }
    }
  }

  @Test
  void aGrayStaysGrayWithoutColorBalance() {
    for (int v = 0; v < 256; v += 5) {
      int p = run(solid(gray(v)), "exposure", 0.7, "contrast", 30, "shadows", 40).argb(0, 0);
      int r = (p >> 16) & 0xFF;
      assertThat((p >> 8) & 0xFF).as("verde en %d", v).isEqualTo(r);
      assertThat(p & 0xFF).as("azul en %d", v).isEqualTo(r);
    }
  }

  @Test
  void oneStopOfExposureDoublesLinearLight() {
    int v = 90;
    int p = run(solid(gray(v)), "exposure", 1.0).argb(0, 0) & 0xFF;
    float expected = SrgbLut.toLinear(v) * 2;
    assertThat(Math.abs(p - SrgbLut.toSrgb8(expected))).isLessThanOrEqualTo(1);
  }

  @Test
  void whiteClipsAndBlackStaysBlackWithExposure() {
    assertThat(run(solid(gray(255)), "exposure", 2.0).argb(0, 0)).isEqualTo(gray(255));
    assertThat(run(solid(gray(0)), "exposure", 2.0).argb(0, 0)).isEqualTo(gray(0));
  }

  @Test
  void blacksLiftPureBlack() {
    assertThat(run(solid(gray(0)), "blacks", 100).argb(0, 0) & 0xFF).isGreaterThan(0);
  }

  @Test
  void highlightsRecoverOverexposedTones() {
    int plain = run(solid(gray(200)), "exposure", 1.5).argb(0, 0) & 0xFF;
    int recovered = run(solid(gray(200)), "exposure", 1.5, "highlights", -100).argb(0, 0) & 0xFF;
    assertThat(plain).isEqualTo(255);
    assertThat(recovered).isLessThan(255);
  }

  @Test
  void saturationMinusHundredMakesEveryPixelGray() {
    Img src = noise(32, 32, 3);
    Img out = run(src, "saturation", -100);
    for (int y = 0; y < 32; y++) {
      for (int x = 0; x < 32; x++) {
        int p = out.argb(x, y);
        assertThat(Math.abs(((p >> 16) & 0xFF) - (p & 0xFF))).isLessThanOrEqualTo(1);
      }
    }
  }

  @Test
  void temperatureWarmsAndCools() {
    int mid = gray(128);
    int warm = run(solid(mid), "temperature", 60).argb(0, 0);
    int cool = run(solid(mid), "temperature", -60).argb(0, 0);
    assertThat((warm >> 16) & 0xFF).isGreaterThan(warm & 0xFF);
    assertThat((cool >> 16) & 0xFF).isLessThan(cool & 0xFF);
  }

  @Test
  void whiteBalanceKeepsTheLuminanceOfAGray() {
    float[] g = AdjustBasicOp.whiteBalanceGains(0.8, -0.5);
    assertThat(0.2126f * g[0] + 0.7152f * g[1] + 0.0722f * g[2]).isBetween(0.9999f, 1.0001f);
  }

  @Test
  void vibranceAffectsMutedColorsMoreThanSaturatedOnes() {
    int muted = 0xFF80756A;
    int vivid = 0xFFD02010;
    assertThat(chromaGain(muted)).isGreaterThan(chromaGain(vivid));
  }

  private static double chromaGain(int argb) {
    int p = run(solid(argb), "vibrance", 80).argb(0, 0);
    return (double) spread(p) / spread(argb);
  }

  private static int spread(int p) {
    int r = (p >> 16) & 0xFF;
    int g = (p >> 8) & 0xFF;
    int b = p & 0xFF;
    return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
  }

  @Test
  void aSmallSliderMoveNeverJumps() {
    Img src = noise(128, 128, 4);
    Img out = run(src, "exposure", 0.01);
    for (int y = 0; y < 128; y++) {
      for (int x = 0; x < 128; x++) {
        for (int shift = 0; shift < 24; shift += 8) {
          int d = Math.abs(((out.argb(x, y) >> shift) & 0xFF) - ((src.argb(x, y) >> shift) & 0xFF));
          assertThat(d).isLessThanOrEqualTo(2);
        }
      }
    }
  }

  /** La curva es monótona en todas las combinaciones extremas de sus cinco controles. */
  @Test
  void theToneCurveIsMonotonic() {
    double[] values = {-1, -0.5, 0, 0.5, 1};
    for (double contrast : values) {
      for (double highlights : values) {
        for (double shadows : values) {
          for (double whites : values) {
            for (double blacks : values) {
              double previous = -1;
              for (int i = 0; i <= 2000; i++) {
                double v =
                    AdjustBasicOp.curve(i / 1000.0, contrast, highlights, shadows, whites, blacks);
                assertThat(v)
                    .as(
                        "c=%s h=%s s=%s w=%s b=%s t=%s",
                        contrast, highlights, shadows, whites, blacks, i / 1000.0)
                    .isGreaterThanOrEqualTo(previous - 1e-12);
                previous = v;
              }
            }
          }
        }
      }
    }
  }

  @Test
  void theToneTableIsMonotonicWithExposure() {
    for (double ev : new double[] {-5, -1, 0.5, 5}) {
      float[] table = AdjustBasicOp.toneTable(ev, 0.5, -0.8, 0.8, 0.3, -0.3);
      for (int i = 1; i <= AdjustBasicOp.TONE_SIZE; i++) {
        assertThat(table[i]).as("ev=%s i=%s", ev, i).isGreaterThanOrEqualTo(table[i - 1] - 1e-6f);
      }
    }
  }
}
