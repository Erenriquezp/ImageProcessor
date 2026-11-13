package io.github.erenriquezp.rora.app.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.model.CropModel;
import io.github.erenriquezp.rora.app.model.FrameOverlayState;
import io.github.erenriquezp.rora.app.session.EditSession;
import io.github.erenriquezp.rora.app.session.ManualExecutor;
import io.github.erenriquezp.rora.app.session.ManualScheduler;
import io.github.erenriquezp.rora.app.session.RenderCoordinator;
import io.github.erenriquezp.rora.app.session.RenderLane;
import io.github.erenriquezp.rora.app.session.wallpaper.WallpaperWorkspace;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy;
import io.github.erenriquezp.rora.wallpapers.plan.PlannedTarget;
import io.github.erenriquezp.rora.wallpapers.plan.Target.FitMode;
import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset;
import io.github.erenriquezp.rora.wallpapers.preset.UserPresetStore;
import io.github.erenriquezp.rora.wallpapers.render.FillSpec;
import io.github.erenriquezp.rora.wallpapers.subject.Subject;
import io.github.erenriquezp.rora.wallpapers.subject.SubjectDetector;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** La herramienta Wallpapers sobre una sesión real, con hilos y tiempo manuales y sin toolkit. */
class WallpapersViewModelTest {

  @TempDir Path dir;

  private ManualScheduler time;
  private ManualExecutor preview;
  private ManualExecutor full;
  private ManualExecutor aux;
  private ManualExecutor publisher;
  private ManualExecutor background;
  private ManualExecutor io;
  private EditSession<Img> session;
  private StudioViewModel<Img> studio;
  private WallpapersViewModel<Img> vm;
  private int detections;
  private List<Subject> faces = List.of();
  private boolean detectorBroken;
  private final Map<String, String> prefs = new HashMap<>();

  private static long ms(long m) {
    return Duration.ofMillis(m).toNanos();
  }

  @BeforeEach
  void setUp() throws Exception {
    time = new ManualScheduler();
    preview = new ManualExecutor();
    full = new ManualExecutor();
    aux = new ManualExecutor();
    publisher = new ManualExecutor();
    background = new ManualExecutor();
    io = new ManualExecutor();
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
    Env<Img> env =
        new Env<>(session, Messages.of(Locale.of("es")), time, publisher, background, io);
    SubjectDetector fake =
        new SubjectDetector() {
          @Override
          public String id() {
            return "falso";
          }

          @Override
          public List<Subject> detect(Img image, CancellationToken token) {
            detections++;
            if (detectorBroken) {
              throw new IllegalStateException("sin motor");
            }
            return faces;
          }

          @Override
          public void close() {}
        };
    studio =
        new StudioViewModel<>(
            env,
            img -> img,
            img -> img,
            img -> img,
            new WallpaperWorkspace(() -> fake, TileExecutor.sequential()),
            new UserPresetStore(dir.resolve("presets")));
    vm = studio.wallpapers();
    vm.usePrefs(
        new ExportPrefs() {
          @Override
          public String get(String key, String fallback) {
            return prefs.getOrDefault(key, fallback);
          }

          @Override
          public void put(String key, String value) {
            prefs.put(key, value);
          }
        });
    studio.viewer().setViewport(800, 600, 1);
    studio.open(png("foto.png", 400, 300));
    drain();
  }

  @AfterEach
  void tearDown() {
    studio.close();
    session.close();
  }

  private File png(String name, int w, int h) throws Exception {
    BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        bi.setRGB(x, y, (x % 256) << 16 | (y % 256) << 8 | 64);
      }
    }
    File f = dir.resolve(name).toFile();
    ImageIO.write(bi, "png", f);
    return f;
  }

  private void drain() {
    for (int i = 0; i < 4000; i++) {
      int n =
          preview.runAll()
              + full.runAll()
              + aux.runAll()
              + publisher.runAll()
              + background.runAll()
              + io.runAll();
      if (n == 0) {
        if (session.isIdle() && time.pendingTasks() == 0) {
          return;
        }
        time.advance(ms(100));
      }
    }
    throw new AssertionError("no converge");
  }

  private void open() {
    studio.toolProperty().set(StudioViewModel.Tool.WALLPAPERS);
    drain();
  }

  private List<String> ids() {
    return vm.planProperty().get().targets().stream().map(t -> t.target().id()).toList();
  }

  private DevicePreset preset(String id) {
    return vm.rows().stream()
        .filter(r -> !r.header() && r.preset().id().equals(id))
        .findFirst()
        .orElseThrow()
        .preset();
  }

  private PlannedTarget current() {
    return vm.planProperty().get().targets().stream()
        .filter(t -> t.target().id().equals(vm.currentIdProperty().get()))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void openingTheToolSelectsTheDefaultsAndPlansThem() {
    open();
    assertThat(ids()).containsExactlyElementsOf(WallpapersViewModel.DEFAULT_TARGETS);
    assertThat(vm.exportLabelProperty().get()).isEqualTo("Exportar 5 wallpapers");
    assertThat(vm.canExportProperty().get()).isTrue();
    assertThat(vm.planProperty().get().sourceWidth()).isEqualTo(400);
    assertThat(vm.thumbs()).hasSize(5);
    assertThat(vm.thumbs().getFirst().current()).isTrue();
    assertThat(vm.overlayProperty().get()).isNotNull();
  }

  @Test
  void myScreensComeFirst() {
    vm.setScreens(List.of(new WallpapersViewModel.Screen(2560, 1440)));
    open();
    assertThat(ids()).first().isEqualTo("screen-1");
    assertThat(ids()).hasSize(6);
    assertThat(vm.rows().getFirst().label()).isEqualTo("Mi pantalla");
  }

  @Test
  void theAnalysisRunsOncePerRecipeAndNeverEnhances() {
    open();
    assertThat(detections).isEqualTo(1);
    assertThat(vm.analysisProperty().get()).isEqualTo(WallpapersViewModel.AnalysisState.READY);
    studio.toolProperty().set(StudioViewModel.Tool.ADJUST);
    open();
    assertThat(detections).as("misma receta: caché").isEqualTo(1);
    studio.crop().rotate(true);
    drain();
    assertThat(detections).isEqualTo(2);
    assertThat(session.ai()).isEmpty();
  }

  @Test
  void facesMoveTheAutomaticFrame() {
    faces = List.of(new Subject(0.05, 0.05, 0.1, 0.12, 0.9, Subject.Kind.FACE, Optional.empty()));
    open();
    vm.select("phone-1179-2556");
    assertThat(current().frame().x()).as("encuadre desplazado hacia la cara").isLessThan(20);
  }

  @Test
  void withoutDetectionNothingIsAnalyzed() {
    vm.detectFacesProperty().set(false);
    open();
    assertThat(detections).isZero();
    assertThat(vm.analysisProperty().get()).isEqualTo(WallpapersViewModel.AnalysisState.READY);
    assertThat(prefs).containsEntry(WallpapersViewModel.KEY_DETECT, "false");
  }

  @Test
  void aBrokenDetectorFallsBackToCentred() {
    detectorBroken = true;
    open();
    assertThat(vm.analysisProperty().get())
        .isEqualTo(WallpapersViewModel.AnalysisState.NO_DETECTOR);
    assertThat(vm.planProperty().get()).isNotNull();
  }

  @Test
  void draggingMakesAManualFrameAndAutoUndoesIt() {
    open();
    vm.select("pc-4k");
    FrameOverlayState o = vm.overlayProperty().get();
    assertThat(o.editable()).isTrue();
    CropModel moved = o.frame().movedBy(0, -0.05);
    vm.setFrame(moved);
    assertThat(current().target().manualFrame()).isPresent();
    assertThat(current().frame().y())
        .isCloseTo(moved.top() * 300, org.assertj.core.data.Offset.offset(1e-6));

    vm.auto();
    assertThat(current().target().manualFrame()).isEmpty();
  }

  @Test
  void changingThePhotoProportionResetsManualFrames() {
    open();
    vm.select("pc-4k");
    vm.anchor(0, 0);
    assertThat(current().target().manualFrame()).isPresent();
    List<String> shown = new java.util.ArrayList<>();
    studio
        .notifications()
        .notices()
        .addListener(
            (javafx.collections.ListChangeListener<NotificationsViewModel.Notice>)
                c -> {
                  while (c.next()) {
                    c.getAddedSubList().forEach(n -> shown.add(n.message()));
                  }
                });
    studio.crop().rotate(true);
    drain();
    assertThat(current().target().manualFrame()).isEmpty();
    assertThat(shown).anyMatch(msg -> msg.contains("volvieron a Auto"));
  }

  @Test
  void containIsNotEditableAndTheFillCanChange() {
    open();
    vm.select("phone-1179-2556");
    vm.fitProperty().set(FitMode.CONTAIN);
    assertThat(current().filled()).isTrue();
    assertThat(vm.overlayProperty().get().editable()).isFalse();
    vm.strengthProperty().set(0.2);
    vm.fillProperty().set(FillSpec.Kind.SOLID);
    assertThat(current().target().fill().kind()).isEqualTo(FillSpec.Kind.SOLID);
    assertThat(current().target().fill().strength()).isEqualTo(0.2);
    vm.select("pc-4k");
    assertThat(vm.fitProperty().get()).as("cada destino conserva lo suyo").isEqualTo(FitMode.AUTO);
    vm.select("phone-1179-2556");
    assertThat(vm.fillProperty().get()).isEqualTo(FillSpec.Kind.SOLID);
  }

  @Test
  void searchingAndTogglingDevices() {
    open();
    vm.queryProperty().set("tableta 2732");
    assertThat(vm.rows()).filteredOn(r -> !r.header()).hasSize(1);
    vm.toggle(preset("tablet-2048-2732"));
    vm.queryProperty().set("");
    vm.toggle(preset("pc-qhd"));
    assertThat(ids()).contains("tablet-2048-2732").doesNotContain("pc-qhd");
    assertThat(prefs.get(WallpapersViewModel.KEY_TARGETS)).contains("tablet-2048-2732");
  }

  @Test
  void rotatingSwapsTheSides() {
    open();
    vm.select("tablet-1668-2388");
    vm.rotateCurrent();
    assertThat(current().target().id()).isEqualTo("tablet-1668-2388@rot");
    assertThat(current().target().width()).isEqualTo(2388);
    assertThat(ids()).hasSize(5);
  }

  @Test
  void customSizesAreSavedAndSelected() throws Exception {
    open();
    assertThat(vm.addCustom("", 100, 100)).isPresent();
    assertThat(vm.addCustom("Mi marco", 8, 100)).isPresent();
    assertThat(vm.addCustom("Mi marco", 1024, 600)).isEmpty();
    drain();
    assertThat(ids()).contains("user:mi-marco");
    assertThat(Files.readString(dir.resolve("presets").resolve(UserPresetStore.FILE)))
        .contains("Mi marco");
    vm.deleteCustom(preset("user:mi-marco"));
    drain();
    assertThat(ids()).doesNotContain("user:mi-marco");
  }

  @Test
  void blurryTargetsGetNoAiSuggestionWithoutTheEngine() {
    open();
    assertThat(vm.planProperty().get().requiredEnhanceScale()).isPresent();
    assertThat(vm.suggestionProperty().get()).isEmpty();
  }

  @Test
  void exportWritesEveryTargetAndAsksOnceAboutCollisions() throws Exception {
    open();
    Path out = dir.resolve("salida");
    vm.folderProperty().set(out.toString());
    vm.showExport();
    assertThat(vm.dialogOpenProperty().get()).isTrue();
    assertThat(vm.namePreviewProperty().get()).startsWith("foto - wallpapers");
    vm.confirmExport();
    drain();

    assertThat(vm.reportProperty().get().summary()).startsWith("5 escritos");
    assertThat(files(out)).hasSize(5);
    assertThat(prefs.get(WallpapersViewModel.KEY_FOLDER)).isEqualTo(out.toString());

    vm.closeReport();
    vm.showExport();
    vm.confirmExport();
    drain();
    Prompt p = studio.promptProperty().get();
    assertThat(p).isNotNull();
    assertThat(p.body()).contains("5 archivos");
    p.choices().getFirst().action().run();
    drain();
    assertThat(files(out)).hasSize(10);
  }

  @Test
  void cancellingAnExportLeavesOnlyFinishedFiles() throws Exception {
    open();
    Path out = dir.resolve("salida");
    vm.folderProperty().set(out.toString());
    vm.showExport();
    vm.confirmExport();
    // El render FULL y el primer destino; luego se cancela.
    for (int i = 0; i < 50 && !vm.exportingProperty().get(); i++) {
      publisher.runAll();
      io.runAll();
    }
    vm.cancelExport();
    drain();
    assertThat(vm.reportProperty().get().summary()).contains("cancelados");
    try (Stream<Path> s = Files.walk(out)) {
      assertThat(s.filter(Files::isRegularFile).filter(f -> f.toString().endsWith(".tmp")))
          .isEmpty();
    } catch (java.nio.file.NoSuchFileException e) {
      // nada escrito: también vale
    }
  }

  private static List<Path> files(Path root) throws Exception {
    try (Stream<Path> s = Files.walk(root)) {
      return s.filter(Files::isRegularFile).toList();
    }
  }
}
