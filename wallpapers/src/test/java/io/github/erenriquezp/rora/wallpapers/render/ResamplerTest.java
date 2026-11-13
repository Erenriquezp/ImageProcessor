package io.github.erenriquezp.rora.wallpapers.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.wallpapers.geom.Fit;
import io.github.erenriquezp.rora.wallpapers.geom.FrameRect;
import io.github.erenriquezp.rora.wallpapers.geom.IntRect;
import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset;
import io.github.erenriquezp.rora.wallpapers.preset.PresetCatalog;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ResamplerTest {

  static Img noise(int w, int h, long seed) {
    Random r = new Random(seed);
    int[] px = new int[w * h];
    for (int i = 0; i < px.length; i++) {
      px[i] = 0xFF000000 | (r.nextInt() & 0xFFFFFF);
    }
    return Img.adopt(w, h, px);
  }

  static Img uniform(int w, int h, int argb) {
    int[] px = new int[w * h];
    java.util.Arrays.fill(px, argb);
    return Img.adopt(w, h, px);
  }

  private static Img run(Img src, FrameRect f, int w, int h, TileExecutor ex) {
    return Resampler.resample(src, f, w, h, ex, CancellationToken.none(), Progress.NONE);
  }

  private static Img run(Img src, FrameRect f, int w, int h) {
    return run(src, f, w, h, TileExecutor.sequential());
  }

  @Test
  void everySrgbValueSurvivesTheRoundTrip() {
    for (int v = 0; v < 256; v++) {
      assertThat(SrgbEncoder.quantize(SrgbEncoder.decode(v), 0.5f)).as("valor %d", v).isEqualTo(v);
    }
  }

  @Test
  void theIdentityIsExact() {
    Img src = noise(97, 61, 1);
    Img out = run(src, new FrameRect(0, 0, 97, 61), 97, 61);
    assertThat(out.sameContent(src)).isTrue();
  }

  @Test
  void anIntegerShiftWithoutScalingIsAnExactCopy() {
    Img src = noise(50, 40, 2);
    Img out = run(src, new FrameRect(10, 5, 30, 20), 30, 20);
    for (int y = 0; y < 20; y++) {
      for (int x = 0; x < 30; x++) {
        assertThat(out.argb(x, y)).isEqualTo(src.argb(x + 10, y + 5));
      }
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 7, 333})
  void uniformImagesStayUniformWithFractionalFrames(int side) {
    Img src = uniform(side, side, 0xFF3A7FC1);
    double w = side * 0.37;
    Img out = run(src, new FrameRect(side * 0.21, side * 0.13, w, w * 9 / 16), 211, 119);
    assertThat(out.width()).isEqualTo(211);
    assertThat(out.height()).isEqualTo(119);
    for (int i : out.toArray()) {
      assertThat(i).isEqualTo(0xFF3A7FC1);
    }
  }

  @Test
  void outputSizeIsExactForEveryPreset() {
    Img src = noise(64, 48, 3);
    for (DevicePreset p : PresetCatalog.builtIns()) {
      Img out = run(src, Fit.cover(64, 48, p.width(), p.height()), p.width() / 8, p.height() / 8);
      assertThat(out.width()).as(p.id()).isEqualTo(p.width() / 8);
      assertThat(out.height()).as(p.id()).isEqualTo(p.height() / 8);
    }
  }

  @Test
  void theResultDoesNotDependOnThreadsOrBands() {
    Img src = noise(300, 200, 4);
    FrameRect f = new FrameRect(12.3, 7.7, 250.1, 140.6);
    Img a = run(src, f, 401, 225);
    try (TileExecutor four = TileExecutor.create(4)) {
      assertThat(run(src, f, 401, 225, four).sameContent(a)).isTrue();
      assertThat(run(src, f, 120, 67, four).sameContent(run(src, f, 120, 67))).isTrue();
    }
    assertThat(run(src, f, 401, 225, TileExecutor.sequential(7)).sameContent(a)).isTrue();
  }

  /**
   * Líneas suaves (gaussianas de σ = 3 px en luz lineal) sobre gris: tras escalar, el centroide de
   * cada línea debe caer donde dice la convención de muestreo. Con líneas de 1 px el centroide de
   * una reducción solo se puede medir con resolución de píxel, por eso son suaves.
   */
  @ParameterizedTest
  @ValueSource(doubles = {0.5, 0.75, 1.5, 2.0, 3.0})
  void linesStayWhereTheConventionPutsThem(double factor) {
    int sw = 512;
    int sh = 4;
    float bg = SrgbEncoder.decode(0x30);
    int[] px = new int[sw * sh];
    for (int x = 0; x < sw; x++) {
      double d = (x % 64) - 32;
      float lin = (float) (bg + 0.6 * Math.exp(-d * d / (2 * 9.0)));
      int v = SrgbEncoder.quantize(lin, 0.5f);
      for (int y = 0; y < sh; y++) {
        px[y * sw + x] = 0xFF000000 | v << 16 | v << 8 | v;
      }
    }
    Img src = Img.adopt(sw, sh, px);
    int dw = (int) Math.round(sw * factor);
    Img out = run(src, new FrameRect(0, 0, sw, sh), dw, sh);
    for (int line = 32; line < sw; line += 64) {
      double expected = (line + 0.5) * dw / sw - 0.5;
      double sum = 0;
      double moment = 0;
      double srcSum = 0;
      double srcMoment = 0;
      for (int x = line - 20; x <= line + 20; x++) {
        double v = SrgbEncoder.decode((src.argb(x, 0) >> 16) & 0xFF) - bg;
        srcSum += v;
        srcMoment += v * x;
      }
      // El centroide de la fuente cuantizada no es exactamente "line": se compara con el suyo.
      double srcCenter = srcMoment / srcSum;
      expected = (srcCenter + 0.5) * dw / sw - 0.5;
      int lo = (int) Math.floor(expected - 20 * factor);
      int hi = (int) Math.ceil(expected + 20 * factor);
      for (int x = Math.max(0, lo); x <= Math.min(dw - 1, hi); x++) {
        double v = SrgbEncoder.decode((out.argb(x, 1) >> 16) & 0xFF) - bg;
        sum += v;
        moment += v * x;
      }
      // 0,02 px: la salida está cuantizada a 8 bits (el spike midió 0,0000 px en coma flotante).
      assertThat(moment / sum).as("línea %d ×%s", line, factor).isCloseTo(expected, offset(0.02));
    }
  }

  /** Una fuente simétrica da una salida simétrica: un desplazamiento de medio píxel lo rompería. */
  @ParameterizedTest
  @ValueSource(ints = {37, 64, 101, 128, 255, 384, 700})
  void mirroredSourcesGiveMirroredOutputs(int dw) {
    Img half = noise(64, 3, 5);
    int[] px = new int[128 * 3];
    for (int y = 0; y < 3; y++) {
      for (int x = 0; x < 64; x++) {
        px[y * 128 + x] = half.argb(x, y);
        px[y * 128 + 127 - x] = half.argb(x, y);
      }
    }
    Img out = run(Img.adopt(128, 3, px), new FrameRect(0, 0, 128, 3), dw, 3);
    for (int x = 0; x < dw / 2; x++) {
      int a = out.argb(x, 1);
      int b = out.argb(dw - 1 - x, 1);
      for (int shift = 0; shift <= 16; shift += 8) {
        assertThat(Math.abs(((a >> shift) & 0xFF) - ((b >> shift) & 0xFF)))
            .as("columna %d de %d", x, dw)
            .isLessThanOrEqualTo(1);
      }
    }
  }

  @Test
  void writesOnlyTheDestinationRectangle() {
    int[] buf = new int[40 * 30];
    java.util.Arrays.fill(buf, 0xFF00FF00);
    Resampler.resampleInto(
        uniform(10, 10, 0xFF102030),
        new FrameRect(0, 0, 10, 10),
        new IntRect(5, 4, 20, 20),
        buf,
        40,
        TileExecutor.sequential(),
        CancellationToken.none(),
        Progress.NONE);
    for (int y = 0; y < 30; y++) {
      for (int x = 0; x < 40; x++) {
        boolean in = x >= 5 && x < 25 && y >= 4 && y < 24;
        assertThat(buf[y * 40 + x]).isEqualTo(in ? 0xFF102030 : 0xFF00FF00);
      }
    }
  }
}
