package io.github.erenriquezp.rora.service;

import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.Params;
import java.util.concurrent.TimeUnit;
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
 * Fase 3: {@code adjust.basic} sobre 12 MP y un tick de slider sobre un proxy de 2 MP (meta: menos
 * de 50 ms), más el coste de las operaciones de vecindad nuevas. Todos los ajustes activos: es el
 * peor caso del kernel.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(
    value = 1,
    jvmArgsAppend = {"-Xms4g", "-Xmx4g", "-Djava.awt.headless=true"})
public class AdjustBenchmark {

  @Param({"1", "7"})
  public int threads;

  private TileExecutor tiles;
  private OpContext context;
  private Img img12;
  private Img proxy2;
  private Operation adjust;
  private Operation blur;
  private Operation sharpen;
  private Params adjustAll;
  private Params blurRadius8;
  private Params blurRadius40;
  private Params sharpenDefault;

  private static Params params(Operation op, Object... kv) {
    Params.Builder b = op.schema().defaults().toBuilder();
    for (int i = 0; i < kv.length; i += 2) {
      b.set((String) kv[i], kv[i + 1]);
    }
    return b.build();
  }

  @Setup(Level.Trial)
  public void setUp() {
    OpRegistry reg = OpRegistry.load();
    tiles = threads == 1 ? TileExecutor.sequential() : TileExecutor.create(threads);
    context = OpContext.of(tiles);
    img12 = PipelineBenchmark.noise(4000, 3000, 0x5EED_00C0L);
    proxy2 = PipelineBenchmark.noise(1732, 1155, 0x5EED_00C1L);
    adjust = reg.require("adjust.basic");
    blur = reg.require("blur.gaussian");
    sharpen = reg.require("detail.unsharp-mask");
    adjustAll =
        params(
            adjust,
            "exposure",
            0.6,
            "contrast",
            25.0,
            "highlights",
            -40.0,
            "shadows",
            35.0,
            "whites",
            10.0,
            "blacks",
            -10.0,
            "temperature",
            15.0,
            "tint",
            -5.0,
            "vibrance",
            20.0,
            "saturation",
            -10.0);
    blurRadius8 = params(blur, "radius", 8.0);
    blurRadius40 = params(blur, "radius", 40.0);
    sharpenDefault = params(sharpen);
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    if (threads != 1) {
      tiles.close();
    }
  }

  @Benchmark
  public Img adjustBasic12Mp() {
    return adjust.apply(img12, adjustAll, context).image();
  }

  @Benchmark
  public Img adjustBasicTick2Mp() {
    return adjust.apply(proxy2, adjustAll, context).image();
  }

  @Benchmark
  public Img blurRadius8Tick2Mp() {
    return blur.apply(proxy2, blurRadius8, context).image();
  }

  @Benchmark
  public Img blurRadius40Tick2Mp() {
    return blur.apply(proxy2, blurRadius40, context).image();
  }

  @Benchmark
  public Img unsharpTick2Mp() {
    return sharpen.apply(proxy2, sharpenDefault, context).image();
  }
}
