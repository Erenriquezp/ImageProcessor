package io.github.erenriquezp.rora.core.op;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.CancelledException;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Downscale;
import io.github.erenriquezp.rora.core.image.Img;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** {@link Renderer}: preview, escalado de píxeles, caché, cancelación y progreso. */
class RendererTest {

  /** Operación de prueba que cancela su propio token a mitad de paso. */
  static final class CancellingOp implements Operation {
    @Override
    public String id() {
      return "test.cancel";
    }

    @Override
    public ParamSchema schema() {
      return ParamSchema.EMPTY;
    }

    @Override
    public OpMeta meta() {
      return new OpMeta(OpKind.GLOBAL, true, true, Cost.CHEAP, 0);
    }

    @Override
    public OpResult apply(Img src, Params params, OpContext ctx) {
      ctx.token().cancel();
      ctx.token().throwIfCancelled();
      return OpResult.of(src);
    }
  }

  private static final OpRegistry CORE = OpRegistry.load();
  private static final OpRegistry WITH_CANCEL;

  static {
    List<Operation> ops = new ArrayList<>(CORE.all());
    ops.add(new CancellingOp());
    WITH_CANCEL = OpRegistry.of(ops);
  }

  private static OpSpec spec(OpRegistry reg, String id, Object... kv) {
    Operation op = reg.require(id);
    Params.Builder b = Params.builder(op.schema());
    for (int i = 0; i < kv.length; i += 2) {
      b.set((String) kv[i], kv[i + 1]);
    }
    return OpSpec.of(op, b.build());
  }

  private static final Img SRC = RandomParams.noise(120, 80, 0x5EED_0060L);
  private static final Renderer.SourceImage SOURCE = new Renderer.SourceImage(42, SRC);

  @Test
  void previewIsTheRecipeOnTheDownscaledProxyWithScaledPixelParams() {
    List<OpSpec> steps =
        List.of(
            spec(CORE, "raster.pixelate", "block", 16),
            spec(CORE, "point.saturation", "factor", 1.4f),
            spec(CORE, "accum.motion", "mode", "accum", "displacement", 8));
    Renderer r = new Renderer(CORE, new ResultCache(64L << 20), true);
    Img preview =
        r.render(
                new Renderer.Request(SOURCE, steps, 3, new RenderLevel.Preview(30, 20)),
                OpContext.sequential())
            .image();
    Img proxy =
        Downscale.areaAverage(
            SRC, 30, 20, TileExecutor.sequential(), CancellationToken.none(), Progress.NONE);
    // factor 30/120 = 0,25: bloque 16 -> 4, desplazamiento 8 -> 2.
    List<OpSpec> scaled =
        List.of(
            spec(CORE, "raster.pixelate", "block", 4),
            spec(CORE, "point.saturation", "factor", 1.4f),
            spec(CORE, "accum.motion", "mode", "accum", "displacement", 2));
    assertThat(preview.sameContent(RecipeEquivalenceTest.sequential(proxy, scaled))).isTrue();
    assertThat(preview.width()).isEqualTo(30);
  }

  @Test
  void pixelScalingRespectsTheMinimum() {
    Operation pix = CORE.require("raster.pixelate");
    Params p = Params.builder(pix.schema()).set("block", 3).build();
    assertThat(Renderer.scalePixels(p, 0.1).getInt("block")).isEqualTo(2);
    Operation sat = CORE.require("point.saturation");
    Params s = sat.schema().defaults();
    assertThat(Renderer.scalePixels(s, 0.1)).isSameAs(s);
  }

  @Test
  void proxyIsCachedAndSourceSizedPreviewIsTheFullImage() {
    ResultCache cache = new ResultCache(64L << 20);
    Renderer r = new Renderer(CORE, cache, true);
    r.render(
        new Renderer.Request(SOURCE, List.of(), 0, new RenderLevel.Preview(60, 40)),
        OpContext.sequential());
    assertThat(cache.contains(RenderKey.root(42, new RenderLevel.Preview(60, 40)))).isTrue();
    Img same =
        r.render(
                new Renderer.Request(SOURCE, List.of(), 0, new RenderLevel.Preview(120, 80)),
                OpContext.sequential())
            .image();
    assertThat(same).isSameAs(SRC);
    assertThat(
            r.render(
                    new Renderer.Request(SOURCE, List.of(), 0, RenderLevel.FULL),
                    OpContext.sequential())
                .image())
        .isSameAs(SRC);
  }

  @Test
  void onlyCheckpointsAreCached() {
    List<OpSpec> steps =
        List.of(
            spec(CORE, "color.negative"),
            spec(CORE, "point.saturation", "factor", 1.2f), // fin de un tramo puntual
            spec(CORE, "raster.pixelate", "block", 4), // no puntual: punto de control
            spec(CORE, "color.brightness", "value", 10), // fin de la cabeza (head = 4)
            spec(CORE, "point.luminance"),
            spec(CORE, "color.negative")); // final
    for (boolean fusion : new boolean[] {false, true}) {
      ResultCache cache = new ResultCache(64L << 20);
      Renderer r = new Renderer(CORE, cache, fusion);
      Renderer.Request req = new Renderer.Request(SOURCE, steps, 4, RenderLevel.FULL);
      r.render(req, OpContext.sequential());
      RenderKey k = RenderKey.root(42, RenderLevel.FULL);
      List<Boolean> cached = new ArrayList<>();
      for (OpSpec s : r.executedSteps(req)) {
        k = k.then(s);
        cached.add(cache.contains(k));
      }
      assertThat(cached)
          .as("fusion=" + fusion)
          .containsExactly(false, false, true, true, false, true);
      assertThat(r.keyOf(req)).isEqualTo(k);
    }
  }

  @Test
  void disabledStepsAreSkipped() {
    Renderer r = new Renderer(CORE, new ResultCache(64L << 20), true);
    List<OpSpec> steps = List.of(spec(CORE, "color.negative").withEnabled(false));
    assertThat(
            r.render(
                    new Renderer.Request(SOURCE, steps, 1, RenderLevel.FULL),
                    OpContext.sequential())
                .image())
        .isSameAs(SRC);
  }

  @Test
  void cancellationMidRenderLeavesNoEntryForTheCancelledStepOrLater() {
    List<OpSpec> steps =
        List.of(
            spec(WITH_CANCEL, "raster.pixelate", "block", 4), // control: se cachea
            spec(WITH_CANCEL, "point.saturation", "factor", 1.2f),
            spec(WITH_CANCEL, "test.cancel"),
            spec(WITH_CANCEL, "raster.pixelate", "block", 8));
    ResultCache cache = new ResultCache(64L << 20);
    Renderer r = new Renderer(WITH_CANCEL, cache, true);
    Renderer.Request req = new Renderer.Request(SOURCE, steps, 4, RenderLevel.FULL);
    OpContext ctx =
        new OpContext(TileExecutor.sequential(), CancellationToken.create(), Progress.NONE);
    assertThatThrownBy(() -> r.render(req, ctx)).isInstanceOf(CancelledException.class);
    RenderKey k = RenderKey.root(42, RenderLevel.FULL);
    List<Boolean> cached = new ArrayList<>();
    for (OpSpec s : r.executedSteps(req)) {
      k = k.then(s);
      cached.add(cache.contains(k));
    }
    assertThat(cached).containsExactly(true, false, false, false);
    assertThat(cache.stats().entries()).isEqualTo(1);
    assertThat(cache.weightBytes()).isEqualTo(ResultCache.bytesOf(SRC));
  }

  @Test
  void alreadyCancelledTokenComputesNothing() {
    ResultCache cache = new ResultCache(64L << 20);
    Renderer r = new Renderer(CORE, cache, true);
    CancellationToken t = CancellationToken.create();
    t.cancel();
    assertThatThrownBy(
            () ->
                r.render(
                    new Renderer.Request(
                        SOURCE,
                        List.of(spec(CORE, "color.negative")),
                        1,
                        new RenderLevel.Preview(30, 20)),
                    new OpContext(TileExecutor.sequential(), t, Progress.NONE)))
        .isInstanceOf(CancelledException.class);
    assertThat(cache.stats().entries()).isZero();
  }

  @Test
  void auxOfTheLastComputedStepAndMonotonicProgress() {
    Renderer r = new Renderer(CORE, new ResultCache(64L << 20), true);
    AtomicLong last = new AtomicLong(-1);
    AtomicLong total = new AtomicLong();
    Progress p =
        (done, tot) -> {
          assertThat(done).isGreaterThanOrEqualTo(last.get());
          last.set(done);
          total.set(tot);
        };
    Renderer.Result res =
        r.render(
            new Renderer.Request(
                SOURCE,
                List.of(spec(CORE, "color.negative"), spec(CORE, "analysis.exposure")),
                2,
                RenderLevel.FULL),
            new OpContext(TileExecutor.sequential(), CancellationToken.none(), p));
    assertThat(res.aux()).isInstanceOf(ExposureStats.class);
    assertThat(last.get()).isEqualTo(total.get()).isEqualTo(2 * Renderer.PROGRESS_SCALE);
  }

  @Test
  void paramsBuiltWithAnEqualSchemaInstanceAreRebound() {
    Operation neg = CORE.require("point.saturation");
    ParamSchema copy = ParamSchema.of(neg.schema().params().toArray(new ParamDef[0]));
    assertThat(copy).isNotSameAs(neg.schema()).isEqualTo(neg.schema());
    OpSpec s = new OpSpec(neg.id(), Params.builder(copy).set("factor", 0.5f).build(), true);
    Renderer r = new Renderer(CORE, new ResultCache(64L << 20), false);
    Img out =
        r.render(
                new Renderer.Request(SOURCE, List.of(s), 1, RenderLevel.FULL),
                OpContext.sequential())
            .image();
    assertThat(
            out.sameContent(
                RecipeEquivalenceTest.sequential(
                    SRC, List.of(spec(CORE, "point.saturation", "factor", 0.5f)))))
        .isTrue();
    assertThatThrownBy(() -> s.params().withSchema(ParamSchema.EMPTY))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
