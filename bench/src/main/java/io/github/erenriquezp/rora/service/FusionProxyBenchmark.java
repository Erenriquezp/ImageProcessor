package io.github.erenriquezp.rora.service;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Downscale;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.op.PointFusion;
import io.github.erenriquezp.rora.core.op.PointOperation;
import java.util.ArrayList;
import java.util.List;
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
 * Fase 2: beneficio de la fusión (cadena de 5 puntuales sobre 12 MP, fusionada o paso a paso) y
 * coste de la reducción a proxy (24 MP → 2 MP y → 4 MP), con 1 y N−1 hilos.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(
    value = 1,
    jvmArgsAppend = {"-Xms4g", "-Xmx4g", "-Djava.awt.headless=true"})
public class FusionProxyBenchmark {

  @Param({"1", "7"})
  public int threads;

  private TileExecutor tiles;
  private Img img12;
  private Img source24;
  private List<PointFusion.Stage> chain;

  private static PointFusion.Stage stage(OpRegistry reg, String id, Object... kv) {
    Operation op = reg.require(id);
    Params.Builder b = Params.builder(op.schema());
    for (int i = 0; i < kv.length; i += 2) {
      b.set((String) kv[i], kv[i + 1]);
    }
    return new PointFusion.Stage((PointOperation) op, b.build());
  }

  @Setup(Level.Trial)
  public void setUp() {
    OpRegistry reg = OpRegistry.load();
    tiles = threads == 1 ? TileExecutor.sequential() : TileExecutor.create(threads);
    img12 = PipelineBenchmark.noise(4000, 3000, 0x5EED_00B0L);
    source24 = PipelineBenchmark.noise(6000, 4000, 0x5EED_00B1L);
    chain = new ArrayList<>();
    chain.add(stage(reg, "color.brightness", "value", 10));
    chain.add(stage(reg, "point.scale-bias", "scale", 1.1f, "bias", -4f));
    chain.add(stage(reg, "color.negative"));
    chain.add(stage(reg, "point.color-gain", "gainR", 1.1f, "gainG", 0.95f, "gainB", 1.05f));
    chain.add(stage(reg, "tone.posterize", "levels", 64));
    Img a = fused();
    Img b = sequential();
    if (!a.sameContent(b)) {
      throw new IllegalStateException("Fusión no exacta");
    }
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    tiles.close();
  }

  private OpContext ctx() {
    return new OpContext(tiles, CancellationToken.none(), Progress.NONE);
  }

  @Benchmark
  public Img fused() {
    return PointFusion.apply(img12, chain, ctx());
  }

  @Benchmark
  public Img sequential() {
    Img cur = img12;
    for (PointFusion.Stage s : chain) {
      cur = s.op().apply(cur, s.params(), ctx()).image();
    }
    return cur;
  }

  @Benchmark
  public Img proxy2mp() {
    return Downscale.areaAverage(
        source24, 1732, 1155, tiles, CancellationToken.none(), Progress.NONE);
  }

  @Benchmark
  public Img proxy4mp() {
    return Downscale.areaAverage(
        source24, 2449, 1633, tiles, CancellationToken.none(), Progress.NONE);
  }
}
