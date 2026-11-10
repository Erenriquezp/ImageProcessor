package io.github.erenriquezp.rora.app.session.ai;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.ai.api.AiEngine;
import io.github.erenriquezp.rora.ai.api.ModelInfo;
import io.github.erenriquezp.rora.ai.api.ModelSource;
import io.github.erenriquezp.rora.ai.api.TileRunner;
import io.github.erenriquezp.rora.ai.store.ArtifactStore;
import io.github.erenriquezp.rora.app.session.ManualExecutor;
import io.github.erenriquezp.rora.app.session.ManualScheduler;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.CancelledException;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure.Reason;
import io.github.erenriquezp.rora.core.op.RenderKey;
import io.github.erenriquezp.rora.core.op.RenderLevel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiJobServiceTest {

  private static final ModelInfo INFO = new ModelInfo("fake", 4, 8, 2, "abc", 1, "", "", "");

  private final ManualExecutor worker = new ManualExecutor();
  private final ManualExecutor publisher = new ManualExecutor();
  private final ManualScheduler clock = new ManualScheduler();
  private final List<AiJobService.Progress> progress = new ArrayList<>();
  private final List<AiFailure> failures = new ArrayList<>();
  private final double[] saved = {-1};
  private int opened;
  private int closed;
  private int runs;
  private long tileNanos = TimeUnit.MILLISECONDS.toNanos(500);
  private long freeHeap = Long.MAX_VALUE;
  private long freePhysical = Long.MAX_VALUE;
  private Runnable duringRun = () -> {};
  private AiFailure openFailure;

  @TempDir Path root;

  private AiJobService service;

  private final AiEngine engine =
      (model, threads) -> {
        if (openFailure != null) {
          throw openFailure;
        }
        opened++;
        return new TileRunner() {
          @Override
          public float[] run(float[] chw, int h, int w, CancellationToken t) {
            t.throwIfCancelled();
            runs++;
            clock.advance(tileNanos);
            duringRun.run();
            return new float[3 * h * 4 * w * 4];
          }

          @Override
          public void close() {
            closed++;
          }
        };
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

  @BeforeEach
  void setUp() {
    service =
        new AiJobService(
            engine,
            MODEL,
            () -> ArtifactStore.open(root, 1 << 24, 1 << 20),
            worker,
            publisher,
            clock,
            new AiJobService.Timing() {
              @Override
              public OptionalDouble load() {
                return OptionalDouble.empty();
              }

              @Override
              public void save(double s) {
                saved[0] = s;
              }
            },
            new AiLimits.Memory() {
              @Override
              public long freeHeapBytes() {
                return freeHeap;
              }

              @Override
              public long freePhysicalBytes() {
                return freePhysical;
              }
            },
            2,
            null);
    service.addListener(
        new AiJobService.Listener() {
          @Override
          public void progress(AiJobService.Progress p) {
            progress.add(p);
          }

          @Override
          public void failed(AiFailure f) {
            failures.add(f);
          }
        });
  }

  @AfterEach
  void tearDown() {
    service.close();
    worker.runAll();
  }

  private static ArtifactKey key(int n) {
    return new ArtifactKey(RenderKey.root(n, RenderLevel.FULL), "abc", 2, ArtifactKey.CPU);
  }

  private static Img opaque(int w, int h) {
    int[] p = new int[w * h];
    java.util.Arrays.fill(p, 0xFF102030);
    return Img.adopt(w, h, p);
  }

  private static AiJobService.Input input(Img img) {
    return t -> img;
  }

  private void drain() {
    while (worker.runAll() + publisher.runAll() > 0) {
      // Hasta que no quede trabajo.
    }
  }

  @Test
  void nothingRunsUntilAskedAndTheStateFollowsTheKey() {
    assertThat(service.state(key(1))).isEqualTo(AiJobService.State.NOT_RUN);
    assertThat(opened).isZero();

    assertThat(service.start(key(1), true, input(opaque(16, 16)))).isTrue();
    assertThat(service.state(key(1))).isEqualTo(AiJobService.State.RUNNING);
    drain();

    assertThat(service.state(key(1))).isEqualTo(AiJobService.State.READY);
    assertThat(service.artifactFor(key(1))).isPositive();
    assertThat(opened).isEqualTo(1);
    assertThat(service.state(key(2))).isEqualTo(AiJobService.State.STALE);
  }

  @Test
  void onlyOneJobRunsAtATimeAndAFinishedKeyIsNotRepeated() {
    assertThat(service.start(key(1), true, input(opaque(16, 16)))).isTrue();
    assertThat(service.start(key(2), true, input(opaque(16, 16)))).isFalse();
    drain();
    int before = runs;
    assertThat(service.start(key(1), true, input(opaque(16, 16)))).isFalse();
    drain();
    assertThat(runs).isEqualTo(before);
  }

  @Test
  void undoingAGeometryChangeRecoversTheResult() {
    service.start(key(1), true, input(opaque(16, 16)));
    drain();
    int id = service.artifactFor(key(1));
    service.keyChanged(java.util.Optional.of(key(2)));
    assertThat(service.artifactFor(key(2))).isZero();
    service.keyChanged(java.util.Optional.of(key(1)));
    assertThat(service.artifactFor(key(1))).isEqualTo(id);
    assertThat(service.full(id)).isPresent();
  }

  @Test
  void progressIsMonotonicAndEtaUsesMeasuredTiles() {
    Img img = opaque(20, 8);
    service.start(key(1), true, input(img));
    drain();
    assertThat(progress).isNotEmpty();
    for (int i = 0; i < progress.size(); i++) {
      assertThat(progress.get(i).done()).isEqualTo(i + 1);
    }
    assertThat(progress.get(0).etaSeconds()).isEmpty();
    AiJobService.Progress second = progress.get(1);
    int remaining = second.total() - second.done();
    assertThat(second.etaSeconds().orElseThrow()).isEqualTo(0.5 * remaining);
  }

  @Test
  void theMeasuredTimeReplacesTheDefaultEstimate() {
    assertThat(service.estimateSeconds(16, 16))
        .isEqualTo(service.tiles(16, 16) * AiLimits.DEFAULT_SECONDS_PER_TILE);
    service.start(key(1), true, input(opaque(20, 8)));
    drain();
    assertThat(saved[0]).isEqualTo(0.5);
    assertThat(service.estimateSeconds(16, 16)).isEqualTo(service.tiles(16, 16) * 0.5);
  }

  @Test
  void cancellingLeavesNoResultAndAllowsANewRun() {
    service.start(key(1), true, input(opaque(20, 8)));
    duringRun = service::cancel;
    drain();
    assertThat(service.isRunning()).isFalse();
    assertThat(service.state(key(1))).isEqualTo(AiJobService.State.NOT_RUN);
    assertThat(failures).isEmpty();

    duringRun = () -> {};
    assertThat(service.start(key(1), true, input(opaque(20, 8)))).isTrue();
    drain();
    assertThat(service.state(key(1))).isEqualTo(AiJobService.State.READY);
  }

  @Test
  void aChangedKeyCancelsTheRunningJob() {
    service.start(key(1), true, input(opaque(20, 8)));
    service.keyChanged(java.util.Optional.of(key(2)));
    drain();
    assertThat(service.artifactFor(key(1))).isZero();
    assertThat(runs).isZero();
  }

  @Test
  void anEngineFailureIsReportedAndRecoverable() {
    openFailure = new AiFailure(Reason.ENGINE_UNAVAILABLE, "x", null);
    service.start(key(1), true, input(opaque(16, 16)));
    drain();
    assertThat(failures).extracting(AiFailure::reason).containsExactly(Reason.ENGINE_UNAVAILABLE);
    assertThat(service.isRunning()).isFalse();

    openFailure = null;
    assertThat(service.start(key(1), true, input(opaque(16, 16)))).isTrue();
    drain();
    assertThat(service.state(key(1))).isEqualTo(AiJobService.State.READY);
  }

  @Test
  void runningOutOfHeapBeforeInferenceFailsCleanly() {
    freeHeap = 16;
    service.start(key(1), true, input(opaque(16, 16)));
    drain();
    assertThat(failures).extracting(AiFailure::reason).containsExactly(Reason.OUT_OF_MEMORY);
    assertThat(runs).isZero();
  }

  @Test
  void aCancelledInputIsNotAFailure() {
    service.start(
        key(1),
        true,
        t -> {
          throw new CancelledException();
        });
    drain();
    assertThat(failures).isEmpty();
    assertThat(service.isRunning()).isFalse();
  }

  @Test
  void theSessionIsReleasedAfterTwoIdleMinutesAndReopensOnDemand() {
    service.start(key(1), true, input(opaque(16, 16)));
    drain();
    assertThat(closed).isZero();

    clock.advance(TimeUnit.SECONDS.toNanos(119));
    drain();
    assertThat(closed).isZero();

    clock.advance(TimeUnit.SECONDS.toNanos(1));
    drain();
    assertThat(closed).isEqualTo(1);

    service.start(key(2), true, input(opaque(16, 16)));
    drain();
    assertThat(opened).isEqualTo(2);
  }

  @Test
  void aNewJobCancelsTheIdleRelease() {
    service.start(key(1), true, input(opaque(16, 16)));
    drain();
    service.start(key(2), true, input(opaque(16, 16)));
    clock.advance(TimeUnit.SECONDS.toNanos(121));
    drain();
    assertThat(opened).isEqualTo(1);
  }

  @Test
  void closingTheImageForgetsEveryResult() {
    service.start(key(1), true, input(opaque(16, 16)));
    drain();
    int id = service.artifactFor(key(1));
    service.clear();
    drain();
    assertThat(service.artifactFor(key(1))).isZero();
    assertThat(service.state(key(1))).isEqualTo(AiJobService.State.NOT_RUN);
    assertThat(service.full(id)).isEmpty();
  }

  @Test
  void aResultArrivingAfterTheImageClosedIsDiscarded() {
    service.start(key(1), true, input(opaque(16, 16)));
    worker.runAll();
    service.clear();
    drain();
    assertThat(service.artifactFor(key(1))).isZero();
    assertThat(service.state(key(1))).isEqualTo(AiJobService.State.NOT_RUN);
  }

  @Test
  void closingRemovesTheSessionFolder() throws Exception {
    service.start(key(1), true, input(opaque(16, 16)));
    drain();
    service.close();
    drain();
    assertThat(closed).isEqualTo(1);
    try (var s = java.nio.file.Files.list(root)) {
      assertThat(s).isEmpty();
    }
  }

  @Test
  void limitsFollowTheOutputSizeAndMemory() {
    AiLimits.Memory plenty = memory(Long.MAX_VALUE, Long.MAX_VALUE);
    assertThat(AiLimits.check(2000, 2000, 4, true, false, plenty)).isEqualTo(AiLimits.Verdict.OK);
    assertThat(AiLimits.check(2001, 2000, 4, true, false, plenty))
        .isEqualTo(AiLimits.Verdict.OUTPUT_TOO_LARGE);
    assertThat(AiLimits.check(4000, 4000, 2, true, false, plenty)).isEqualTo(AiLimits.Verdict.OK);
    long need = AiLimits.heapBytes(100, 100, 2, true, false);
    assertThat(AiLimits.check(100, 100, 2, true, false, memory(need, Long.MAX_VALUE)))
        .isEqualTo(AiLimits.Verdict.OK);
    assertThat(AiLimits.check(100, 100, 2, true, false, memory(need - 1, Long.MAX_VALUE)))
        .isEqualTo(AiLimits.Verdict.NOT_ENOUGH_HEAP);
    assertThat(AiLimits.check(100, 100, 2, false, false, memory(need, Long.MAX_VALUE)))
        .isEqualTo(AiLimits.Verdict.NOT_ENOUGH_HEAP);
    assertThat(AiLimits.check(100, 100, 2, true, false, memory(need, 1)))
        .isEqualTo(AiLimits.Verdict.NOT_ENOUGH_PHYSICAL);
  }

  private static AiLimits.Memory memory(long heap, long physical) {
    return new AiLimits.Memory() {
      @Override
      public long freeHeapBytes() {
        return heap;
      }

      @Override
      public long freePhysicalBytes() {
        return physical;
      }
    };
  }

  @Test
  void bandsMatchTheAgreedThresholds() {
    assertThat(AiLimits.band(19.9)).isEqualTo(AiLimits.Band.SHORT);
    assertThat(AiLimits.band(20)).isEqualTo(AiLimits.Band.NORMAL);
    assertThat(AiLimits.band(60)).isEqualTo(AiLimits.Band.NORMAL);
    assertThat(AiLimits.band(61)).isEqualTo(AiLimits.Band.SLOW);
    assertThat(AiLimits.band(120)).isEqualTo(AiLimits.Band.SLOW);
    assertThat(AiLimits.band(121)).isEqualTo(AiLimits.Band.CONFIRM);
  }
}
