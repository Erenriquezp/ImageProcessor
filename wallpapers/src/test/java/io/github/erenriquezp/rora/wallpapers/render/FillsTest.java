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
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FillsTest {

  static final CancellationToken NONE = CancellationToken.none();

  /** Foto sintética: degradado suave con un "sujeto" claro. */
  static Img scene(int w, int h) {
    int[] px = new int[w * h];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int r = 40 + 150 * x / Math.max(1, w - 1);
        int g = 60 + 100 * y / Math.max(1, h - 1);
        int b = (x - w / 2) * (x - w / 2) + (y - h / 2) * (y - h / 2) < w * h / 20 ? 230 : 90;
        px[y * w + x] = 0xFF000000 | r << 16 | g << 8 | b;
      }
    }
    return Img.adopt(w, h, px);
  }

  static TargetRenderer.Job contain(Img src, int w, int h, FillSpec fill, boolean dither) {
    return new TargetRenderer.Job(
        w,
        h,
        new FrameRect(0, 0, src.width(), src.height()),
        Fit.contain(src.width(), src.height(), w, h),
        fill,
        dither);
  }

  static Img render(Img src, TargetRenderer.Job job, TileExecutor ex) {
    return TargetRenderer.render(src, job, DominantColor.of(src, NONE), ex, NONE, Progress.NONE);
  }

  @ParameterizedTest
  @EnumSource(FillSpec.Kind.class)
  void everyFillHasExactSizeAndIsDeterministic(FillSpec.Kind kind) {
    Img src = scene(400, 300);
    for (int[] t : new int[][] {{108, 240}, {16, 16}, {640, 40}, {37, 501}}) {
      TargetRenderer.Job job = contain(src, t[0], t[1], new FillSpec(kind, 0.35, 0xFF223344), true);
      Img a = render(src, job, TileExecutor.sequential());
      assertThat(a.width()).isEqualTo(t[0]);
      assertThat(a.height()).isEqualTo(t[1]);
      try (TileExecutor four = TileExecutor.create(4)) {
        assertThat(render(src, job, four).sameContent(a)).as("%s %dx%d", kind, t[0], t[1]).isTrue();
      }
      assertThat(render(src, job, TileExecutor.sequential(5)).sameContent(a)).isTrue();
    }
  }

  @Test
  void theContentIsTheSameWhateverTheFill() {
    Img src = scene(400, 300);
    IntRect c = Fit.contain(400, 300, 108, 240);
    Img blur =
        render(src, contain(src, 108, 240, FillSpec.blur(), true), TileExecutor.sequential());
    Img solid =
        render(
            src,
            contain(src, 108, 240, new FillSpec(FillSpec.Kind.SOLID, 0, 0xFF000000), false),
            TileExecutor.sequential());
    for (int y = c.y(); y < c.bottom(); y++) {
      for (int x = c.x(); x < c.right(); x++) {
        assertThat(blur.argb(x, y)).isEqualTo(solid.argb(x, y));
      }
    }
    assertThat(solid.argb(0, 0)).isEqualTo(0xFF000000);
  }

  @Test
  void theMirrorSeamIsContinuous() {
    Img src = scene(400, 300);
    TargetRenderer.Job job =
        contain(src, 300, 100, new FillSpec(FillSpec.Kind.MIRROR, 0, 0), false);
    Img out = render(src, job, TileExecutor.sequential());
    IntRect c = job.content();
    for (int y = 0; y < 100; y++) {
      assertThat(out.argb(c.x() - 1, y)).isEqualTo(out.argb(c.x(), y));
      assertThat(out.argb(c.right(), y)).isEqualTo(out.argb(c.right() - 1, y));
      assertThat(out.argb(c.x() - 3, y)).isEqualTo(out.argb(c.x() + 2, y));
    }
  }

  @Test
  void theBlurredBackgroundIsSmooth() {
    Img src = scene(400, 300);
    TargetRenderer.Job job = contain(src, 1080, 2400, FillSpec.blur(), false);
    Img out = render(src, job, TileExecutor.sequential());
    int maxStep = 0;
    for (int y = 1; y < job.content().y() - 1; y++) {
      for (int x = 1; x < 1080; x++) {
        for (int s = 0; s <= 16; s += 8) {
          maxStep =
              Math.max(
                  maxStep,
                  Math.abs((out.argb(x, y) >> s & 0xFF) - (out.argb(x - 1, y) >> s & 0xFF)));
          maxStep =
              Math.max(
                  maxStep,
                  Math.abs((out.argb(x, y) >> s & 0xFF) - (out.argb(x, y - 1) >> s & 0xFF)));
        }
      }
    }
    assertThat(maxStep).as("salto máximo entre vecinos del fondo").isLessThanOrEqualTo(2);
  }

  @Test
  void theBlurIsDarkenedByTheStrength() {
    Img src = Img.adopt(64, 64, filled(64 * 64, 0xFFC0C0C0));
    Img light =
        render(
            src,
            contain(src, 64, 200, new FillSpec(FillSpec.Kind.BLUR, 0, 0), false),
            TileExecutor.sequential());
    Img dark =
        render(
            src,
            contain(src, 64, 200, new FillSpec(FillSpec.Kind.BLUR, 0.5, 0), false),
            TileExecutor.sequential());
    assertThat(light.argb(10, 5)).isEqualTo(0xFFC0C0C0);
    float half = SrgbEncoder.decode(0xC0) * 0.5f;
    assertThat(dark.argb(10, 5) & 0xFF).isEqualTo(SrgbEncoder.quantize(half, 0.5f));
  }

  static int[] filled(int n, int argb) {
    int[] px = new int[n];
    Arrays.fill(px, argb);
    return px;
  }

  /**
   * Rampa oscura de 6 a 38 niveles: con tramado, el error tras un filtro paso bajo de 32 px queda
   * por debajo de 0,01 niveles (Puerta 0: 0,003); sin tramado, en torno a 0,1.
   */
  @Test
  void ditheringRemovesBanding() {
    int w = 3840;
    int h = 256;
    double[] noDither = new double[1];
    double[] withDither = new double[1];
    for (boolean dither : new boolean[] {false, true}) {
      double[] got = new double[w];
      double[] want = new double[w];
      for (int x = 0; x < w; x++) {
        double s = 6 + 32.0 * x / (w - 1);
        float lin = (float) io.github.erenriquezp.rora.core.color.SrgbLut.decode(s / 255);
        want[x] = SrgbEncoder.encode255(lin);
        for (int y = 0; y < h; y++) {
          got[x] += (Dither.pixel(lin, lin, lin, x, y, dither) & 0xFF) / (double) h;
        }
      }
      double se = 0;
      int n = 0;
      for (int x = 16; x + 16 <= w; x += 32) {
        double a = 0;
        double b = 0;
        for (int i = x - 16; i < x + 16; i++) {
          a += got[i] / 32;
          b += want[i] / 32;
        }
        se += (a - b) * (a - b);
        n++;
      }
      (dither ? withDither : noDither)[0] = Math.sqrt(se / n);
    }
    assertThat(withDither[0]).isLessThan(0.01);
    assertThat(noDither[0]).isGreaterThan(0.05);
  }

  @Test
  void ditherOffsetsAreUniformAndPositional() {
    int[] hist = new int[10];
    for (int y = 0; y < 300; y++) {
      for (int x = 0; x < 300; x++) {
        float v = Dither.offset(x, y, 1);
        assertThat(v).isGreaterThanOrEqualTo(0f).isLessThan(1f);
        hist[(int) (v * 10)]++;
      }
    }
    for (int c : hist) {
      assertThat(c).isBetween(8_500, 9_500);
    }
    assertThat(Dither.offset(7, 9, 2)).isEqualTo(Dither.offset(7, 9, 2));
    assertThat(Dither.offset(7, 9, 2)).isNotEqualTo(Dither.offset(9, 7, 2));
  }

  @Test
  void dominantColorFindsTheMajority() {
    int[] px = new int[100 * 100];
    for (int i = 0; i < px.length; i++) {
      px[i] = i % 100 < 70 ? 0xFFC03020 : 0xFF2040D0;
    }
    float[] c = DominantColor.of(Img.adopt(100, 100, px), NONE);
    assertThat(SrgbEncoder.quantize(c[0], 0.5f)).isEqualTo(0xC0);
    assertThat(SrgbEncoder.quantize(c[2], 0.5f)).isEqualTo(0x20);
    assertThat(DominantColor.of(Img.adopt(100, 100, px), NONE)).containsExactly(c);
    float[] u = DominantColor.of(Img.adopt(3, 1, filled(3, 0xFF808080)), NONE);
    assertThat(u[1]).isCloseTo(SrgbEncoder.decode(0x80), offset(1e-6f));
  }

  @Test
  void translucentSourcesAreFlattenedOverTheBackground() {
    Img opaque = Img.adopt(2, 1, new int[] {0xFF102030, 0xFF405060});
    assertThat(TargetRenderer.opaque(opaque, 0xFFFFFFFF)).isSameAs(opaque);
    Img glass = Img.adopt(2, 1, new int[] {0x00000000, 0xFF405060});
    Img flat = TargetRenderer.opaque(glass, 0xFFFFFFFF);
    assertThat(flat.argb(0, 0)).isEqualTo(0xFFFFFFFF);
    assertThat(flat.argb(1, 0)).isEqualTo(0xFF405060);
  }

  @Test
  void reflectRepeatsTheEdge() {
    assertThat(Fills.reflect(-1, 0, 5)).isEqualTo(0);
    assertThat(Fills.reflect(-2, 0, 5)).isEqualTo(1);
    assertThat(Fills.reflect(5, 0, 5)).isEqualTo(4);
    assertThat(Fills.reflect(11, 0, 5)).isEqualTo(1);
    assertThat(Fills.reflect(-12, 10, 11)).isEqualTo(10);
  }
}
