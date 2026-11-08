package io.github.erenriquezp.rora.app.session;

import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.Cost;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpKind;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.OpResult;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.op.RenderLevel;
import io.github.erenriquezp.rora.core.op.Renderer;
import io.github.erenriquezp.rora.core.op.ResultCache;
import io.github.erenriquezp.rora.service.LegacySteps;
import io.github.erenriquezp.rora.service.SourceFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Operaciones de prueba para los carriles: bloqueante, que falla y registros. */
final class TestOps {

  private TestOps() {}

  /** Bloquea en {@code release} tras avisar por {@code entered}; no comprueba la cancelación. */
  static final class BlockingOp implements Operation {
    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    private final ParamSchema schema =
        ParamSchema.of(new IntParam("n", "test.n", 0, 1000, 0, OptionalInt.empty()));

    @Override
    public String id() {
      return "test.block";
    }

    @Override
    public ParamSchema schema() {
      return schema;
    }

    @Override
    public OpMeta meta() {
      return new OpMeta(OpKind.GLOBAL, true, true, Cost.EXPENSIVE, 0);
    }

    @Override
    public OpResult apply(Img src, Params params, OpContext ctx) {
      entered.countDown();
      try {
        if (!release.await(30, TimeUnit.SECONDS)) {
          throw new IllegalStateException("BlockingOp no liberada");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
      return OpResult.of(src);
    }
  }

  /** Lanza lo que da {@code error}. */
  static final class FailingOp implements Operation {
    private final Supplier<? extends Throwable> error;

    FailingOp(Supplier<? extends Throwable> error) {
      this.error = error;
    }

    @Override
    public String id() {
      return "test.fail";
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
      Throwable t = error.get();
      if (t instanceof RuntimeException re) {
        throw re;
      }
      throw (Error) t;
    }
  }

  static OpRegistry registry(Operation... extra) {
    List<Operation> ops =
        new ArrayList<>(LegacySteps.registryWithLegacy(SourceFormat.NativeSource.NONE).all());
    ops.addAll(List.of(extra));
    return OpRegistry.of(ops);
  }

  static Renderer renderer(OpRegistry reg) {
    return new Renderer(reg, new ResultCache(64L << 20), true);
  }

  static final Renderer.SourceImage SOURCE = new Renderer.SourceImage(7, Img.create(16, 12));

  static OpSpec brightness(OpRegistry reg, int v) {
    Operation op = reg.require("color.brightness");
    return OpSpec.of(op, Params.builder(op.schema()).set("value", v).build());
  }

  static OpSpec spec(OpRegistry reg, String id) {
    return OpSpec.defaults(reg.require(id));
  }

  static Renderer.Request request(List<OpSpec> steps) {
    return new Renderer.Request(SOURCE, steps, steps.size(), RenderLevel.FULL);
  }
}
