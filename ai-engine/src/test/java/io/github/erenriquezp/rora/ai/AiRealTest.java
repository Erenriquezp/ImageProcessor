package io.github.erenriquezp.rora.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.ai.api.AiEngines;
import io.github.erenriquezp.rora.ai.api.ModelSource;
import io.github.erenriquezp.rora.ai.api.TileRunner;
import io.github.erenriquezp.rora.ai.tile.Tiler;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.CancelledException;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.image.Img;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pruebas con el modelo real, fuera de {@code verify}, porque los umbrales dependen de la máquina.
 * Ejecutar: {@code ./mvnw -pl ai-engine test -Dgroups=ai-real -DexcludedGroups= -Dtest=AiRealTest}.
 */
@Tag("ai-real")
class AiRealTest {

  private static final ModelSource MODEL = AiEngines.bundledModel();
  private static final int THREADS =
      AiEngines.defaultThreads(Runtime.getRuntime().availableProcessors());

  /** Textura de varias octavas: tiene detalle en todas las escalas, como una foto. */
  private static Img texture(int w, int h, long seed) {
    Random rnd = new Random(seed);
    int[] p = new int[w * h];
    float[][] amp = new float[3][6];
    float[][] fx = new float[3][6];
    float[][] fy = new float[3][6];
    float[][] ph = new float[3][6];
    for (int c = 0; c < 3; c++) {
      for (int o = 0; o < 6; o++) {
        amp[c][o] = 40f / (o + 1);
        fx[c][o] = (float) (rnd.nextDouble() * 0.02 * (1 << o));
        fy[c][o] = (float) (rnd.nextDouble() * 0.02 * (1 << o));
        ph[c][o] = (float) (rnd.nextDouble() * 6.28);
      }
    }
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int v = 0xFF;
        for (int c = 0; c < 3; c++) {
          double s = 128;
          for (int o = 0; o < 6; o++) {
            s += amp[c][o] * Math.sin(x * fx[c][o] + y * fy[c][o] + ph[c][o]);
          }
          v = v << 8 | (int) Math.max(0, Math.min(255, s));
        }
        p[y * w + x] = v;
      }
    }
    return Img.adopt(w, h, p);
  }

  private static float[] chw(Img img) {
    int w = img.width();
    int h = img.height();
    float[] v = new float[3 * h * w];
    for (int y = 0, i = 0; y < h; y++) {
      for (int x = 0; x < w; x++, i++) {
        int p = img.argb(x, y);
        v[i] = ((p >> 16) & 0xFF) / 255f;
        v[i + h * w] = ((p >> 8) & 0xFF) / 255f;
        v[i + 2 * h * w] = (p & 0xFF) / 255f;
      }
    }
    return v;
  }

  private static long peakWorkingSetMb() throws Exception {
    if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
      return -1;
    }
    Process p =
        new ProcessBuilder(
                "powershell",
                "-NoProfile",
                "-Command",
                "(Get-Process -Id " + ProcessHandle.current().pid() + ").PeakWorkingSet64")
            .redirectErrorStream(true)
            .start();
    String out = new String(p.getInputStream().readAllBytes()).trim();
    p.waitFor();
    return Long.parseLong(out) >> 20;
  }

  @Test
  void oneMegapixelAtX4StaysWithinTimeAndMemory() throws Exception {
    Img src = texture(1000, 1000, 1);
    long t0 = System.nanoTime();
    try (TileRunner r = AiEngines.onnx().open(MODEL, THREADS)) {
      Img out = new Tiler(256, 32, 4).enhance(src, 4, r, CancellationToken.none(), Progress.NONE);
      assertThat(out.width()).isEqualTo(4000);
    }
    double seconds = (System.nanoTime() - t0) / 1e9;
    long peak = peakWorkingSetMb();
    System.out.printf(
        Locale.ROOT, "AI-REAL 1MPx4 %.1f s, pico WS %d MB, hilos %d%n", seconds, peak, THREADS);
    assertThat(seconds).isLessThan(60);
    if (peak > 0) {
      assertThat(peak).isLessThan(1400);
    }
  }

  @Test
  void tilesAreIndistinguishableFromTheWholeImage() throws Exception {
    Img src = texture(512, 400, 2);
    float[] in = chw(src);
    try (TileRunner r = AiEngines.onnx().open(MODEL, THREADS)) {
      float[] whole = r.run(in, 400, 512, CancellationToken.none());
      Img tiled = new Tiler(256, 32, 4).enhance(src, 4, r, CancellationToken.none(), Progress.NONE);
      int w = 512 * 4;
      int h = 400 * 4;
      double sum = 0;
      int worst = 0;
      for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
          int p = tiled.argb(x, y);
          for (int c = 0; c < 3; c++) {
            int a = (p >> (16 - 8 * c)) & 0xFF;
            float v = whole[c * w * h + y * w + x];
            int b = (int) (Math.max(0f, Math.min(1f, v)) * 255f + 0.5f);
            int d = Math.abs(a - b);
            worst = Math.max(worst, d);
            sum += d * d;
          }
        }
      }
      double mse = sum / (3.0 * w * h);
      double psnr = mse == 0 ? 99 : 10 * Math.log10(255 * 255 / mse);
      System.out.printf(Locale.ROOT, "AI-REAL tiles %.1f dB, max %d%n", psnr, worst);
      assertThat(psnr).isGreaterThanOrEqualTo(60);
    }
  }

  @Test
  void cancellationIsFastAndTheSessionSurvives() throws Exception {
    Img src = texture(256, 256, 3);
    float[] in = chw(src);
    double[] latencies = new double[20];
    try (TileRunner r = AiEngines.onnx().open(MODEL, THREADS)) {
      float[] first = r.run(in, 256, 256, CancellationToken.none());
      Random rnd = new Random(4);
      for (int i = 0; i < latencies.length; i++) {
        CancellationToken t = CancellationToken.create();
        long delay = 50 + rnd.nextInt(150);
        long[] cancelledAt = new long[1];
        Thread canceller =
            new Thread(
                () -> {
                  try {
                    Thread.sleep(delay);
                  } catch (InterruptedException e) {
                    return;
                  }
                  cancelledAt[0] = System.nanoTime();
                  t.cancel();
                });
        canceller.start();
        assertThatThrownBy(() -> r.run(in, 256, 256, t)).isInstanceOf(CancelledException.class);
        long end = System.nanoTime();
        canceller.join();
        latencies[i] = (end - cancelledAt[0]) / 1e6;
      }
      assertThat(r.run(in, 256, 256, CancellationToken.none())).containsExactly(first);
    }
    Arrays.sort(latencies);
    System.out.printf(
        Locale.ROOT,
        "AI-REAL cancelacion mediana %.1f ms, max %.1f ms%n",
        latencies[latencies.length / 2],
        latencies[latencies.length - 1]);
    assertThat(latencies[latencies.length - 1]).isLessThan(200);
  }
}
