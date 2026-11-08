package io.github.erenriquezp.rora.app.shell;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.app.viewmodel.ControlState;
import io.github.erenriquezp.rora.app.viewmodel.StudioViewModel;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Humo con el toolkit real (perfil {@code ui-smoke}, necesita pantalla o {@code xvfb-run}; no entra
 * en {@code verify}): arranca, abre una imagen, mueve un ajuste y exporta.
 */
class StudioUiSmoke {

  private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(60);

  @TempDir Path dir;

  private static final class App extends StudioApp {
    @Override
    protected LayoutStore layoutStore() {
      return LayoutStore.inMemory();
    }

    @Override
    protected java.util.List<String> arguments() {
      return java.util.List.of();
    }

    StudioViewModel<WritableImage> vm() {
      return viewModel();
    }
  }

  private static <T> T onFx(Callable<T> work) throws Exception {
    CompletableFuture<T> result = new CompletableFuture<>();
    Platform.runLater(
        () -> {
          try {
            result.complete(work.call());
          } catch (Exception | Error e) {
            result.completeExceptionally(e);
          }
        });
    return result.get(60, TimeUnit.SECONDS);
  }

  /** Espera una condición que se evalúa en el hilo FX; el toolkit real no tiene tiempo virtual. */
  private static void await(BooleanSupplier condition, String what) throws Exception {
    long deadline = System.nanoTime() + TIMEOUT_NANOS;
    while (!onFx(condition::getAsBoolean)) {
      assertThat(System.nanoTime()).as("Esperando: " + what).isLessThan(deadline);
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
    }
  }

  @Test
  void opensAdjustsAndExportsOnTheRealToolkit() throws Exception {
    File input = dir.resolve("entrada.png").toFile();
    BufferedImage image = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
    ImageIO.write(image, "png", input);
    File output = dir.resolve("salida.png").toFile();

    CompletableFuture<Void> started = new CompletableFuture<>();
    Platform.startup(() -> started.complete(null));
    started.get(60, TimeUnit.SECONDS);

    App app = new App();
    onFx(
        () -> {
          app.start(new Stage());
          return null;
        });
    onFx(
        () -> {
          app.vm().open(input);
          return null;
        });
    await(() -> app.vm().viewer().frameProperty().get() != null, "primer frame");

    var first = onFx(() -> app.vm().viewer().frameProperty().get());
    onFx(
        () -> {
          ControlState exposure = app.vm().basic().groups().get(0).controls().get(0);
          exposure.userSet(1.0);
          exposure.endGesture();
          return null;
        });
    await(() -> app.vm().viewer().frameProperty().get() != first, "frame con el ajuste");
    assertThat(onFx(() -> app.vm().stack().rows().size())).isEqualTo(1);

    onFx(
        () -> {
          app.vm().export(output);
          return null;
        });
    await(output::exists, "archivo exportado");
    await(() -> app.vm().statusProperty().get().startsWith("Exportada"), "fin de la exportación");
    BufferedImage written = ImageIO.read(output);
    assertThat(written.getWidth()).isEqualTo(320);
    assertThat(written.getHeight()).isEqualTo(200);

    onFx(
        () -> {
          app.stop();
          return null;
        });
    Platform.exit();
  }
}
