package io.github.erenriquezp.rora.core.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.CancelledException;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import java.util.SplittableRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/** {@link Downscale}: dimensiones, alpha ponderado, determinismo y referencia independiente. */
class DownscaleTest {

  private static final TileExecutor SEVEN = TileExecutor.create(7);

  @AfterAll
  static void close() {
    SEVEN.close();
  }

  private static Img down(Img src, int w, int h) {
    return Downscale.areaAverage(
        src, w, h, TileExecutor.sequential(), CancellationToken.none(), Progress.NONE);
  }

  private static Img noise(int w, int h, long seed) {
    SplittableRandom rnd = new SplittableRandom(seed);
    int[] px = new int[w * h];
    for (int i = 0; i < px.length; i++) {
      px[i] = rnd.nextInt();
    }
    return Img.adopt(w, h, px);
  }

  /** Implementación de referencia directa en 2D (sin separar ejes), con los mismos redondeos. */
  private static Img reference(Img src, int dw, int dh) {
    int sw = src.width();
    int sh = src.height();
    int[] out = new int[dw * dh];
    long n = (long) sw * sh;
    for (int i = 0; i < dh; i++) {
      for (int j = 0; j < dw; j++) {
        long a = 0;
        long[] ca = new long[3];
        long[] c = new long[3];
        for (int y = 0; y < sh; y++) {
          long oy =
              Math.max(
                  0,
                  Math.min((long) (y + 1) * dh, (long) (i + 1) * sh)
                      - Math.max((long) y * dh, (long) i * sh));
          if (oy == 0) {
            continue;
          }
          for (int x = 0; x < sw; x++) {
            long ox =
                Math.max(
                    0,
                    Math.min((long) (x + 1) * dw, (long) (j + 1) * sw)
                        - Math.max((long) x * dw, (long) j * sw));
            if (ox == 0) {
              continue;
            }
            int p = src.argb(x, y);
            long wgt = ox * oy;
            long pa = (p >>> 24) * wgt;
            a += pa;
            for (int k = 0; k < 3; k++) {
              int v = (p >> (16 - 8 * k)) & 0xFF;
              ca[k] += v * pa;
              c[k] += v * wgt;
            }
          }
        }
        int ao = (int) ((2 * a + n) / (2 * n));
        int px = ao << 24;
        for (int k = 0; k < 3; k++) {
          long v = a > 0 ? (2 * ca[k] + a) / (2 * a) : (2 * c[k] + n) / (2 * n);
          px |= (int) v << (16 - 8 * k);
        }
        out[i * dw + j] = px;
      }
    }
    return Img.adopt(dw, dh, out);
  }

  @Test
  void matchesTheDirectReferenceOnRandomSizes() {
    SplittableRandom rnd = new SplittableRandom(0x5EED_0030L);
    for (int t = 0; t < 200; t++) {
      int sw = 1 + rnd.nextInt(60);
      int sh = 1 + rnd.nextInt(60);
      int dw = 1 + rnd.nextInt(sw);
      int dh = 1 + rnd.nextInt(sh);
      Img src = noise(sw, sh, 0x5EED_3000L + t);
      Img expected = reference(src, dw, dh);
      assertThat(down(src, dw, dh).sameContent(expected))
          .as(sw + "x" + sh + " -> " + dw + "x" + dh)
          .isTrue();
    }
  }

  @Test
  void dimensionsIncludingExtremeAspectRatios() {
    int[][] cases = {
      {1, 1, 1, 1},
      {97, 61, 13, 7},
      {10000, 3, 100, 1},
      {3, 10000, 1, 100},
      {1, 5000, 1, 7},
      {5000, 1, 3, 1},
      {64, 48, 64, 24}
    };
    for (int[] c : cases) {
      Img out = down(noise(c[0], c[1], 1), c[2], c[3]);
      assertThat(out.width()).isEqualTo(c[2]);
      assertThat(out.height()).isEqualTo(c[3]);
    }
  }

  @Test
  void sameSizeIsACopy() {
    Img src = noise(5, 4, 2);
    Img out = down(src, 5, 4);
    assertThat(out).isNotSameAs(src);
    assertThat(out.sameContent(src)).isTrue();
  }

  @Test
  void alphaWeightedColorHasNoDarkHalo() {
    // Opaco (200,100,50) junto a transparente negro: el color no se oscurece; alpha 127,5 -> 128.
    Img src = Img.adopt(2, 1, new int[] {0xFFC86432, 0x00000000});
    assertThat(down(src, 1, 1).argb(0, 0)).isEqualTo(0x80C86432);
  }

  @Test
  void fullyTransparentRegionUsesThePlainAverage() {
    Img src = Img.adopt(2, 1, new int[] {0x00102030, 0x00305070});
    assertThat(down(src, 1, 1).argb(0, 0)).isEqualTo(0x00203850);
  }

  @Test
  void fractionalCoverageByHand() {
    // 3 -> 2: dst0 = (10·2 + 20·1)/3 = 13,3 -> 13; dst1 = (20·1 + 40·2)/3 = 33,3 -> 33.
    int[] px = {0xFF0A0A0A, 0xFF141414, 0xFF282828};
    Img out = down(Img.adopt(3, 1, px), 2, 1);
    assertThat(out.argb(0, 0)).isEqualTo(0xFF0D0D0D);
    assertThat(out.argb(1, 0)).isEqualTo(0xFF212121);
  }

  @Test
  void uniformStaysUniform() {
    int[] px = new int[123 * 77];
    java.util.Arrays.fill(px, 0x7F3366CC);
    Img out = down(Img.adopt(123, 77, px), 17, 9);
    for (int y = 0; y < 9; y++) {
      for (int x = 0; x < 17; x++) {
        assertThat(out.argb(x, y)).isEqualTo(0x7F3366CC);
      }
    }
  }

  @Test
  void deterministicWithAnyNumberOfThreadsAndBandSize() {
    Img src = noise(1000, 700, 3);
    Img one = down(src, 333, 211);
    Img seven =
        Downscale.areaAverage(src, 333, 211, SEVEN, CancellationToken.none(), Progress.NONE);
    Img rows1 =
        Downscale.areaAverage(
            src, 333, 211, TileExecutor.sequential(1), CancellationToken.none(), Progress.NONE);
    assertThat(seven.sameContent(one)).isTrue();
    assertThat(rows1.sameContent(one)).isTrue();
  }

  @Test
  void cancellationAndInvalidSizes() {
    CancellationToken t = CancellationToken.create();
    t.cancel();
    Img src = noise(10, 10, 4);
    assertThatThrownBy(
            () -> Downscale.areaAverage(src, 5, 5, TileExecutor.sequential(), t, Progress.NONE))
        .isInstanceOf(CancelledException.class);
    assertThatThrownBy(() -> down(src, 11, 5)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> down(src, 0, 5)).isInstanceOf(IllegalArgumentException.class);
  }
}
