package io.github.erenriquezp.rora.ai.tile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.ai.api.TileRunner;
import io.github.erenriquezp.rora.ai.onnx.OnnxEngineAccess;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.CancelledException;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure.Reason;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TilerTest {

  private static final Tiler TILER = new Tiler(256, 32, 4);

  /** Repite cada muestra, sin ORT: rápido y exacto. */
  private static final TileRunner NEAREST =
      new TileRunner() {
        @Override
        public float[] run(float[] chw, int h, int w, CancellationToken t) {
          int s = 4;
          float[] out = new float[3 * h * s * w * s];
          int side = w * s;
          for (int c = 0; c < 3; c++) {
            for (int y = 0; y < h * s; y++) {
              for (int x = 0; x < w * s; x++) {
                out[c * side * side + y * side + x] = chw[c * h * w + (y / s) * w + x / s];
              }
            }
          }
          return out;
        }

        @Override
        public void close() {}
      };

  private static Img noise(int w, int h, long seed, boolean opaque) {
    Random r = new Random(seed);
    int[] p = new int[w * h];
    for (int i = 0; i < p.length; i++) {
      p[i] = r.nextInt() | (opaque ? 0xFF000000 : 0);
    }
    return Img.adopt(w, h, p);
  }

  private static Img byRepetition(Img src, int s) {
    int[] p = new int[src.width() * s * src.height() * s];
    for (int y = 0; y < src.height() * s; y++) {
      for (int x = 0; x < src.width() * s; x++) {
        p[y * src.width() * s + x] = src.argb(x / s, y / s);
      }
    }
    return Img.adopt(src.width() * s, src.height() * s, p);
  }

  private static Img enhance(Img src, int scale) throws AiFailure {
    return TILER.enhance(src, scale, NEAREST, CancellationToken.none(), Progress.NONE);
  }

  @ParameterizedTest
  @CsvSource({
    "1,1", "2,3", "255,255", "256,256", "257,256", "300,500", "480,481", "700,40", "40,700",
    "1,900", "513,737"
  })
  void tilesEqualTheWholeImageBitForBit(int w, int h) throws Exception {
    Img src = noise(w, h, w * 31L + h, true);
    assertThat(enhance(src, 4).sameContent(byRepetition(src, 4))).isTrue();
  }

  @ParameterizedTest
  @CsvSource({"1,1", "300,500", "513,737"})
  void halfScaleEqualsTheInputRepeatedTwice(int w, int h) throws Exception {
    Img src = noise(w, h, 7, true);
    assertThat(enhance(src, 2).sameContent(byRepetition(src, 2))).isTrue();
  }

  @Test
  void aRealOnnxModelMatchesTheJavaOneThroughTheTiler() throws Exception {
    Img src = noise(300, 280, 11, true);
    try (TileRunner r = OnnxEngineAccess.open("nearest_x4", 4)) {
      Img out = TILER.enhance(src, 4, r, CancellationToken.none(), Progress.NONE);
      assertThat(out.sameContent(byRepetition(src, 4))).isTrue();
    }
  }

  @Test
  void seamsOfAConvolutionWithAMarginWiderThanItsRadiusAreInvisible() throws Exception {
    Img src = noise(600, 500, 13, true);
    int w = src.width();
    int h = src.height();
    float[] in = new float[3 * h * w];
    for (int y = 0, i = 0; y < h; y++) {
      for (int x = 0; x < w; x++, i++) {
        int p = src.argb(x, y);
        in[i] = ((p >> 16) & 0xFF) / 255f;
        in[i + h * w] = ((p >> 8) & 0xFF) / 255f;
        in[i + 2 * h * w] = (p & 0xFF) / 255f;
      }
    }
    try (TileRunner r = OnnxEngineAccess.open("box5_x4", 4)) {
      float[] whole = r.run(in, h, w, CancellationToken.none());
      Img out = TILER.enhance(src, 4, r, CancellationToken.none(), Progress.NONE);
      int side = w * 4;
      int plane = side * (h * 4);
      int worst = 0;
      for (int y = 0; y < h * 4; y++) {
        for (int x = 0; x < w * 4; x++) {
          int p = out.argb(x, y);
          int i = y * side + x;
          worst = Math.max(worst, Math.abs(Tiler.to8(whole[i]) - ((p >> 16) & 0xFF)));
          worst = Math.max(worst, Math.abs(Tiler.to8(whole[i + plane]) - ((p >> 8) & 0xFF)));
          worst = Math.max(worst, Math.abs(Tiler.to8(whole[i + 2 * plane]) - (p & 0xFF)));
        }
      }
      // Cada tile suma en su propio orden: una diferencia de 1 ulp puede cambiar un redondeo.
      assertThat(worst).isLessThanOrEqualTo(1);
    }
  }

  @Test
  void toEightBitsIsTheInverseOfTheInputConversion() throws Exception {
    for (int v = 0; v < 256; v++) {
      assertThat(Tiler.to8(v / 255f)).isEqualTo(v);
    }
  }

  @Test
  void outOfRangeValuesAreClamped() throws Exception {
    assertThat(Tiler.to8(-3f)).isZero();
    assertThat(Tiler.to8(7f)).isEqualTo(255);
  }

  @Test
  void nonFiniteOutputIsAFailure() {
    TileRunner nan = constant(Float.NaN);
    assertFails(nan, Reason.FAILURE);
    assertFails(constant(Float.POSITIVE_INFINITY), Reason.FAILURE);
  }

  private static TileRunner constant(float value) {
    return new TileRunner() {
      @Override
      public float[] run(float[] chw, int h, int w, CancellationToken t) {
        float[] out = new float[3 * h * 4 * w * 4];
        java.util.Arrays.fill(out, value);
        return out;
      }

      @Override
      public void close() {}
    };
  }

  private static void assertFails(TileRunner r, Reason reason) {
    assertThatThrownBy(
            () ->
                TILER.enhance(noise(8, 8, 1, true), 4, r, CancellationToken.none(), Progress.NONE))
        .isInstanceOfSatisfying(AiFailure.class, e -> assertThat(e.reason()).isEqualTo(reason));
  }

  @Test
  void aRunnerWithTheWrongOutputLengthIsAShapeMismatch() {
    TileRunner wrong =
        new TileRunner() {
          @Override
          public float[] run(float[] chw, int h, int w, CancellationToken t) {
            return new float[10];
          }

          @Override
          public void close() {}
        };
    assertFails(wrong, Reason.SHAPE_MISMATCH);
  }

  @Test
  void progressIsMonotonicWithAKnownTotal() throws Exception {
    Img src = noise(700, 500, 3, true);
    List<long[]> calls = new ArrayList<>();
    TILER.enhance(
        src, 2, NEAREST, CancellationToken.none(), (d, t) -> calls.add(new long[] {d, t}));
    int total = TILER.tileCount(700, 500);
    assertThat(calls).hasSize(total);
    for (int i = 0; i < calls.size(); i++) {
      assertThat(calls.get(i)).containsExactly(i + 1L, (long) total);
    }
  }

  @Test
  void cancellingBetweenTilesStops() {
    CancellationToken token = CancellationToken.create();
    assertThatThrownBy(
            () ->
                TILER.enhance(
                    noise(700, 500, 3, true), 2, NEAREST, token, (d, t) -> token.cancel()))
        .isInstanceOf(CancelledException.class);
  }

  @Test
  void aGrayImageStaysGray() throws Exception {
    TileRunner tinted =
        new TileRunner() {
          @Override
          public float[] run(float[] chw, int h, int w, CancellationToken t) throws AiFailure {
            float[] out = NEAREST.run(chw, h, w, t);
            for (int i = 0; i < out.length / 3; i++) {
              out[i] = Math.min(1f, out[i] + 0.1f);
            }
            return out;
          }

          @Override
          public void close() {}
        };
    int[] p = new int[64];
    for (int i = 0; i < p.length; i++) {
      int v = i * 4;
      p[i] = 0xFF000000 | v << 16 | v << 8 | v;
    }
    Img out = TILER.enhance(Img.adopt(8, 8, p), 4, tinted, CancellationToken.none(), Progress.NONE);
    for (int i = 0; i < out.width(); i++) {
      int q = out.argb(i, i);
      assertThat((q >> 16) & 0xFF).isEqualTo((q >> 8) & 0xFF).isEqualTo(q & 0xFF);
    }
  }

  @Test
  void alphaIsScaledSeparatelyAndTransparentColorDoesNotBleed() throws Exception {
    int[] p = new int[16 * 16];
    for (int y = 0; y < 16; y++) {
      for (int x = 0; x < 16; x++) {
        p[y * 16 + x] = x < 8 ? 0xFF204060 : 0x00FF00FF;
      }
    }
    Img src = Img.adopt(16, 16, p);
    Img out = enhance(src, 4);
    assertThat(out.width()).isEqualTo(64);
    assertThat(out.argb(2, 32) >>> 24).isEqualTo(255);
    assertThat(out.argb(60, 32) >>> 24).isZero();
    // El magenta de la zona transparente no llega al borde visible.
    int edge = out.argb(30, 32);
    assertThat((edge >> 16) & 0xFF).isEqualTo(0x20);
    assertThat(edge & 0xFF).isEqualTo(0x60);
  }

  @Test
  void reflectionMirrorsAroundTheEdges() {
    assertThat(Tiler.reflect(0, 3)).isZero();
    assertThat(Tiler.reflect(2, 3)).isEqualTo(2);
    assertThat(Tiler.reflect(3, 3)).isEqualTo(2);
    assertThat(Tiler.reflect(5, 3)).isZero();
    assertThat(Tiler.reflect(6, 3)).isZero();
    assertThat(Tiler.reflect(255, 1)).isZero();
  }

  @Test
  void positionsCoverTheAxisAndEndAtTheEdge() {
    assertThat(TILER.positions(100)).containsExactly(0);
    assertThat(TILER.positions(256)).containsExactly(0);
    assertThat(TILER.positions(257)).containsExactly(0, 1);
    assertThat(TILER.positions(480)).containsExactly(0, 224);
    assertThat(TILER.tileCount(257, 257)).isEqualTo(4);
  }

  @Test
  void aScaleThatIsNeitherTheModelsNorHalfIsRejected() {
    assertThatThrownBy(
            () ->
                TILER.enhance(
                    noise(4, 4, 1, true), 3, NEAREST, CancellationToken.none(), Progress.NONE))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
