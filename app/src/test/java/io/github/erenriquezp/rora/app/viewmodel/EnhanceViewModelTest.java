package io.github.erenriquezp.rora.app.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.ai.api.AiEngine;
import io.github.erenriquezp.rora.ai.api.ModelInfo;
import io.github.erenriquezp.rora.ai.api.ModelSource;
import io.github.erenriquezp.rora.ai.api.TileRunner;
import io.github.erenriquezp.rora.ai.store.ArtifactStore;
import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.model.RecipeEdits;
import io.github.erenriquezp.rora.app.session.EditSession;
import io.github.erenriquezp.rora.app.session.ExportTarget.Collision;
import io.github.erenriquezp.rora.app.session.ManualExecutor;
import io.github.erenriquezp.rora.app.session.ManualScheduler;
import io.github.erenriquezp.rora.app.session.RenderCoordinator;
import io.github.erenriquezp.rora.app.session.RenderLane;
import io.github.erenriquezp.rora.app.session.ai.AiJobService;
import io.github.erenriquezp.rora.app.session.ai.AiLimits;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy;
import io.github.erenriquezp.rora.core.io.ExportSettings;
import io.github.erenriquezp.rora.core.io.ExportSettings.MetadataPolicy;
import io.github.erenriquezp.rora.core.io.FormatId;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;
import io.github.erenriquezp.rora.core.io.ProvenanceXmp;
import io.github.erenriquezp.rora.core.op.OpSpec;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** El panel Mejorar sobre una sesión real, con un motor simulado que pinta de blanco. */
class EnhanceViewModelTest {

  private static final ModelInfo INFO = new ModelInfo("fake", 4, 16, 4, "sha", 1, "", "", "1");

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

  @TempDir Path dir;

  private final ManualScheduler time = new ManualScheduler();
  private final ManualExecutor preview = new ManualExecutor();
  private final ManualExecutor full = new ManualExecutor();
  private final ManualExecutor aux = new ManualExecutor();
  private final ManualExecutor publisher = new ManualExecutor();
  private final ManualExecutor background = new ManualExecutor();
  private final ManualExecutor io = new ManualExecutor();
  private final ManualExecutor aiWorker = new ManualExecutor();

  private int engineRuns;
  private boolean failing;
  private long freeHeap = Long.MAX_VALUE;
  private OptionalDouble secondsPerTile = OptionalDouble.empty();

  private EditSession<Img> session;
  private AiJobService jobs;
  private StudioViewModel<Img> vm;
  private EnhanceViewModel<Img> enhance;

  private final AiEngine engine =
      (model, threads) ->
          new TileRunner() {
            @Override
            public float[] run(float[] chw, int h, int w, CancellationToken t) throws AiFailure {
              if (failing) {
                throw new AiFailure(AiFailure.Reason.FAILURE, "simulado", null);
              }
              engineRuns++;
              float[] out = new float[3 * h * 4 * w * 4];
              Arrays.fill(out, 1f);
              return out;
            }

            @Override
            public void close() {}
          };

  private static long ms(long m) {
    return Duration.ofMillis(m).toNanos();
  }

  @BeforeEach
  void setUp() throws Exception {
    secondsPerTile = OptionalDouble.empty();
    build();
  }

  private void build() throws Exception {
    jobs =
        new AiJobService(
            engine,
            MODEL,
            () -> ArtifactStore.open(dir.resolve("cache"), 1 << 24, 1 << 22),
            aiWorker,
            publisher,
            time,
            new AiJobService.Timing() {
              @Override
              public OptionalDouble load() {
                return secondsPerTile;
              }

              @Override
              public void save(double s) {}
            },
            new AiLimits.Memory() {
              @Override
              public long freeHeapBytes() {
                return freeHeap;
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
    vm =
        new StudioViewModel<>(
            new Env<>(session, Messages.of(Locale.of("es")), time, publisher, background, io),
            img -> img,
            img -> img);
    enhance = vm.enhance();
    vm.viewer().setViewport(800, 600, 1);
    vm.open(png());
    drain();
  }

  @AfterEach
  void tearDown() {
    vm.close();
    session.close();
    aiWorker.runAll();
  }

  private File png() throws Exception {
    File f = dir.resolve("foto.png").toFile();
    ImageIO.write(new BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB), "png", f);
    return f;
  }

  private int step() {
    return preview.runAll()
        + full.runAll()
        + aux.runAll()
        + aiWorker.runAll()
        + publisher.runAll()
        + background.runAll()
        + io.runAll();
  }

  private void drain() {
    for (int i = 0; i < 2000; i++) {
      if (step() == 0) {
        time.advance(ms(200));
        if (step() == 0 && session.isIdle() && !jobs.isRunning()) {
          return;
        }
      }
    }
    throw new AssertionError("no converge");
  }

  private List<String> stepIds() {
    return session.recipe().steps().stream().map(OpSpec::opId).toList();
  }

  private AiJobService.State state() {
    return enhance.stateProperty().get();
  }

  private StackViewModel.Row enhanceRow() {
    return vm.stack().rows().stream()
        .filter(r -> r.title().equals("Mejorar"))
        .findFirst()
        .orElseThrow();
  }

  private void exportPng(MetadataPolicy policy) {
    vm.startExport(
        ExportSettings.defaults(FormatId.PNG).withMetadata(policy),
        dir,
        "salida.png",
        Collision.NUMBER);
  }

  private String exported() throws Exception {
    return new String(Files.readAllBytes(dir.resolve("salida.png")), StandardCharsets.ISO_8859_1);
  }

  private void choose(int index) {
    vm.promptProperty().get().choices().get(index).action().run();
    drain();
  }

  // ── El motor solo arranca a petición ──────────────────────────────────────

  @Test
  void openingThePanelChangesNothing() {
    vm.toolProperty().set(StudioViewModel.Tool.ENHANCE);
    drain();
    assertThat(engineRuns).isZero();
    assertThat(stepIds()).isEmpty();
    assertThat(enhance.canStartProperty().get()).isTrue();
  }

  @Test
  void theControlsNeverRunTheEngine() {
    enhance.setScale(EnhanceViewModel.X4);
    enhance.intensity().userSet(40.0);
    enhance.intensity().endGesture();
    drain();
    assertThat(stepIds()).isEmpty();
    assertThat(engineRuns).isZero();
    assertThat(enhance.dimensionsProperty().get()).isEqualTo("64×32 → 256×128 (0,0 MP)");
  }

  @Test
  void improvingAddsTheStepWithTheChosenValuesAndRunsOnce() {
    enhance.setScale(EnhanceViewModel.X4);
    enhance.intensity().userSet(40.0);
    enhance.intensity().endGesture();
    enhance.start();
    assertThat(enhance.runningProperty().get()).isTrue();
    assertThat(enhanceRow().badge()).isEqualTo("Mejorando");
    drain();
    assertThat(engineRuns).isPositive();
    assertThat(stepIds()).containsExactly(RecipeEdits.ENHANCE);
    OpSpec s = session.recipe().steps().get(0);
    assertThat(s.params().getEnum("scale")).isEqualTo("X4");
    assertThat(s.params().getDouble("intensity")).isEqualTo(40.0);
    assertThat(state()).isEqualTo(AiJobService.State.READY);
    assertThat(enhanceRow().badge()).isEqualTo("Listo");
    assertThat(enhanceRow().badgeHint()).isEmpty();
    assertThat(enhance.canStartProperty().get()).isFalse();
  }

  @Test
  void theViewIsRedrawnWhenTheResultArrives() {
    int[] seen = new int[1];
    session.addListener(
        new EditSession.Listener<>() {
          @Override
          public void frameReady(EditSession.Frame<Img> f) {
            seen[0] = f.pixels().argb(3, 3) & 0xFFFFFF;
          }
        });
    enhance.start();
    drain();
    assertThat(seen[0]).isEqualTo(0xFFFFFF);
  }

  @Test
  void movingTheIntensityAfterwardsDoesNotRunTheEngineAgain() {
    enhance.start();
    drain();
    int runs = engineRuns;
    enhance.intensity().userSet(50.0);
    enhance.intensity().endGesture();
    drain();
    assertThat(engineRuns).isEqualTo(runs);
    assertThat(state()).isEqualTo(AiJobService.State.READY);
  }

  @Test
  void changingTheScaleOfAnExistingStepLeavesItWithoutAResult() {
    enhance.start();
    drain();
    int runs = engineRuns;
    enhance.setScale(EnhanceViewModel.X4);
    drain();
    assertThat(engineRuns).isEqualTo(runs);
    assertThat(session.recipe().steps().get(0).params().getEnum("scale")).isEqualTo("X4");
    assertThat(state()).isNotEqualTo(AiJobService.State.READY);
  }

  @Test
  void cancellingLeavesTheStepWithTheClassicUpscale() {
    enhance.start();
    enhance.cancel();
    drain();
    assertThat(engineRuns).isZero();
    assertThat(state()).isEqualTo(AiJobService.State.NOT_RUN);
    assertThat(enhanceRow().badge()).isEqualTo("Sin ejecutar");
    assertThat(enhanceRow().badgeHint()).isEqualTo("Vista con ampliación clásica");
    assertThat(enhance.canStartProperty().get()).isTrue();
  }

  @Test
  void aGeometryChangeMarksItStaleAndOffersToRepeat() {
    enhance.start();
    drain();
    vm.crop().rotate(true);
    drain();
    assertThat(state()).isEqualTo(AiJobService.State.STALE);
    assertThat(enhanceRow().badge()).isEqualTo("Obsoleto");
    assertThat(enhance.noticeProperty().get()).contains("ampliación clásica");
    assertThat(enhance.actionProperty().get()).isEqualTo("Mejorar de nuevo");
  }

  @Test
  void theStepIsNotMovableAndOpensThePanel() {
    enhance.start();
    drain();
    assertThat(enhanceRow().movable()).isFalse();
    vm.stack().select(enhanceRow().index());
    assertThat(vm.toolProperty().get()).isEqualTo(StudioViewModel.Tool.ENHANCE);
    assertThat(vm.stack().selectedProperty().get()).isEqualTo(-1);
  }

  @Test
  void anotherImageStartsFromTheDefaults() throws Exception {
    enhance.setScale(EnhanceViewModel.X4);
    enhance.intensity().userSet(40.0);
    enhance.intensity().endGesture();
    vm.open(png());
    drain();
    assertThat(enhance.scaleProperty().get()).isEqualTo(EnhanceViewModel.X2);
    assertThat(enhance.intensity().value()).isEqualTo(100.0);
  }

  // ── Límites y estimación ──────────────────────────────────────────────────

  @Test
  void withoutMemoryForX4OnlyX2IsOffered() {
    freeHeap = AiLimits.heapBytes(64, 32, 2, true, false);
    enhance.setScale(EnhanceViewModel.X2);
    enhance.refresh();
    assertThat(enhance.x4AllowedProperty().get()).isFalse();
    assertThat(enhance.limitProperty().get()).startsWith("×4 no disponible");
    assertThat(enhance.canStartProperty().get()).isTrue();
    enhance.setScale(EnhanceViewModel.X4);
    assertThat(enhance.canStartProperty().get()).isFalse();
    enhance.start();
    drain();
    assertThat(engineRuns).isZero();
    assertThat(stepIds()).isEmpty();
  }

  @Test
  void aLongEstimateAsksBeforeRunning() throws Exception {
    tearDown();
    secondsPerTile = OptionalDouble.of(1000);
    build();
    enhance.start();
    assertThat(jobs.isRunning()).isFalse();
    assertThat(vm.promptProperty().get().body()).contains("min");
    choose(1);
    assertThat(jobs.isRunning()).isFalse();
    assertThat(engineRuns).isZero();
    enhance.start();
    choose(0);
    assertThat(engineRuns).isPositive();
  }

  @Test
  void aShortEstimateRunsWithoutAsking() {
    enhance.start();
    assertThat(vm.promptProperty().get()).isNull();
    assertThat(jobs.isRunning()).isTrue();
  }

  @Test
  void theEstimateIsShownInSeconds() {
    assertThat(enhance.estimateProperty().get())
        .startsWith("Tiempo estimado: unos ")
        .endsWith(" s");
  }

  @Test
  void progressShowsTheTilesDone() {
    enhance.progress(new AiJobService.Progress(3, 8, OptionalDouble.empty()));
    assertThat(enhance.progressProperty().get()).isEqualTo(3.0 / 8);
    assertThat(enhance.progressTextProperty().get()).isEqualTo("3/8");
  }

  @Test
  void aFailureIsReportedAndTheClassicUpscaleRemains() {
    failing = true;
    enhance.start();
    drain();
    assertThat(vm.notifications().notices()).isNotEmpty();
    assertThat(vm.notifications().notices().get(0).toString())
        .contains("La mejora con IA ha fallado");
    assertThat(state()).isEqualTo(AiJobService.State.NOT_RUN);
    assertThat(session.outputSize().width()).isEqualTo(128);
  }

  // ── Exportación ───────────────────────────────────────────────────────────

  private void notReady() {
    enhance.start();
    enhance.cancel();
    drain();
    assertThat(state()).isEqualTo(AiJobService.State.NOT_RUN);
  }

  @Test
  void exportingWithoutAResultAsksAndOffersThreeChoices() {
    notReady();
    exportPng(MetadataPolicy.REMOVE_LOCATION);
    drain();
    Prompt p = vm.promptProperty().get();
    assertThat(p.choices()).hasSize(3);
    assertThat(p.choices().get(0).label()).startsWith("Mejorar y exportar (unos ");
    assertThat(p.choices().get(1).label()).isEqualTo("Exportar con ampliación clásica");
    assertThat(p.choices().get(2).label()).isEqualTo("Cancelar");
    assertThat(engineRuns).isZero();
    assertThat(dir.resolve("salida.png")).doesNotExist();
  }

  @Test
  void theClassicChoiceExportsWithoutTheEngineOrAMark() throws Exception {
    notReady();
    exportPng(MetadataPolicy.REMOVE_LOCATION);
    drain();
    choose(1);
    assertThat(engineRuns).isZero();
    assertThat(exported()).doesNotContain(ProvenanceXmp.SOURCE_TYPE);
  }

  @Test
  void cancellingTheQuestionExportsNothing() {
    notReady();
    exportPng(MetadataPolicy.REMOVE_LOCATION);
    drain();
    choose(2);
    assertThat(engineRuns).isZero();
    assertThat(dir.resolve("salida.png")).doesNotExist();
  }

  @Test
  void improvingFromTheQuestionExportsTheResultMarked() throws Exception {
    notReady();
    exportPng(MetadataPolicy.REMOVE_LOCATION);
    drain();
    choose(0);
    assertThat(engineRuns).isPositive();
    assertThat(exported()).contains(ProvenanceXmp.SOURCE_TYPE).contains("rora:Model=\"fake\"");
  }

  @Test
  void aJobThatDoesNotFinishCancelsTheExport() {
    notReady();
    exportPng(MetadataPolicy.REMOVE_LOCATION);
    drain();
    vm.promptProperty().get().choices().get(0).action().run();
    enhance.cancel();
    drain();
    assertThat(dir.resolve("salida.png")).doesNotExist();
    assertThat(vm.notifications().notices()).isNotEmpty();
  }

  @Test
  void aReadyResultExportsWithoutAskingAndCarriesTheMark() throws Exception {
    enhance.start();
    drain();
    exportPng(MetadataPolicy.REMOVE_LOCATION);
    drain();
    assertThat(vm.promptProperty().get()).isNull();
    assertThat(exported()).contains(ProvenanceXmp.SOURCE_TYPE);
  }

  @Test
  void removeAllLeavesNoMark() throws Exception {
    enhance.start();
    drain();
    exportPng(MetadataPolicy.REMOVE_ALL);
    drain();
    assertThat(exported()).doesNotContain(ProvenanceXmp.SOURCE_TYPE).doesNotContain("fake");
  }

  @Test
  void theUserCanTurnTheMarkOff() throws Exception {
    enhance.start();
    drain();
    vm.exportDialog().provenanceProperty().set(false);
    exportPng(MetadataPolicy.REMOVE_LOCATION);
    drain();
    assertThat(exported()).doesNotContain(ProvenanceXmp.SOURCE_TYPE);
  }

  @Test
  void zeroIntensityIsNotDeclaredAsAi() throws Exception {
    enhance.start();
    drain();
    enhance.intensity().userSet(0.0);
    enhance.intensity().endGesture();
    drain();
    exportPng(MetadataPolicy.REMOVE_LOCATION);
    drain();
    assertThat(exported()).doesNotContain(ProvenanceXmp.SOURCE_TYPE);
  }

  @Test
  void theCheckboxOnlyAppearsWithAnActiveStep() {
    vm.showExport();
    assertThat(vm.exportDialog().provenanceShownProperty().get()).isFalse();
    vm.exportDialog().close();
    enhance.start();
    drain();
    vm.showExport();
    assertThat(vm.exportDialog().provenanceShownProperty().get()).isTrue();
    assertThat(vm.exportDialog().provenanceProperty().get()).isTrue();
  }
}
