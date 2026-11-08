package io.github.erenriquezp.rora.app.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.op.RenderLevel;
import io.github.erenriquezp.rora.core.op.Renderer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Coordinador real (hilos de verdad): un paso no cancelable bloqueado en el carril FULL no impide
 * que el PREVIEW termine. Sincronización solo con latches y futuros (sin {@code sleep}).
 */
class RenderCoordinatorTest {

  @Test
  @Timeout(30)
  void blockedFullNeverBlocksPreview() throws Exception {
    TestOps.BlockingOp block = new TestOps.BlockingOp();
    OpRegistry reg = TestOps.registry(block);
    Renderer renderer = TestOps.renderer(reg);
    try (RenderCoordinator c = RenderCoordinator.create(renderer, Runnable::run)) {
      CompletableFuture<Renderer.Result> fullDone = new CompletableFuture<>();
      Operation op = reg.require("test.block");
      OpSpec blocking = OpSpec.of(op, Params.builder(op.schema()).set("n", 1).build());
      c.full(TestOps.request(List.of(blocking)), fullDone::complete);
      assertThat(block.entered.await(10, TimeUnit.SECONDS)).isTrue();

      CompletableFuture<Renderer.Result> previewDone = new CompletableFuture<>();
      Renderer.Request preview =
          new Renderer.Request(
              TestOps.SOURCE,
              List.of(TestOps.brightness(reg, 20)),
              1,
              new RenderLevel.Preview(8, 6));
      c.preview(preview, previewDone::complete);
      Renderer.Result p = previewDone.get(10, TimeUnit.SECONDS);
      assertThat(p.image().width()).isEqualTo(8);
      assertThat(fullDone).isNotDone();

      block.release.countDown();
      assertThat(fullDone.get(10, TimeUnit.SECONDS).image().width()).isEqualTo(16);
    }
  }

  @Test
  @Timeout(30)
  void exportAndInteractiveFullWithTheSameKeyShareOneRender() throws Exception {
    TestOps.BlockingOp block = new TestOps.BlockingOp();
    OpRegistry reg = TestOps.registry(block);
    try (RenderCoordinator c = RenderCoordinator.create(TestOps.renderer(reg), Runnable::run)) {
      Operation op = reg.require("test.block");
      Renderer.Request req =
          TestOps.request(List.of(OpSpec.of(op, Params.builder(op.schema()).set("n", 2).build())));
      CompletableFuture<Renderer.Result> a = new CompletableFuture<>();
      CompletableFuture<Renderer.Result> b = new CompletableFuture<>();
      c.export(req, a::complete);
      assertThat(block.entered.await(10, TimeUnit.SECONDS)).isTrue();
      c.full(req, b::complete);
      block.release.countDown();
      assertThat(a.get(10, TimeUnit.SECONDS)).isSameAs(b.get(10, TimeUnit.SECONDS));
    }
  }
}
