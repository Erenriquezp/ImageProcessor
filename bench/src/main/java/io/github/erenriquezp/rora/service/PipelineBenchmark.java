package io.github.erenriquezp.rora.service;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.op.PointFusion;
import io.github.erenriquezp.rora.core.op.PointOperation;
import io.github.erenriquezp.rora.core.op.RenderLevel;
import io.github.erenriquezp.rora.core.op.Renderer;
import io.github.erenriquezp.rora.core.op.ResultCache;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
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
 * Fase 2: latencia de un tick de slider (ver también {@link FusionProxyBenchmark}).
 *
 * <ul>
 *   <li>{@code tick}: un tick de slider sobre un proxy de 2 MP (de una fuente de 24 MP) con un
 *       prefijo de 10 pasos ya cacheado: render del pendiente (valor nuevo en cada invocación,
 *       nunca en caché). Meta del roadmap: &lt; 50 ms para operaciones puntuales.
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(
    value = 1,
    jvmArgsAppend = {"-Xms4g", "-Xmx4g", "-Djava.awt.headless=true"})
public class PipelineBenchmark {

  /** Paso pendiente del tick: puntuales y una de vecindad como referencia. */
  @Param({"point.saturation", "color.brightness", "point.scale-bias", "raster.pixelate"})
  public String tickOp;

  @Param({"1", "7"})
  public int threads;

  private OpRegistry registry;
  private TileExecutor tiles;
  private Img source24;
  private Img img12;
  private Renderer renderer;
  private Renderer.SourceImage source;
  private List<OpSpec> prefix;
  private RenderLevel.Preview level;
  private List<PointFusion.Stage> chain;
  private int counter;

  static Img noise(int w, int h, long seed) {
    SplittableRandom rnd = new SplittableRandom(seed);
    int[] px = new int[w * h];
    for (int i = 0; i < px.length; i++) {
      px[i] = rnd.nextInt() | 0xFF000000;
    }
    return Img.adopt(w, h, px);
  }

  private OpSpec spec(String id, Object... kv) {
    Operation op = registry.require(id);
    Params.Builder b = Params.builder(op.schema());
    for (int i = 0; i < kv.length; i += 2) {
      b.set((String) kv[i], kv[i + 1]);
    }
    return OpSpec.of(op, b.build());
  }

  @Setup(Level.Trial)
  public void setUp() {
    registry = LegacySteps.registryWithLegacy(SourceFormat.NativeSource.NONE);
    if (registry.size() != 25 + 15) {
      throw new IllegalStateException("Registro incompleto: " + registry.ids());
    }
    tiles = threads == 1 ? TileExecutor.sequential() : TileExecutor.create(threads);
    source24 = noise(6000, 4000, 0x5EED_00A0L);
    img12 = noise(4000, 3000, 0x5EED_00A1L);
    // 64 MB: cabeza (≈8 MB) + pocos resultados; un valor repetido ya se ha expulsado (LRU).
    renderer = new Renderer(registry, new ResultCache(64L << 20), true);
    source = new Renderer.SourceImage(1, source24);
    // ≈ 2 MP con la relación de aspecto de la fuente.
    level = new RenderLevel.Preview(1732, 1155);
    prefix =
        List.of(
            spec("color.brightness", "value", 15),
            spec("point.saturation", "factor", 1.2f),
            spec("raster.pixelate", "block", 4),
            spec("tone.equalize", "factor", 0.6f),
            spec("point.hue-rotate", "degrees", 20f),
            spec("legacy.frosted"),
            spec("point.scale-bias", "scale", 1.1f, "bias", 5f),
            spec("legacy.convolution", "kernel", "sharpen"),
            spec("point.color-gain", "gainR", 1.1f, "gainG", 0.9f, "gainB", 1.0f),
            spec("tone.posterize", "levels", 32));
    // Calienta la caché: cabeza (los 10 pasos) en el proxy.
    renderer.render(new Renderer.Request(source, prefix, prefix.size(), level), ctx());
    chain = new ArrayList<>();
    for (OpSpec s :
        List.of(
            spec("color.brightness", "value", 10),
            spec("point.scale-bias", "scale", 1.1f, "bias", -4f),
            spec("color.negative"),
            spec("point.color-gain", "gainR", 1.1f, "gainG", 0.95f, "gainB", 1.05f),
            spec("tone.posterize", "levels", 64))) {
      chain.add(new PointFusion.Stage((PointOperation) registry.require(s.opId()), s.params()));
    }
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    tiles.close();
  }

  private OpContext ctx() {
    return new OpContext(tiles, CancellationToken.none(), Progress.NONE);
  }

  private OpSpec pendingFor(int i) {
    // Valor nuevo en cada invocación: el resultado nunca está en caché.
    return switch (tickOp) {
      case "point.saturation" -> spec(tickOp, "factor", 0.5f + (i % 100_000) * 1e-5f);
      case "color.brightness" -> spec(tickOp, "value", (i % 200) - 100);
      case "point.scale-bias" -> spec(tickOp, "scale", 0.5f + (i % 100_000) * 1e-5f, "bias", 0f);
      // Bloques que dan 17 bloques distintos en el proxy (escala 1732/6000): sin aciertos.
      default -> spec(tickOp, "block", (int) Math.round((2 + i % 17) * 6000.0 / 1732));
    };
  }

  @Benchmark
  public Img tick() {
    List<OpSpec> steps = new ArrayList<>(prefix);
    steps.add(pendingFor(counter++));
    Renderer.Result r =
        renderer.render(new Renderer.Request(source, steps, prefix.size(), level), ctx());
    if (r.stepsComputed() != 1) {
      throw new IllegalStateException("El prefijo no estaba en caché: " + r.stepsComputed());
    }
    return r.image();
  }
}
