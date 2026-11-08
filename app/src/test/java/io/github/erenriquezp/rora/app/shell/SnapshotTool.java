package io.github.erenriquezp.rora.app.shell;

import io.github.erenriquezp.rora.app.catalog.Catalog;
import io.github.erenriquezp.rora.app.viewmodel.ControlState;
import io.github.erenriquezp.rora.app.viewmodel.StudioViewModel;
import io.github.erenriquezp.rora.app.viewmodel.ViewerViewModel.CompareMode;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;
import javax.imageio.ImageIO;

/**
 * Herramienta de desarrollo (no se distribuye): abre la interfaz real y guarda en PNG los estados
 * de los wireframes, sin interacción. Uso: ver {@code docs/UI.md}. Argumentos: imagen de entrada,
 * carpeta de salida, archivo no válido para el estado de error.
 */
public class SnapshotTool extends StudioApp {

  private static final double SETTLE_SECONDS = 1.6;

  private record Shot(String name, double width, double height, Runnable action) {}

  private Stage stage;
  private File output;
  private final Deque<Shot> shots = new ArrayDeque<>();

  @Override
  protected LayoutStore layoutStore() {
    return LayoutStore.inMemory();
  }

  private ControlState adjust(StudioViewModel<WritableImage> vm, String key) {
    return vm.basic().groups().stream()
        .flatMap(g -> g.controls().stream())
        .filter(c -> c.spec().key().equals(key))
        .findFirst()
        .orElseThrow();
  }

  private static void set(ControlState c, double value) {
    c.userSet(value);
    c.endGesture();
  }

  private void addFilter(StudioViewModel<WritableImage> vm, String id) {
    vm.catalog()
        .add(Catalog.ENTRIES.stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow());
  }

  @Override
  public void start(Stage primary) {
    super.start(primary);
    stage = primary;
    List<String> args = getParameters().getRaw();
    File image = new File(args.get(0));
    output = new File(args.get(1));
    File broken = new File(args.get(2));
    output.mkdirs();
    StudioViewModel<WritableImage> vm = viewModel();
    double w = 1400;
    double h = 860;
    shots.add(new Shot("01-vacio", w, h, () -> {}));
    shots.add(new Shot("02-cargada", w, h, () -> vm.open(image)));
    shots.add(
        new Shot(
            "03-ajustes",
            w,
            h,
            () -> {
              set(adjust(vm, "exposure"), 0.45);
              set(adjust(vm, "contrast"), 18);
              set(adjust(vm, "shadows"), 30);
              set(adjust(vm, "vibrance"), 25);
            }));
    shots.add(
        new Shot(
            "04-cortina",
            w,
            h,
            () -> vm.viewer().compareModeProperty().set(CompareMode.CURTAIN)));
    shots.add(
        new Shot(
            "05-lado-a-lado",
            w,
            h,
            () -> vm.viewer().compareModeProperty().set(CompareMode.SIDE_BY_SIDE)));
    shots.add(
        new Shot(
            "06-pila-5-pasos",
            w,
            h,
            () -> {
              vm.viewer().compareModeProperty().set(CompareMode.RESULT);
              vm.crop().rotate(true);
              vm.crop().rotate(false);
              addFilter(vm, "sepia");
              addFilter(vm, "sharpen");
              addFilter(vm, "pixelate");
              addFilter(vm, "blur");
              vm.stack().toggle(1);
            }));
    shots.add(
        new Shot(
            "07-catalogo",
            w,
            h,
            () -> {
              vm.stack().clearSelection();
              vm.toolProperty().set(StudioViewModel.Tool.FILTERS);
            }));
    shots.add(
        new Shot(
            "08-recortar",
            w,
            h,
            () -> {
              vm.reset();
              vm.toolProperty().set(StudioViewModel.Tool.CROP);
              vm.crop().setCrop(vm.crop().cropProperty().get().withRatio(16.0 / 9));
            }));
    shots.add(
        new Shot(
            "09-recorte-superpuesto",
            w,
            h,
            () -> {
              vm.crop().cancel();
              vm.toolProperty().set(StudioViewModel.Tool.ADJUST);
              set(adjust(vm, "exposure"), 1.6);
              vm.histogram().overlayEnabledProperty().set(true);
            }));
    shots.add(
        new Shot(
            "10-error",
            w,
            h,
            () -> {
              vm.histogram().overlayEnabledProperty().set(false);
              vm.reset();
              vm.open(broken);
            }));
    shots.add(
        new Shot(
            "11-ventana-estrecha",
            1100,
            700,
            () -> {
              vm.toolProperty().set(StudioViewModel.Tool.FILTERS);
              set(adjust(vm, "contrast"), 20);
            }));
    next();
  }

  private void next() {
    Shot shot = shots.poll();
    if (shot == null) {
      Platform.exit();
      return;
    }
    stage.setWidth(shot.width());
    stage.setHeight(shot.height());
    shot.action().run();
    waitUntilLoaded(shot);
  }

  /** Abrir una foto tarda segundos: espera a que termine antes de dejar asentar la vista. */
  private void waitUntilLoaded(Shot shot) {
    if (viewModel().statusProperty().get().startsWith("Abriendo")) {
      PauseTransition poll = new PauseTransition(Duration.millis(100));
      poll.setOnFinished(e -> waitUntilLoaded(shot));
      poll.play();
      return;
    }
    PauseTransition settle = new PauseTransition(Duration.seconds(SETTLE_SECONDS));
    settle.setOnFinished(
        e -> {
          save(shot.name());
          next();
        });
    settle.play();
  }

  private void save(String name) {
    WritableImage snap = stage.getScene().snapshot(null);
    PixelReader r = snap.getPixelReader();
    int w = (int) snap.getWidth();
    int h = (int) snap.getHeight();
    BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        out.setRGB(x, y, r.getArgb(x, y));
      }
    }
    try {
      ImageIO.write(out, "png", new File(output, name + ".png"));
    } catch (java.io.IOException ex) {
      throw new java.io.UncheckedIOException(ex);
    }
  }

  public static void main(String[] args) {
    Application.launch(SnapshotTool.class, args);
  }
}
