package io.github.erenriquezp.rora.app.shell;

import io.github.erenriquezp.rora.app.catalog.Catalog;
import io.github.erenriquezp.rora.app.model.Curtain;
import io.github.erenriquezp.rora.app.session.ExportTarget;
import io.github.erenriquezp.rora.app.viewmodel.ControlState;
import io.github.erenriquezp.rora.app.viewmodel.StudioViewModel;
import io.github.erenriquezp.rora.app.viewmodel.ViewerViewModel.CompareMode;
import io.github.erenriquezp.rora.core.io.ExportSettings;
import io.github.erenriquezp.rora.core.io.FormatId;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.IntConsumer;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Herramienta de desarrollo (no se distribuye): ejercita la interfaz real a ~60 Hz con el monitor
 * de bloqueos activo ({@code -Drora.fxStallMonitor=true}). Argumento: imagen grande. Cada sección
 * se anuncia con una línea {@code SECTION} para atribuirle las pausas.
 */
public class StressTool extends StudioApp {

  private record Section(String name, int ticks, IntConsumer tick) {}

  private static final List<String> FILTERS =
      List.of(
          "negative",
          "hue",
          "posterize",
          "pixelate",
          "sepia",
          "sharpen",
          "blur",
          "duotone",
          "motion");

  private final Deque<Section> sections = new ArrayDeque<>();

  @Override
  protected LayoutStore layoutStore() {
    return LayoutStore.inMemory();
  }

  private ControlState exposure(StudioViewModel<WritableImage> vm) {
    return vm.basic().groups().get(0).controls().get(0);
  }

  @Override
  public void start(Stage stage) {
    super.start(stage);
    StudioViewModel<WritableImage> vm = viewModel();
    File image = new File(getParameters().getRaw().get(0));
    sections.add(new Section("open-24mp", 1, i -> vm.open(image)));
    sections.add(
        new Section(
            "recipe-10-steps",
            FILTERS.size() * 30,
            i -> {
              if (i % 30 == 0) {
                vm.catalog()
                    .add(
                        Catalog.ENTRIES.stream()
                            .filter(e -> e.id().equals(FILTERS.get(i / 30)))
                            .findFirst()
                            .orElseThrow());
              }
            }));
    sections.add(
        new Section(
            "slider-drag",
            300,
            i -> {
              vm.stack().clearSelection();
              exposure(vm).userSet(Math.sin(i / 20.0));
              if (i == 299) {
                exposure(vm).endGesture();
              }
            }));
    sections.add(new Section("zoom", 240, i -> vm.viewer().zoomAt(500, 350, i % 80 < 40)));
    sections.add(
        new Section(
            "pan",
            180,
            i -> {
              if (i == 0) {
                vm.viewer().actualSize();
              }
              vm.viewer().panBy(Math.sin(i / 8.0) * 20, Math.cos(i / 8.0) * 20);
            }));
    sections.add(
        new Section(
            "curtain",
            240,
            i -> {
              if (i == 0) {
                vm.viewer().fit();
                vm.viewer().compareModeProperty().set(CompareMode.CURTAIN);
              }
              vm.viewer().curtainProperty().set(new Curtain(0.5 + 0.4 * Math.sin(i / 15.0)));
            }));
    sections.add(
        new Section(
            "undo-redo",
            240,
            i -> {
              if (i == 0) {
                vm.viewer().compareModeProperty().set(CompareMode.RESULT);
              }
              if (i % 12 == 0) {
                if (i % 24 == 0) {
                  vm.undo();
                } else {
                  vm.redo();
                }
              }
            }));
    sections.add(
        new Section(
            "export-dialog-first-open",
            30,
            i -> {
              if (i == 0) {
                vm.showExport();
              } else if (i == 29) {
                vm.exportDialog().close();
              }
            }));
    sections.add(
        new Section(
            "export-jpeg-24mp",
            1,
            i ->
                vm.startExport(
                    ExportSettings.defaults(FormatId.JPEG),
                    exportDir(image),
                    "stress-" + System.nanoTime() + ".jpg",
                    ExportTarget.Collision.ASK)));
    sections.add(
        new Section(
            "export-png-24mp",
            1,
            i ->
                vm.startExport(
                    ExportSettings.defaults(FormatId.PNG),
                    exportDir(image),
                    "stress-" + System.nanoTime() + ".png",
                    ExportTarget.Collision.ASK)));
    addWallpaperSections(vm, image);
    // El arranque de la ventana (CSS, fuentes, Prism) no forma parte de lo que se mide.
    System.out.println("SECTION startup");
    PauseTransition warm = new PauseTransition(Duration.seconds(4));
    warm.setOnFinished(e -> next());
    warm.play();
  }

  /** Fase 6: reencuadrar, cambiar destinos y exportar con la fuente de 24 MP. */
  private void addWallpaperSections(StudioViewModel<WritableImage> vm, File image) {
    var wp = vm.wallpapers();
    sections.add(
        new Section(
            "wallpapers-open-tool",
            60,
            i -> {
              if (i == 0) {
                vm.reset();
                vm.toolProperty().set(StudioViewModel.Tool.WALLPAPERS);
              }
            }));
    sections.add(
        new Section(
            "wallpapers-reframe",
            300,
            i -> {
              if (i == 0) {
                wp.select("pc-4k");
              }
              var o = wp.overlayProperty().get();
              if (o != null && o.editable()) {
                double dy = 0.004 * Math.sin(i / 10.0);
                double dx = 0.004 * Math.cos(i / 13.0);
                wp.setFrame(o.frame().movedBy(dx, dy));
              }
            }));
    List<String> ids =
        List.of("pc-4k", "pc-qhd", "phone-1080-2400", "phone-1179-2556", "tablet-1668-2388");
    sections.add(
        new Section(
            "wallpapers-switch-targets",
            150,
            i -> {
              if (i % 10 == 0) {
                wp.select(ids.get((i / 10) % ids.size()));
              }
            }));
    sections.add(
        new Section(
            "wallpapers-fit-fill",
            90,
            i -> {
              if (i % 15 == 0) {
                wp.fitProperty()
                    .set(
                        io.github.erenriquezp.rora.wallpapers.plan.Target.FitMode.values()[
                            (i / 15) % 3]);
              }
            }));
    sections.add(
        new Section(
            "wallpapers-export-24mp",
            1,
            i -> {
              wp.autoAll();
              wp.folderProperty()
                  .set(exportDir(image).resolve("stress-wallpapers-" + System.nanoTime()).toString());
              wp.showExport();
              wp.confirmExport();
            }));
  }

  private static Path exportDir(File image) {
    return image.toPath().toAbsolutePath().getParent();
  }

  private void next() {
    Section s = sections.poll();
    if (s == null) {
      System.out.println("SECTION end");
      Platform.exit();
      return;
    }
    System.out.println("SECTION " + s.name());
    int[] i = {0};
    Timeline t = new Timeline(new KeyFrame(Duration.millis(16), e -> s.tick().accept(i[0]++)));
    t.setCycleCount(s.ticks());
    t.setOnFinished(e -> settle());
    if (s.ticks() > 0) {
      t.playFromStart();
    }
    if (t.getStatus() == Animation.Status.STOPPED) {
      settle();
    }
  }

  private void settle() {
    String st = viewModel().statusProperty().get();
    if (st.startsWith("Abriendo")
        || st.startsWith("Exportando")
        || viewModel().wallpapers().exportingProperty().get()) {
      PauseTransition poll = new PauseTransition(Duration.millis(100));
      poll.setOnFinished(e -> settle());
      poll.play();
      return;
    }
    PauseTransition pause = new PauseTransition(Duration.seconds(2));
    pause.setOnFinished(e -> next());
    pause.play();
  }

  public static void main(String[] args) {
    Application.launch(StressTool.class, args);
  }
}
