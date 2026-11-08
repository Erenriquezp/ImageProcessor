package io.github.erenriquezp.rora.app.fx;

import io.github.erenriquezp.rora.core.image.Img;
import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.image.WritableImage;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Fase 2: conversión {@code Img → WritableImage} reutilizada (lo que hace el worker por frame),
 * {@code INT_ARGB} frente a {@code INT_ARGB_PRE}, con imagen opaca o con alpha, 2 y 4 MP. Arranca
 * el toolkit de JavaFX real (no headless).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(
    value = 1,
    jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
public class FxImagesBenchmark {

  @Param({"INT_ARGB", "INT_ARGB_PRE"})
  public FxImages.Format format;

  @Param({"opaque", "alpha"})
  public String content;

  @Param({"2", "4"})
  public int megapixels;

  private Img img;
  private WritableImage target;

  @Setup(Level.Trial)
  public void setUp() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    try {
      Platform.startup(started::countDown);
    } catch (IllegalStateException alreadyStarted) {
      started.countDown();
    }
    started.await(30, TimeUnit.SECONDS);
    Platform.setImplicitExit(false);
    int w = megapixels == 2 ? 1732 : 2449;
    int h = megapixels == 2 ? 1155 : 1633;
    SplittableRandom rnd = new SplittableRandom(0x5EED_00C0L);
    int[] px = new int[w * h];
    for (int i = 0; i < px.length; i++) {
      int p = rnd.nextInt();
      px[i] = content.equals("opaque") ? (p | 0xFF000000) : p;
    }
    img = Img.adopt(w, h, px);
    target = new WritableImage(w, h);
  }

  @Benchmark
  public WritableImage write() {
    FxImages.write(img, target, format);
    return target;
  }
}
