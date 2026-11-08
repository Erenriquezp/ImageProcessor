package io.github.erenriquezp.rora.core.op;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy.Size;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** La escala de los parámetros en píxeles se calcula en cada paso, no solo con la fuente. */
class RendererPreviewScaleTest {

  /** Reduce la imagen completa a un tamaño fijo y deja el preview tal como llega. */
  static final class ShrinkFullOp implements Operation {
    @Override
    public String id() {
      return "test.shrink-full";
    }

    @Override
    public ParamSchema schema() {
      return ParamSchema.EMPTY;
    }

    @Override
    public OpMeta meta() {
      return new OpMeta(OpKind.GLOBAL, false, false, Cost.CHEAP, 0);
    }

    @Override
    public Size outputSize(Size in, Params params) {
      return in.width() > 500 ? new Size(500, 250) : in;
    }

    @Override
    public OpResult apply(Img src, Params params, OpContext ctx) {
      Size out = outputSize(new Size(src.width(), src.height()), params);
      return OpResult.of(Img.create(out.width(), out.height()));
    }
  }

  /** Conserva el tamaño y tiene un parámetro en píxeles. */
  static final class PixelParamOp implements Operation {
    private static final ParamSchema SCHEMA =
        ParamSchema.of(IntParam.pixels("r", "op.test.pixel.r", 1, 1000, 100, OptionalInt.empty()));

    @Override
    public String id() {
      return "test.pixel";
    }

    @Override
    public ParamSchema schema() {
      return SCHEMA;
    }

    @Override
    public OpMeta meta() {
      return new OpMeta(OpKind.GLOBAL, true, true, Cost.CHEAP, 0);
    }

    @Override
    public OpResult apply(Img src, Params params, OpContext ctx) {
      return OpResult.of(src);
    }
  }

  private static final Operation SHRINK = new ShrinkFullOp();
  private static final Operation PIXEL = new PixelParamOp();
  private static final Renderer RENDERER =
      new Renderer(OpRegistry.of(List.of(SHRINK, PIXEL)), new ResultCache(1L << 20), false);

  private static Renderer.Request request(RenderLevel level, Operation... ops) {
    List<OpSpec> steps = new ArrayList<>();
    for (Operation op : ops) {
      steps.add(OpSpec.of(op, op.schema().defaults()));
    }
    return new Renderer.Request(
        new Renderer.SourceImage(1, Img.create(4000, 2000)), steps, 0, level);
  }

  private static int executedRadius(Renderer.Request req, int index) {
    return req.steps().isEmpty() ? -1 : RENDERER.executedSteps(req).get(index).params().getInt("r");
  }

  @Test
  void withoutGeometryTheScaleIsTheProxyRatio() {
    assertThat(executedRadius(request(new RenderLevel.Preview(1000, 500), PIXEL), 0)).isEqualTo(25);
  }

  @Test
  void theFullLevelKeepsTheOriginalValues() {
    assertThat(executedRadius(request(RenderLevel.FULL, PIXEL), 0)).isEqualTo(100);
  }

  @Test
  void aStepAfterAResizeUsesTheRatioAtThatPoint() {
    // Completa: 4000 → 500. Preview: 1000 → 500. A partir de ahí, preview = completa.
    assertThat(executedRadius(request(new RenderLevel.Preview(1000, 500), SHRINK, PIXEL), 1))
        .isEqualTo(100);
  }

  @Test
  void aStepBeforeAResizeStillUsesTheSourceRatio() {
    assertThat(executedRadius(request(new RenderLevel.Preview(1000, 500), PIXEL, SHRINK), 0))
        .isEqualTo(25);
  }

  @Test
  void theDefaultOutputSizeIsTheInputSize() {
    Size in = new Size(321, 123);
    assertThat(PIXEL.outputSize(in, PIXEL.schema().defaults())).isEqualTo(in);
  }
}
