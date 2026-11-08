package io.github.erenriquezp.rora.service;

import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.Params;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Legacy vs núcleo sobre una imagen sintética de 12 MP (4000×3000 {@code TYPE_INT_ARGB}, ruido con
 * semilla fija). Variantes, para atribuir de dónde viene la mejora:
 *
 * <ul>
 *   <li>{@code legacy}: implementación heredada ({@code getRGB/setRGB} por píxel, 1 hilo).
 *   <li>{@code core1Thread}: núcleo sobre {@code Img}, 1 hilo → efecto del acceso directo al
 *       buffer.
 *   <li>{@code coreNThreads}: núcleo sobre {@code Img}, N−1 hilos → efecto del paralelismo.
 *   <li>{@code facade1Thread} / {@code facadeNThreads}: lo mismo incluyendo la conversión {@code
 *       BufferedImage ↔ Img} de ida y vuelta (lo que paga hoy la app; {@code facadeNThreads} es la
 *       llamada real a {@code ImageProcessor}).
 * </ul>
 *
 * Antes de medir se comprueba que legacy y fachada dan exactamente el mismo resultado.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(
    value = 1,
    jvmArgsAppend = {"-Xms4g", "-Xmx4g", "-Djava.awt.headless=true"})
@SuppressWarnings("removal") // el legacy está @Deprecated(forRemoval) a propósito
public class CoreVsLegacyBenchmark {

  @Param({"grayscale", "saturation", "pixelate"})
  public String op;

  private BufferedImage bi;
  private Img img;
  private Operation core;
  private Params params;
  private UnaryOperator<Params.Builder> values;
  private UnaryOperator<BufferedImage> legacy;
  private UnaryOperator<BufferedImage> facade;
  private TileExecutor pool;
  private OpContext ctxN;

  @Setup(Level.Trial)
  public void setUp() {
    int w = 4000;
    int h = 3000;
    int[] px = new int[w * h];
    SplittableRandom rnd = new SplittableRandom(0x5EED_BE7CL);
    for (int i = 0; i < px.length; i++) {
      px[i] = rnd.nextInt();
    }
    bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    bi.getRaster().setDataElements(0, 0, w, h, px);
    img = Img.copyOf(w, h, px);

    OpRegistry reg = OpRegistry.load();
    if (reg.size() != 25) {
      throw new IllegalStateException("Registro incompleto en classpath: " + reg.ids());
    }
    String id;
    switch (op) {
      case "grayscale" -> {
        id = "color.grayscale-average";
        values = p -> p;
        legacy = ColorFilters::grayscale;
        facade = ImageProcessor::grayscale;
      }
      case "saturation" -> {
        id = "point.saturation";
        values = p -> p.set("factor", 1.3f);
        legacy = i -> PointOpsFilters.pointSaturation(i, 1.3f);
        facade = i -> ImageProcessor.pointSaturation(i, 1.3f);
      }
      case "pixelate" -> {
        id = "raster.pixelate";
        values = p -> p.set("block", 8);
        legacy = i -> RasterPipelineFilters.bitmapPixelate(i, 8);
        facade = i -> ImageProcessor.bitmapPixelate(i, 8);
      }
      default -> throw new IllegalArgumentException(op);
    }
    core = reg.require(id);
    params = values.apply(Params.builder(core.schema())).build();
    pool = TileExecutor.create(TileExecutor.defaultParallelism());
    ctxN = OpContext.of(pool);

    int[] a = pixels(legacy.apply(bi));
    int[] b = pixels(facade.apply(bi));
    if (!Arrays.equals(a, b)) {
      throw new IllegalStateException("Legacy y fachada difieren en " + op);
    }
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    pool.close();
  }

  private static int[] pixels(BufferedImage i) {
    return i.getRGB(0, 0, i.getWidth(), i.getHeight(), null, 0, i.getWidth());
  }

  @Benchmark
  public BufferedImage legacy() {
    return legacy.apply(bi);
  }

  @Benchmark
  public Img core1Thread() {
    return core.apply(img, params, OpContext.sequential()).image();
  }

  @Benchmark
  public Img coreNThreads() {
    return core.apply(img, params, ctxN).image();
  }

  @Benchmark
  public BufferedImage facade1Thread() {
    return CoreBridge.apply(core.id(), bi, values, OpContext.sequential());
  }

  @Benchmark
  public BufferedImage facadeNThreads() {
    return facade.apply(bi);
  }
}
