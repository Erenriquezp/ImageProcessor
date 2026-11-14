package io.github.erenriquezp.rora.app.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy;
import io.github.erenriquezp.rora.core.op.EditRecipe;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.Operation;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tras un {@code OutOfMemoryError}, vaciar la caché deja la sesión usable. */
class EditSessionMemoryReleaseTest {

  private ManualScheduler time;
  private ManualExecutor preview;
  private ManualExecutor full;
  private ManualExecutor aux;
  private ManualExecutor publisher;
  private EditSession<Img> session;
  private final List<EditSession.Frame<Img>> frames = new ArrayList<>();

  private static long ms(long m) {
    return Duration.ofMillis(m).toNanos();
  }

  @BeforeEach
  void setUp() {
    time = new ManualScheduler();
    preview = new ManualExecutor();
    full = new ManualExecutor();
    aux = new ManualExecutor();
    publisher = new ManualExecutor();
    session =
        new EditSession<>(
            img -> img,
            ProxyPolicy.DEFAULT,
            64L << 20,
            true,
            r ->
                new RenderCoordinator(
                    new RenderLane(
                        "p", r, TileExecutor.sequential(), preview, time, publisher, ms(30)),
                    new RenderLane("f", r, TileExecutor.sequential(), full, time, publisher, 0),
                    new RenderLane("a", r, TileExecutor.sequential(), aux, time, publisher, 0)));
    session.addListener(
        new EditSession.Listener<>() {
          @Override
          public void frameReady(EditSession.Frame<Img> f) {
            frames.add(f);
          }
        });
    session.load(SessionSource.of(new BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB)));
    drain();
  }

  @AfterEach
  void tearDown() {
    session.close();
  }

  private void drain() {
    for (int i = 0; i < 1000; i++) {
      int n = preview.runAll() + full.runAll() + aux.runAll() + publisher.runAll();
      if (n == 0) {
        if (session.isIdle() && time.pendingTasks() == 0) {
          return;
        }
        time.advance(ms(30));
      }
    }
    throw new AssertionError("no converge");
  }

  @Test
  void releaseMemoryEmptiesTheCacheAndRenderingContinues() {
    Operation blur = session.registry().require("blur.gaussian");
    OpSpec step = OpSpec.of(blur, blur.schema().defaults().toBuilder().set("radius", 2.0).build());
    session.applyRecipe(EditRecipe.EMPTY.add(step), null);
    drain();
    assertThat(session.renderer().cache().weightBytes()).isPositive();
    int before = frames.size();
    session.releaseMemory();
    assertThat(session.renderer().cache().weightBytes()).isZero();
    session.requestPreview();
    drain();
    assertThat(frames.size()).isGreaterThan(before);
  }
}
