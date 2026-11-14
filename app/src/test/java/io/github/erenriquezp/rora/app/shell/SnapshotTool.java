package io.github.erenriquezp.rora.app.shell;

import io.github.erenriquezp.rora.app.catalog.Catalog;
import io.github.erenriquezp.rora.app.session.ExportTarget;
import io.github.erenriquezp.rora.app.viewmodel.ControlState;
import io.github.erenriquezp.rora.app.viewmodel.ExportDialogViewModel;
import io.github.erenriquezp.rora.app.viewmodel.NotificationsViewModel;
import io.github.erenriquezp.rora.app.viewmodel.StudioViewModel;
import io.github.erenriquezp.rora.app.viewmodel.ViewerViewModel.CompareMode;
import io.github.erenriquezp.rora.core.io.ExportSettings;
import io.github.erenriquezp.rora.core.io.FormatId;
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

  /** Prefijo de las capturas que esperan a que termine la mejora en curso. */
  private static final String WAIT_FOR_ENHANCE = "espera:";

  private record Shot(String name, double width, double height, Runnable action) {}

  /** Comprobador de actualizaciones falso: la herramienta nunca sale a la red. */
  private volatile boolean updateOffline;

  private io.github.erenriquezp.rora.app.viewmodel.UpdateViewModel update;

  @Override
  protected io.github.erenriquezp.rora.app.viewmodel.UpdateViewModel updateViewModel(
      io.github.erenriquezp.rora.app.i18n.Messages messages) {
    update =
        new io.github.erenriquezp.rora.app.viewmodel.UpdateViewModel(
            messages,
            Platform::runLater,
            r -> new Thread(r, "captura-actualizaciones").start(),
            new io.github.erenriquezp.rora.app.update.UpdateChecker(
                () -> {
                  if (updateOffline) {
                    throw new io.github.erenriquezp.rora.app.update.UpdateChecker.SourceException(
                        io.github.erenriquezp.rora.app.update.UpdateResult.Reason.NO_NETWORK,
                        "captura sin red");
                  }
                  return "[{\"tag_name\":\"v0.2.0\",\"draft\":false,\"html_url\":\""
                      + io.github.erenriquezp.rora.app.update.ReleaseFeed.RELEASES_PAGE
                      + "/tag/v0.2.0\",\"published_at\":\"2027-01-10T10:00:00Z\"}]";
                },
                true),
            new io.github.erenriquezp.rora.app.diag.AppInfo("0.1.0", "beta"),
            url -> {});
    return update;
  }

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
    shots.add(
        new Shot(
            "34-wallpapers-sin-foto",
            w,
            h,
            () -> vm.toolProperty().set(StudioViewModel.Tool.WALLPAPERS)));
    shots.add(
        new Shot(
            "02-cargada",
            w,
            h,
            () -> {
              vm.toolProperty().set(StudioViewModel.Tool.ADJUST);
              vm.open(image);
            }));
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
            "04-cortina", w, h, () -> vm.viewer().compareModeProperty().set(CompareMode.CURTAIN)));
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
    shots.add(
        new Shot(
            "14-exportar-jpeg",
            w,
            h,
            () -> {
              vm.toolProperty().set(StudioViewModel.Tool.ADJUST);
              vm.notifications().notices().clear();
              vm.showExport();
            }));
    shots.add(
        new Shot(
            "15-exportar-png", w, h, () -> vm.exportDialog().formatProperty().set(FormatId.PNG)));
    shots.add(
        new Shot(
            "16-exportar-web-estrecha",
            1100,
            700,
            () -> vm.exportDialog().applyPreset(ExportDialogViewModel.Preset.WEB)));
    shots.add(
        new Shot(
            "17-progreso",
            w,
            h,
            () -> {
              vm.exportDialog().close();
              vm.ioProgress().begin("Abriendo paisaje_24mp.jpg\u2026", () -> {});
              vm.ioProgress().update(0.42);
            }));
    shots.add(
        new Shot(
            "18-sin-exportar",
            w,
            h,
            () -> {
              vm.ioProgress().end();
              vm.requestOpen(image);
            }));
    shots.add(
        new Shot(
            "19-archivo-existe",
            w,
            h,
            () -> {
              vm.promptProperty().get().choices().get(2).action().run();
              try {
                java.nio.file.Files.writeString(output.toPath().resolve("existe.jpg"), "x");
              } catch (java.io.IOException ex) {
                throw new java.io.UncheckedIOException(ex);
              }
              vm.startExport(
                  ExportSettings.defaults(FormatId.JPEG),
                  output.toPath(),
                  "existe.jpg",
                  ExportTarget.Collision.ASK);
            }));
    shots.add(
        new Shot(
            "20-aviso-16bits",
            w,
            h,
            () -> {
              vm.promptProperty().get().choices().get(2).action().run();
              vm.reset();
              File g16 = new File(output, "g16.png");
              try {
                ImageIO.write(
                    new BufferedImage(600, 400, BufferedImage.TYPE_USHORT_GRAY), "png", g16);
              } catch (java.io.IOException ex) {
                throw new java.io.UncheckedIOException(ex);
              }
              vm.open(g16);
            }));
    addEnhanceShots(vm, image, w, h);
    addWallpaperShots(vm, image, w, h);
    if (args.contains("--lab")) {
      shots.add(
          new Shot(
              "12-laboratorio-filtro",
              w,
              h,
              () -> {
                vm.toolProperty().set(StudioViewModel.Tool.FILTERS);
                vm.catalog().queryProperty().set("z-buffer");
                vm.catalog().add(vm.catalog().results().get(0));
              }));
      shots.add(
          new Shot(
              "13-laboratorio-ventana",
              w,
              h,
              () ->
                  stage.getScene().getRoot().lookupAll(".tool-button").stream()
                      .filter(n -> n instanceof javafx.scene.control.Button)
                      .map(n -> (javafx.scene.control.Button) n)
                      .findFirst()
                      .orElseThrow()
                      .fire()));
    }
    next();
  }

  /** Estados de Mejorar con el modelo real: el arnés mide las pausas del hilo FX durante estos. */
  private void addEnhanceShots(StudioViewModel<WritableImage> vm, File image, double w, double h) {
    shots.add(
        new Shot(
            "21-mejorar-listo",
            w,
            h,
            () -> {
              vm.notifications().notices().clear();
              vm.open(image);
              vm.toolProperty().set(StudioViewModel.Tool.ENHANCE);
            }));
    shots.add(new Shot("22-mejorar-ejecutando", w, h, () -> vm.enhance().start()));
    shots.add(
        new Shot(
            WAIT_FOR_ENHANCE + "23-mejorar-resultado-cortina",
            w,
            h,
            () -> vm.viewer().compareModeProperty().set(CompareMode.CURTAIN)));
    shots.add(
        new Shot(
            "24-mejorar-intensidad",
            w,
            h,
            () -> {
              vm.viewer().compareModeProperty().set(CompareMode.RESULT);
              for (double v = 95; v >= 50; v -= 5) {
                vm.enhance().intensity().userSet(v);
              }
              vm.enhance().intensity().endGesture();
            }));
    shots.add(new Shot("25-mejorar-obsoleto", w, h, () -> vm.crop().rotate(true)));
    shots.add(new Shot("26-mejorar-de-nuevo", w, h, () -> vm.enhance().start()));
    shots.add(new Shot("27-mejorar-cancelado", w, h, () -> vm.enhance().cancel()));
    shots.add(
        new Shot(
            WAIT_FOR_ENHANCE + "28-mejorar-error",
            w,
            h,
            () ->
                vm.notifications()
                    .notices()
                    .add(
                        new NotificationsViewModel.Notice(
                            NotificationsViewModel.Kind.ERROR,
                            vm.messages()
                                .format(
                                    "ui.error.titled",
                                    vm.messages().get("ui.enhance.failed"),
                                    vm.messages().get("io.error.ai_failure")),
                            "captura simulada",
                            null))));
    File big = new File(output, "6mp.png");
    shots.add(
        new Shot(
            "29-mejorar-limite",
            w,
            h,
            () -> {
              vm.notifications().notices().clear();
              try {
                ImageIO.write(new BufferedImage(3000, 2000, BufferedImage.TYPE_INT_RGB), "png", big);
              } catch (java.io.IOException ex) {
                throw new java.io.UncheckedIOException(ex);
              }
              vm.open(big);
            }));
    shots.add(new Shot("30-mejorar-ventana-estrecha", 1100, 700, () -> {}));
    // Pregunta antes solo si la estimación de este equipo supera los 120 s.
    shots.add(new Shot("31-mejorar-grande", w, h, () -> vm.enhance().start()));
    shots.add(
        new Shot(
            WAIT_FOR_ENHANCE + "32-mejorar-sin-ejecutar",
            w,
            h,
            () -> {
              if (vm.promptProperty().get() != null) {
                vm.promptProperty().get().choices().get(0).action().run();
              }
              vm.enhance().cancel();
            }));
    shots.add(
        new Shot(
            "33-exportar-no-listo",
            w,
            h,
            () ->
                vm.startExport(
                    ExportSettings.defaults(FormatId.JPEG),
                    output.toPath(),
                    "no-listo.jpg",
                    ExportTarget.Collision.NUMBER)));
  }

  /** Estados de Wallpapers (fase 6), con el detector y la IA reales. */
  private void addWallpaperShots(
      StudioViewModel<WritableImage> vm, File image, double w, double h) {
    var wp = vm.wallpapers();
    shots.add(
        new Shot(
            "35-wallpapers-seleccion",
            w,
            h,
            () -> {
              if (vm.promptProperty().get() != null) {
                var c = vm.promptProperty().get().choices();
                c.get(c.size() - 1).action().run();
              }
              vm.notifications().notices().clear();
              vm.open(image);
              vm.toolProperty().set(StudioViewModel.Tool.WALLPAPERS);
            }));
    shots.add(
        new Shot("36-wallpapers-zona-segura", w, h, () -> wp.select("phone-1179-2556")));
    shots.add(
        new Shot(
            "37-wallpapers-relleno",
            w,
            h,
            () ->
                wp.fitProperty()
                    .set(io.github.erenriquezp.rora.wallpapers.plan.Target.FitMode.CONTAIN)));
    File small = new File(output, "pequena-1mp.png");
    shots.add(
        new Shot(
            "38-wallpapers-sugerencia-ia",
            w,
            h,
            () -> {
              try {
                BufferedImage b = new BufferedImage(1155, 866, BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = b.createGraphics();
                g.setPaint(
                    new java.awt.GradientPaint(
                        0, 0, new java.awt.Color(40, 90, 160), 1155, 866, new java.awt.Color(230, 170, 90)));
                g.fillRect(0, 0, 1155, 866);
                g.dispose();
                ImageIO.write(b, "png", small);
              } catch (java.io.IOException ex) {
                throw new java.io.UncheckedIOException(ex);
              }
              vm.open(small);
            }));
    File big = new File(output, "grande-24mp.png");
    shots.add(
        new Shot(
            "39-wallpapers-24mp",
            w,
            h,
            () -> {
              try {
                BufferedImage b = new BufferedImage(6000, 4000, BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = b.createGraphics();
                g.setPaint(
                    new java.awt.GradientPaint(
                        0, 0, new java.awt.Color(20, 40, 80), 6000, 4000, new java.awt.Color(240, 200, 120)));
                g.fillRect(0, 0, 6000, 4000);
                g.dispose();
                ImageIO.write(b, "png", big);
              } catch (java.io.IOException ex) {
                throw new java.io.UncheckedIOException(ex);
              }
              vm.open(big);
            }));
    shots.add(
        new Shot(
            "40-wallpapers-exportando",
            w,
            h,
            () -> {
              wp.folderProperty().set(new File(output, "wallpapers-ok").getAbsolutePath());
              wp.showExport();
              wp.confirmExport();
            }));
    shots.add(new Shot(WAIT_FOR_ENHANCE + "40b-wallpapers-informe", w, h, () -> {}));
    shots.add(
        new Shot(
            WAIT_FOR_ENHANCE + "41-wallpapers-informe-fallo",
            w,
            h,
            () -> {
              wp.closeReport();
              File bad = new File(output, "wallpapers-fallo");
              File base = new File(bad, "grande-24mp - wallpapers");
              base.mkdirs();
              try {
                java.nio.file.Files.writeString(new File(base, "Móvil").toPath(), "estorbo");
              } catch (java.io.IOException ex) {
                throw new java.io.UncheckedIOException(ex);
              }
              wp.folderProperty().set(bad.getAbsolutePath());
              wp.showExport();
              wp.confirmExport();
            }));
    shots.add(new Shot("42-wallpapers-ventana-estrecha", 1100, 700, wp::closeReport));
    // Fase 8: estimación de tamaño, ayuda, "Acerca de", informe, actualizaciones y errores.
    shots.add(new Shot("43-wallpapers-estimacion", w, h, wp::showExport));
    shots.add(
        new Shot(
            "44-ayuda-menu",
            w,
            h,
            () -> {
              wp.closeExport();
              if (stage.getScene().lookup(".help-button")
                  instanceof javafx.scene.control.MenuButton b) {
                b.show();
              }
            }));
    shots.add(
        new Shot(
            "45-acerca-de",
            w,
            h,
            () -> {
              if (stage.getScene().lookup(".help-button")
                  instanceof javafx.scene.control.MenuButton b) {
                b.hide();
              }
              help().showAbout();
            }));
    shots.add(
        new Shot(
            "46-informe-diagnostico",
            w,
            h,
            () -> {
              help().closeAbout();
              help().openReport();
            }));
    shots.add(
        new Shot(
            "47-actualizacion-nueva",
            w,
            h,
            () -> {
              help().closeReport();
              update.check();
            }));
    shots.add(
        new Shot(
            "48-actualizacion-sin-red",
            w,
            h,
            () -> {
              update.close();
              updateOffline = true;
              update.check();
            }));
    shots.add(
        new Shot(
            "49-error-generico",
            w,
            h,
            () -> {
              update.close();
              help().onError(new IllegalStateException("captura simulada"));
            }));
    shots.add(
        new Shot(
            "50-error-ia-vcruntime",
            w,
            h,
            () -> {
              vm.promptProperty().set(null);
              vm.notifications()
                  .notices()
                  .add(
                      new NotificationsViewModel.Notice(
                          NotificationsViewModel.Kind.ERROR,
                          vm.messages().get("ui.error.vcRuntime"),
                          "captura simulada",
                          new NotificationsViewModel.Action(
                              vm.messages().get("ui.error.vcRuntimeDownload"), () -> {})));
            }));
    shots.add(new Shot("51-memoria-agotada", w, h, () -> help().onOutOfMemory(false)));
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
    boolean enhancing =
        shot.name().startsWith(WAIT_FOR_ENHANCE)
            && (viewModel().enhance().runningProperty().get()
                || viewModel().wallpapers().exportingProperty().get());
    if (enhancing || viewModel().statusProperty().get().startsWith("Abriendo")) {
      PauseTransition poll = new PauseTransition(Duration.millis(100));
      poll.setOnFinished(e -> waitUntilLoaded(shot));
      poll.play();
      return;
    }
    PauseTransition settle = new PauseTransition(Duration.seconds(SETTLE_SECONDS));
    settle.setOnFinished(
        e -> {
          save(shot.name().replace(WAIT_FOR_ENHANCE, ""));
          next();
        });
    settle.play();
  }

  private void save(String name) {
    var windows = javafx.stage.Window.getWindows();
    WritableImage snap = windows.get(windows.size() - 1).getScene().snapshot(null);
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
