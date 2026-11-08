package io.github.erenriquezp.rora.app.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy;
import io.github.erenriquezp.rora.core.op.EditRecipe;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.Params;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Edición directa de la receta (UI nueva) en {@link EditSession}, con todo manual. */
class EditSessionRecipeTest {

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

  private OpSpec spec(String id, Object... kv) {
    Operation op = session.registry().require(id);
    Params.Builder b = op.schema().defaults().toBuilder();
    for (int i = 0; i < kv.length; i += 2) {
      b.set((String) kv[i], kv[i + 1]);
    }
    return OpSpec.of(op, b.build());
  }

  @Test
  void aGestureIsOneUndoEntry() {
    Object gesture = new Object();
    for (double ev : new double[] {0.1, 0.2, 0.3}) {
      session.applyRecipe(EditRecipe.EMPTY.add(spec("adjust.basic", "exposure", ev)), gesture);
    }
    session.endGesture();
    assertThat(session.undoDepth()).isEqualTo(1);
    session.undo();
    assertThat(session.recipe()).isEqualTo(EditRecipe.EMPTY);
  }

  @Test
  void aGestureThatReturnsToTheStartLeavesNoEntry() {
    Object gesture = new Object();
    session.applyRecipe(EditRecipe.EMPTY.add(spec("adjust.basic", "exposure", 0.1)), gesture);
    session.applyRecipe(EditRecipe.EMPTY, gesture);
    session.endGesture();
    assertThat(session.undoDepth()).isZero();
  }

  @Test
  void resetCanBeUndone() {
    EditRecipe r = EditRecipe.EMPTY.add(spec("color.negative"));
    session.applyRecipe(r, null);
    session.resetRecipe();
    assertThat(session.recipe()).isEqualTo(EditRecipe.EMPTY);
    session.undo();
    assertThat(session.recipe()).isEqualTo(r);
  }

  @Test
  void theFrameReportsTheSizeAfterGeometry() {
    session.applyRecipe(EditRecipe.EMPTY.add(spec("transform.rotate90", "turns", 1)), null);
    drain();
    EditSession.Frame<Img> f = frames.get(frames.size() - 1);
    assertThat(f.fullWidth()).isEqualTo(20);
    assertThat(f.fullHeight()).isEqualTo(40);
    assertThat(session.outputSize().width()).isEqualTo(20);
  }

  @Test
  void anExcludedOperationIsNotApplied() {
    OpSpec crop = spec("transform.crop", "right", 0.5);
    session.applyRecipe(EditRecipe.EMPTY.add(crop), null);
    drain();
    assertThat(frames.get(frames.size() - 1).fullWidth()).isEqualTo(20);
    session.setExcludedOps(Set.of("transform.crop"));
    drain();
    assertThat(frames.get(frames.size() - 1).fullWidth()).isEqualTo(40);
    assertThat(session.recipe().steps()).containsExactly(crop);
  }

  @Test
  void beforeAppliesOnlyTheGeometry() {
    session.applyRecipe(
        EditRecipe.EMPTY.add(spec("transform.rotate90", "turns", 1)).add(spec("color.negative")),
        null);
    drain();
    List<EditSession.Frame<Img>> before = new ArrayList<>();
    session.before(false, img -> img, before::add, f -> {});
    drain();
    assertThat(before).hasSize(1);
    assertThat(before.get(0).pixels().width()).isEqualTo(20);
    assertThat(before.get(0).pixels().argb(0, 0)).isEqualTo(0);
  }

  @Test
  void aRemovedListenerIsNotCalled() {
    int count = frames.size();
    List<Integer> calls = new ArrayList<>();
    EditSession.Listener<Img> l =
        new EditSession.Listener<>() {
          @Override
          public void historyChanged(int depth, boolean canUndo, boolean canRedo) {
            calls.add(depth);
          }
        };
    session.addListener(l);
    session.removeListener(l);
    session.applyRecipe(EditRecipe.EMPTY.add(spec("color.negative")), null);
    drain();
    assertThat(calls).isEmpty();
    assertThat(frames.size()).isGreaterThan(count);
  }
}
