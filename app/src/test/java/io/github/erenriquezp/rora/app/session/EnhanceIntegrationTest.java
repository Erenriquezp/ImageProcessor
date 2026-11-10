package io.github.erenriquezp.rora.app.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.ai.api.AiEngine;
import io.github.erenriquezp.rora.ai.api.ModelInfo;
import io.github.erenriquezp.rora.ai.api.ModelSource;
import io.github.erenriquezp.rora.ai.api.TileRunner;
import io.github.erenriquezp.rora.ai.store.ArtifactStore;
import io.github.erenriquezp.rora.app.model.RecipeEdits;
import io.github.erenriquezp.rora.app.session.ai.AiJobService;
import io.github.erenriquezp.rora.app.session.ai.AiLimits;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy;
import io.github.erenriquezp.rora.core.image.ProxyPolicy.Size;
import io.github.erenriquezp.rora.core.op.EditRecipe;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.Params;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** La mejora dentro de la sesión, con un motor simulado que pinta de blanco. */
class EnhanceIntegrationTest {

  private static final int W = 40;
  private static final int H = 20;

  private static final ModelInfo INFO = new ModelInfo("fake", 4, 16, 4, "sha", 1, "", "", "");

  private final ManualScheduler time = new ManualScheduler();
  private final ManualExecutor preview = new ManualExecutor();
  private final ManualExecutor full = new ManualExecutor();
  private final ManualExecutor aux = new ManualExecutor();
  private final ManualExecutor publisher = new ManualExecutor();
  private final ManualExecutor aiWorker = new ManualExecutor();
  private final List<EditSession.Frame<Img>> frames = new ArrayList<>();
  private int engineRuns;

  @TempDir Path root;

  private EditSession<Img> session;
  private AiJobService jobs;

  private final AiEngine engine =
      (model, threads) ->
          new TileRunner() {
            @Override
            public float[] run(float[] chw, int h, int w, CancellationToken t) {
              engineRuns++;
              float[] out = new float[3 * h * 4 * w * 4];
              Arrays.fill(out, 1f);
              return out;
            }

            @Override
            public void close() {}
          };

  private static final ModelSource MODEL =
      new ModelSource() {
        @Override
        public ModelInfo info() {
          return INFO;
        }

        @Override
        public byte[] readVerified() {
          return new byte[0];
        }
      };

  private static long ms(long m) {
    return Duration.ofMillis(m).toNanos();
  }

  @BeforeEach
  void setUp() {
    jobs =
        new AiJobService(
            engine,
            MODEL,
            () -> ArtifactStore.open(root, 1 << 24, 1 << 22),
            aiWorker,
            publisher,
            time,
            new AiJobService.Timing() {
              @Override
              public OptionalDouble load() {
                return OptionalDouble.empty();
              }

              @Override
              public void save(double s) {}
            },
            new AiLimits.Memory() {
              @Override
              public long freeHeapBytes() {
                return Long.MAX_VALUE;
              }

              @Override
              public long freePhysicalBytes() {
                return Long.MAX_VALUE;
              }
            },
            2,
            null);
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
                    new RenderLane("a", r, TileExecutor.sequential(), aux, time, publisher, 0)),
            jobs);
    session.addListener(
        new EditSession.Listener<>() {
          @Override
          public void frameReady(EditSession.Frame<Img> f) {
            frames.add(f);
          }
        });
    session.load(SessionSource.of(new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB)));
    drain();
  }

  @AfterEach
  void tearDown() {
    session.close();
    aiWorker.runAll();
  }

  private void drain() {
    for (int i = 0; i < 1000; i++) {
      int n =
          preview.runAll() + full.runAll() + aux.runAll() + aiWorker.runAll() + publisher.runAll();
      if (n == 0) {
        if (session.isIdle() && !jobs.isRunning()) {
          time.advance(ms(30));
          if (preview.runAll() + full.runAll() + aux.runAll() + publisher.runAll() == 0) {
            return;
          }
        } else {
          time.advance(ms(30));
        }
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

  private void addEnhance(double intensity) {
    session.applyRecipe(
        RecipeEdits.putEnhance(
            session.recipe(), spec(RecipeEdits.ENHANCE, "scale", "X4", "intensity", intensity)),
        null);
    drain();
  }

  private AiJobService.State state() {
    return jobs.state(session.enhanceStep().orElseThrow().key());
  }

  private Img export() {
    Img[] out = new Img[1];
    session.exportImg(
        i -> out[0] = i,
        f -> {
          throw new AssertionError(f);
        });
    drain();
    return out[0];
  }

  @Test
  void addingTheStepNeverRunsTheEngineAndPreviewsStayAtInputSize() {
    addEnhance(100);
    assertThat(engineRuns).isZero();
    assertThat(state()).isEqualTo(AiJobService.State.NOT_RUN);
    assertThat(session.outputSize()).isEqualTo(new Size(W * 4, H * 4));
    assertThat(session.proxySize()).contains(new Size(W, H));
    EditSession.Frame<Img> last = frames.get(frames.size() - 1);
    assertThat(last.pixels().width()).isEqualTo(W);
    assertThat(last.fullWidth()).isEqualTo(W * 4);
  }

  @Test
  void slidersAfterTheStepNeverRunTheEngine() {
    addEnhance(100);
    for (double ev : new double[] {0.1, 0.2, 0.3}) {
      session.applyRecipe(
          RecipeEdits.putAdjust(session.recipe(), spec("adjust.basic", "exposure", ev)), "g");
      drain();
    }
    assertThat(engineRuns).isZero();
  }

  @Test
  void dimensionsAreTheSameWithAndWithoutTheEngine() {
    addEnhance(100);
    Img classic = export();
    session.startEnhance();
    drain();
    assertThat(state()).isEqualTo(AiJobService.State.READY);
    Img enhanced = export();
    assertThat(enhanced.width()).isEqualTo(classic.width()).isEqualTo(W * 4);
    assertThat(enhanced.height()).isEqualTo(classic.height());
    assertThat(enhanced.argb(5, 5) & 0xFFFFFF).isEqualTo(0xFFFFFF);
    assertThat(classic.argb(5, 5) & 0xFFFFFF).isNotEqualTo(0xFFFFFF);
  }

  @Test
  void changingTheIntensityBlendsWithoutCallingTheEngineAgain() {
    addEnhance(100);
    session.startEnhance();
    drain();
    int runs = engineRuns;
    assertThat(runs).isPositive();
    addEnhance(50);
    assertThat(state()).isEqualTo(AiJobService.State.READY);
    Img half = export();
    assertThat(engineRuns).isEqualTo(runs);
    int v = half.argb(5, 5) & 0xFF;
    assertThat(v).isBetween(100, 160);
  }

  @Test
  void aDisabledStepNeverRunsAndHasNoKey() {
    addEnhance(100);
    int i = RecipeEdits.indexOf(session.recipe(), RecipeEdits.ENHANCE);
    EditRecipe off =
        session.recipe().replace(i, session.recipe().steps().get(i).withEnabled(false));
    session.applyRecipe(off, null);
    drain();
    assertThat(session.enhanceStep()).isEmpty();
    assertThat(session.startEnhance()).isFalse();
    assertThat(engineRuns).isZero();
  }

  @Test
  void aGeometryChangeMakesItStaleAndUndoRecoversTheResult() {
    addEnhance(100);
    session.startEnhance();
    drain();
    int runs = engineRuns;
    var readyKey = session.enhanceStep().orElseThrow().key();
    int id = jobs.artifactFor(readyKey);

    session.applyRecipe(
        RecipeEdits.putGeometry(session.recipe(), spec(RecipeEdits.ROTATE, "turns", 1)), null);
    drain();
    assertThat(state()).isEqualTo(AiJobService.State.STALE);
    assertThat(export().width()).isEqualTo(H * 4);
    assertThat(export().argb(5, 5) & 0xFFFFFF).isNotEqualTo(0xFFFFFF);

    session.undo();
    drain();
    assertThat(session.enhanceStep().orElseThrow().key()).isEqualTo(readyKey);
    assertThat(jobs.artifactFor(readyKey)).isEqualTo(id);
    assertThat(state()).isEqualTo(AiJobService.State.READY);
    assertThat(engineRuns).isEqualTo(runs);
  }

  @Test
  void adjustmentsAfterTheStepDoNotInvalidateIt() {
    addEnhance(100);
    session.startEnhance();
    drain();
    session.applyRecipe(
        RecipeEdits.putAdjust(session.recipe(), spec("adjust.basic", "exposure", 0.5)), null);
    drain();
    assertThat(state()).isEqualTo(AiJobService.State.READY);
  }

  @Test
  void aGeometryChangeDuringAJobCancelsIt() {
    addEnhance(100);
    session.startEnhance();
    assertThat(jobs.isRunning()).isTrue();
    session.applyRecipe(
        RecipeEdits.putGeometry(session.recipe(), spec(RecipeEdits.ROTATE, "turns", 1)), null);
    drain();
    assertThat(jobs.isRunning()).isFalse();
    assertThat(engineRuns).isZero();
    assertThat(state()).isEqualTo(AiJobService.State.NOT_RUN);
  }

  @Test
  void loadingAnotherImageForgetsTheResults() {
    addEnhance(100);
    session.startEnhance();
    drain();
    session.load(SessionSource.of(new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB)));
    drain();
    addEnhance(100);
    assertThat(state()).isEqualTo(AiJobService.State.NOT_RUN);
  }

  @Test
  void theUsersRecipeNeverContainsAnArtifact() {
    addEnhance(100);
    session.startEnhance();
    drain();
    int i = RecipeEdits.indexOf(session.recipe(), RecipeEdits.ENHANCE);
    assertThat(session.recipe().steps().get(i).params().getInt("artifact")).isZero();
  }

  @Test
  void theStepStaysRightAfterTheGeometry() {
    addEnhance(100);
    session.applyRecipe(
        RecipeEdits.putAdjust(session.recipe(), spec("adjust.basic", "exposure", 0.5)), null);
    session.applyRecipe(
        RecipeEdits.putGeometry(session.recipe(), spec(RecipeEdits.ROTATE, "turns", 1)), null);
    assertThat(session.recipe().steps().stream().map(OpSpec::opId))
        .containsExactly(RecipeEdits.ROTATE, RecipeEdits.ENHANCE, "adjust.basic");
    assertThat(RecipeEdits.geometryEnd(session.recipe())).isEqualTo(2);
    assertThat(RecipeEdits.move(session.recipe(), 2, 0)).isEqualTo(session.recipe());
  }
}
