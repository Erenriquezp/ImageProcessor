package io.github.erenriquezp.rora.app.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.app.catalog.Catalog;
import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.model.CropModel;
import io.github.erenriquezp.rora.app.model.RecipeEdits;
import io.github.erenriquezp.rora.app.session.EditSession;
import io.github.erenriquezp.rora.app.session.ManualExecutor;
import io.github.erenriquezp.rora.app.session.ManualScheduler;
import io.github.erenriquezp.rora.app.session.RenderCoordinator;
import io.github.erenriquezp.rora.app.session.RenderLane;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy;
import io.github.erenriquezp.rora.core.op.OpSpec;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Los ViewModels sobre una sesión real, con hilos y tiempo manuales y sin toolkit. */
class StudioViewModelTest {

  @TempDir Path dir;

  private ManualScheduler time;
  private ManualExecutor preview;
  private ManualExecutor full;
  private ManualExecutor aux;
  private ManualExecutor publisher;
  private ManualExecutor background;
  private ManualExecutor io;
  private EditSession<Img> session;
  private StudioViewModel<Img> vm;

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
    vm = new StudioViewModel<>(env, img -> img, img -> img);
    vm.viewer().setViewport(800, 600, 1);
    vm.open(png("foto.png", 64, 32));
    drain();
  }

  @AfterEach
  void tearDown() {
    vm.close();
    session.close();
  }

  private File png(String name, int w, int h) throws Exception {
    BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        bi.setRGB(x, y, (x * 4) << 16 | (y * 8) << 8 | 64);
      }
    }
    File f = dir.resolve(name).toFile();
    ImageIO.write(bi, "png", f);
    return f;
  }

  private void drain() {
    for (int i = 0; i < 2000; i++) {
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

  private ControlState adjust(String key) {
    return vm.basic().groups().stream()
        .flatMap(g -> g.controls().stream())
        .filter(c -> c.spec().key().equals(key))
        .findFirst()
        .orElseThrow();
  }

  private Catalog.Entry entry(String id) {
    return Catalog.ENTRIES.stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
  }

  private List<String> stepIds() {
    return session.recipe().steps().stream().map(OpSpec::opId).toList();
  }

  // ── Abrir ─────────────────────────────────────────────────────────────

  @Test
  void openingShowsTheImageAndItsData() {
    assertThat(vm.hasImageProperty().get()).isTrue();
    assertThat(vm.viewer().frameProperty().get()).isNotNull();
    assertThat(vm.fileNameProperty().get()).isEqualTo("foto.png");
    assertThat(vm.formatProperty().get()).isEqualTo("PNG");
    assertThat(vm.sizeProperty().get()).isEqualTo("64 × 32 px");
    assertThat(vm.histogram().dataProperty().get().counted()).isEqualTo(64 * 32);
  }

  @Test
  void aBadFileShowsAStickyErrorWithDetails() throws Exception {
    File bad = dir.resolve("mala.png").toFile();
    Files.writeString(bad.toPath(), "no es una imagen");
    vm.open(bad);
    drain();
    assertThat(vm.notifications().notices()).hasSize(1);
    NotificationsViewModel.Notice n = vm.notifications().notices().get(0);
    assertThat(n.kind()).isEqualTo(NotificationsViewModel.Kind.ERROR);
    assertThat(n.message()).contains("mala.png");
    assertThat(n.details()).isNotEmpty();
    assertThat(vm.hasImageProperty().get()).isTrue();
  }

  @Test
  void onlyTheLastOpenRequestWins() throws Exception {
    vm.open(png("a.png", 10, 10));
    vm.open(png("b.png", 12, 12));
    drain();
    assertThat(vm.fileNameProperty().get()).isEqualTo("b.png");
  }

  // ── Ajustes básicos ───────────────────────────────────────────────────

  @Test
  void theFirstSliderCreatesTheStepAndNeutralRemovesIt() {
    ControlState ev = adjust("exposure");
    ev.userSet(0.5);
    ev.endGesture();
    assertThat(stepIds()).containsExactly(RecipeEdits.ADJUST);
    ev.reset();
    assertThat(stepIds()).isEmpty();
  }

  @Test
  void aDragIsOneUndoEntry() {
    ControlState ev = adjust("exposure");
    for (double v : new double[] {0.1, 0.2, 0.3, 0.4}) {
      ev.userSet(v);
    }
    ev.endGesture();
    assertThat(session.undoDepth()).isEqualTo(1);
    vm.undo();
    assertThat(stepIds()).isEmpty();
    assertThat(ev.value()).isEqualTo(0.0);
    assertThat(ev.modifiedProperty().get()).isFalse();
  }

  @Test
  void aDragThatReturnsToNeutralLeavesNoEntry() {
    ControlState ev = adjust("exposure");
    ev.userSet(0.1);
    ev.userSet(0.0);
    ev.endGesture();
    assertThat(session.undoDepth()).isZero();
  }

  @Test
  void undoAndRedoUpdateTheControls() {
    ControlState contrast = adjust("contrast");
    contrast.userSet(30);
    contrast.endGesture();
    vm.undo();
    assertThat(contrast.value()).isEqualTo(0.0);
    vm.redo();
    assertThat(contrast.value()).isEqualTo(30.0);
  }

  @Test
  void keyboardStepsAreGroupedUntilIdle() {
    ControlState ev = adjust("exposure");
    ev.nudge(1, false);
    ev.nudge(1, false);
    ev.nudge(1, true);
    assertThat(ev.value()).isEqualTo(0.021);
    assertThat(session.undoDepth()).isEqualTo(1);
    drain();
    ev.nudge(1, false);
    drain();
    assertThat(session.undoDepth()).isEqualTo(2);
  }

  @Test
  void adjustStaysAfterGeometry() {
    vm.crop().rotate(true);
    adjust("exposure").userSet(0.2);
    adjust("exposure").endGesture();
    assertThat(stepIds()).containsExactly(RecipeEdits.ROTATE, RecipeEdits.ADJUST);
  }

  @Test
  void theDetailGroupControlsASharpenStep() {
    ControlState amount = vm.basic().groups().get(2).controls().get(0);
    amount.userSet(80.0);
    amount.endGesture();
    assertThat(stepIds()).containsExactly(RecipeEdits.SHARPEN);
    amount.reset();
    assertThat(stepIds()).isEmpty();
  }

  // ── Catálogo y pila ───────────────────────────────────────────────────

  @Test
  void choosingAFilterAddsAndSelectsAnEditableStep() {
    vm.catalog().add(entry("blur"));
    assertThat(stepIds()).containsExactly("blur.gaussian");
    assertThat(vm.stack().selectedProperty().get()).isZero();
    assertThat(vm.params().titleProperty().get()).isEqualTo("Desenfoque");
    assertThat(vm.params().controls()).hasSize(1);
    assertThat(session.undoDepth()).isEqualTo(1);
  }

  @Test
  void editingAParameterChangesTheStepInOneEntry() {
    vm.catalog().add(entry("blur"));
    ControlState radius = vm.params().controls().get(0);
    radius.userSet(10.0);
    radius.userSet(12.0);
    radius.endGesture();
    assertThat(session.recipe().steps().get(0).params().getDouble("radius")).isEqualTo(12.0);
    assertThat(session.undoDepth()).isEqualTo(2);
    vm.undo();
    assertThat(vm.params().controls().get(0).value()).isEqualTo(4.0);
  }

  @Test
  void presetsAreTitledAndHideTheirParameter() {
    vm.catalog().add(entry("sepia"));
    assertThat(vm.stack().rows().get(0).title()).isEqualTo("Sepia");
    assertThat(vm.params().controls()).isEmpty();
  }

  @Test
  void equalizeHidesItsMarks() {
    vm.catalog().add(entry("equalize"));
    assertThat(vm.params().controls())
        .extracting(c -> c.spec().key())
        .doesNotContain("markBurned", "markDark");
  }

  @Test
  void stepsCanBeToggledRemovedAndReordered() {
    vm.catalog().add(entry("negative"));
    vm.catalog().add(entry("pixelate"));
    vm.catalog().add(entry("hue"));
    vm.stack().toggle(0);
    assertThat(vm.stack().rows().get(0).enabled()).isFalse();
    vm.stack().move(2, 0);
    assertThat(stepIds()).containsExactly("point.hue-rotate", "color.negative", "raster.pixelate");
    vm.stack().remove(1);
    assertThat(stepIds()).containsExactly("point.hue-rotate", "raster.pixelate");
    assertThat(session.undoDepth()).isEqualTo(6);
  }

  @Test
  void theSelectionFollowsAMovedStep() {
    vm.catalog().add(entry("negative"));
    vm.catalog().add(entry("pixelate"));
    vm.stack().select(1);
    vm.stack().move(1, 0);
    assertThat(vm.stack().selectedProperty().get()).isZero();
    assertThat(vm.params().titleProperty().get()).isEqualTo("Pixelar");
  }

  @Test
  void selectingTheAdjustStepShowsTheBasicPanel() {
    adjust("exposure").userSet(0.5);
    adjust("exposure").endGesture();
    vm.stack().select(0);
    assertThat(vm.stack().selectedProperty().get()).isEqualTo(-1);
  }

  @Test
  void theSummaryDescribesTheStep() {
    adjust("exposure").userSet(0.5);
    adjust("contrast").userSet(10);
    adjust("contrast").endGesture();
    assertThat(vm.stack().rows().get(0).summary()).isEqualTo("2 cambios");
    assertThat(vm.stack().headerProperty().get()).isEqualTo("Pila (1)");
  }

  @Test
  void resetCanBeUndoneAndClearsTheSelection() {
    vm.catalog().add(entry("negative"));
    vm.reset();
    assertThat(stepIds()).isEmpty();
    assertThat(vm.stack().selectedProperty().get()).isEqualTo(-1);
    vm.undo();
    assertThat(stepIds()).containsExactly("color.negative");
  }

  // ── Visor ─────────────────────────────────────────────────────────────

  @Test
  void hoverIsApproximateOnlyOnAProxyAndUsesImageCoordinates() {
    vm.viewer().actualSize();
    var t = vm.viewer().transformProperty().get();
    var p = t.imageToScreen(10.5, 5.5);
    vm.viewer().hover(p.x(), p.y());
    var r = vm.viewer().readoutProperty().get();
    assertThat(r.x()).isEqualTo(10);
    assertThat(r.y()).isEqualTo(5);
    assertThat(r.approximate()).isFalse();
  }

  @Test
  void hoverOutsideTheImageClearsTheReadout() {
    vm.viewer().hover(-5, -5);
    assertThat(vm.viewer().readoutProperty().get()).isNull();
  }

  @Test
  void aClickGivesTheExactPixel() {
    vm.catalog().add(entry("negative"));
    drain();
    var t = vm.viewer().transformProperty().get();
    var p = t.imageToScreen(0.5, 0.5);
    vm.viewer().pick(p.x(), p.y());
    drain();
    assertThat(vm.viewer().readoutProperty().get().argb()).isEqualTo(0xFFFFFFBF);
  }

  @Test
  void comparingRequestsTheBeforeImage() {
    vm.viewer().compareModeProperty().set(ViewerViewModel.CompareMode.CURTAIN);
    drain();
    assertThat(vm.viewer().beforeFrameProperty().get()).isNotNull();
  }

  @Test
  void holdingTheKeyShowsTheOriginalAndReleasingRestoresTheMode() {
    assertThat(vm.viewer().effectiveModeBinding().get())
        .isEqualTo(ViewerViewModel.CompareMode.RESULT);
    vm.viewer().holdOriginalProperty().set(true);
    drain();
    assertThat(vm.viewer().effectiveModeBinding().get())
        .isEqualTo(ViewerViewModel.CompareMode.ORIGINAL);
    assertThat(vm.viewer().beforeFrameProperty().get()).isNotNull();
    vm.viewer().holdOriginalProperty().set(false);
    assertThat(vm.viewer().effectiveModeBinding().get())
        .isEqualTo(ViewerViewModel.CompareMode.RESULT);
  }

  @Test
  void theViewRefitsWhenGeometryChangesTheSize() {
    vm.crop().rotate(true);
    drain();
    var t = vm.viewer().transformProperty().get();
    assertThat(t.imageWidth()).isEqualTo(32);
    assertThat(t.imageHeight()).isEqualTo(64);
    assertThat(vm.sizeProperty().get()).isEqualTo("32 × 64 px");
  }

  // ── Histograma ────────────────────────────────────────────────────────

  @Test
  void theHistogramFollowsTheImage() {
    long before = vm.histogram().dataProperty().get().counted();
    assertThat(vm.histogram().highlightsTextProperty().get()).startsWith("Luces recortadas");
    vm.catalog().add(entry("negative"));
    drain();
    assertThat(vm.histogram().dataProperty().get().counted()).isEqualTo(before);
  }

  @Test
  void theOverlayFollowsItsSwitch() {
    vm.histogram().overlayEnabledProperty().set(true);
    drain();
    assertThat(vm.histogram().overlayProperty().get()).isNotNull();
    vm.histogram().overlayEnabledProperty().set(false);
    drain();
    assertThat(vm.histogram().overlayProperty().get()).isNull();
  }

  // ── Geometría ─────────────────────────────────────────────────────────

  @Test
  void openingTheCropToolShowsTheImageWithoutTheCrop() {
    vm.toolProperty().set(StudioViewModel.Tool.CROP);
    vm.crop().setCrop(vm.crop().cropProperty().get().dragged(CropModel.Handle.SE, 0.5, 0.5));
    vm.crop().commit();
    drain();
    assertThat(vm.sizeProperty().get()).isEqualTo("32 × 16 px");
    vm.toolProperty().set(StudioViewModel.Tool.ADJUST);
    vm.toolProperty().set(StudioViewModel.Tool.CROP);
    drain();
    assertThat(vm.sizeProperty().get()).isEqualTo("64 × 32 px");
    assertThat(vm.crop().cropProperty().get().right()).isEqualTo(0.5);
  }

  @Test
  void cancellingTheCropKeepsTheRecipe() {
    vm.toolProperty().set(StudioViewModel.Tool.CROP);
    vm.crop().setCrop(vm.crop().cropProperty().get().dragged(CropModel.Handle.SE, 0.5, 0.5));
    vm.crop().cancel();
    drain();
    assertThat(stepIds()).isEmpty();
    assertThat(session.undoDepth()).isZero();
  }

  @Test
  void aFullCropRemovesTheStep() {
    vm.toolProperty().set(StudioViewModel.Tool.CROP);
    vm.crop().commit();
    assertThat(stepIds()).isEmpty();
  }

  @Test
  void fourTurnsLeaveNoStep() {
    for (int i = 0; i < 4; i++) {
      vm.crop().rotate(true);
    }
    assertThat(stepIds()).isEmpty();
  }

  @Test
  void flippingTwiceRemovesTheStepAndBothAxesAreAHalfTurn() {
    vm.crop().flip("horizontal");
    vm.crop().flip("horizontal");
    assertThat(stepIds()).isEmpty();
    vm.crop().flip("horizontal");
    vm.crop().flip("vertical");
    assertThat(stepIds()).containsExactly(RecipeEdits.ROTATE);
    assertThat(session.recipe().steps().get(0).params().getInt("turns")).isEqualTo(2);
  }

  @Test
  void straighteningIsOneEntryPerDrag() {
    ControlState s = vm.crop().straighten();
    s.userSet(1.0);
    s.userSet(2.0);
    s.endGesture();
    assertThat(session.undoDepth()).isEqualTo(1);
    s.reset();
    assertThat(stepIds()).isEmpty();
  }

  @Test
  void resizeAddsAndRemovesItsStep() {
    vm.crop().resize(32);
    drain();
    assertThat(vm.sizeProperty().get()).isEqualTo("32 × 16 px");
    vm.crop().resize(0);
    assertThat(stepIds()).isEmpty();
  }

  // ── Exportar y cierre ─────────────────────────────────────────────────

  @Test
  void exportWritesAPngAndNotifies() {
    adjust("exposure").userSet(0.5);
    adjust("exposure").endGesture();
    File chosen = dir.resolve("salida.jpg").toFile();
    vm.export(chosen);
    drain();
    assertThat(dir.resolve("salida.png")).exists();
    assertThat(dir.resolve("salida.jpg")).doesNotExist();
    assertThat(vm.notifications().notices()).isEmpty();
  }

  @Test
  void informationNoticesGoAwayByThemselves() {
    vm.notifications().info("hola");
    assertThat(vm.notifications().notices()).hasSize(1);
    time.advance(Duration.ofSeconds(5).toNanos());
    drain();
    assertThat(vm.notifications().notices()).isEmpty();
  }

  @Test
  void closingStopsListeningToTheSession() {
    vm.close();
    int rows = vm.stack().rows().size();
    session.resetRecipe();
    session.applyRecipe(
        session
            .recipe()
            .add(
                OpSpec.of(
                    session.registry().require("color.negative"),
                    session.registry().require("color.negative").schema().defaults())),
        null);
    assertThat(vm.stack().rows()).hasSize(rows);
  }
}
