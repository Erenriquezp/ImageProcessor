package io.github.erenriquezp.rora.app.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.session.EditSession;
import io.github.erenriquezp.rora.app.session.ManualExecutor;
import io.github.erenriquezp.rora.app.session.ManualScheduler;
import io.github.erenriquezp.rora.app.session.RenderCoordinator;
import io.github.erenriquezp.rora.app.session.RenderLane;
import io.github.erenriquezp.rora.app.session.wallpaper.WallpaperWorkspace;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy;
import io.github.erenriquezp.rora.wallpapers.plan.OutputFormat;
import io.github.erenriquezp.rora.wallpapers.preset.UserPresetStore;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Estimación del peso en el diálogo de exportación de Wallpapers (fase 8). */
class WallpapersEstimateTest {

  private static final Messages M = Messages.of(Locale.of("es"));

  @TempDir Path dir;

  private final ManualScheduler time = new ManualScheduler();
  private final ManualExecutor preview = new ManualExecutor();
  private final ManualExecutor full = new ManualExecutor();
  private final ManualExecutor aux = new ManualExecutor();
  private final ManualExecutor publisher = new ManualExecutor();
  private final ManualExecutor background = new ManualExecutor();
  private final ManualExecutor io = new ManualExecutor();
  private final Map<String, String> prefs = new HashMap<>();
  private EditSession<Img> session;
  private StudioViewModel<Img> studio;
  private WallpapersViewModel<Img> vm;

  private static long ms(long m) {
    return Duration.ofMillis(m).toNanos();
  }

  @BeforeEach
  void setUp() throws Exception {
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
    studio =
        new StudioViewModel<>(
            new Env<>(session, M, time, publisher, background, io),
            img -> img,
            img -> img,
            img -> img,
            WallpaperWorkspace.withoutDetector(),
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
    BufferedImage bi = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < 300; y++) {
      for (int x = 0; x < 400; x++) {
        bi.setRGB(x, y, (x % 256) << 16 | (y % 256) << 8 | ((x * y) % 256));
      }
    }
    File f = dir.resolve("foto.png").toFile();
    ImageIO.write(bi, "png", f);
    studio.open(f);
    drain();
    studio.toolProperty().set(StudioViewModel.Tool.WALLPAPERS);
    drain();
    vm.folderProperty().set(dir.resolve("salida").toString());
  }

  @AfterEach
  void tearDown() {
    studio.close();
    session.close();
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

  @Test
  void openingTheDialogEstimatesEveryTargetOffTheUiThread() {
    vm.showExport();
    assertThat(vm.estimateTotalProperty().get())
        .isEqualTo(M.get("ui.wallpapers.dialog.estimating"));
    assertThat(vm.estimates()).isEmpty();
    drain();
    int targets = vm.planProperty().get().targets().size();
    assertThat(vm.estimates()).hasSize(targets);
    assertThat(vm.estimates()).allMatch(s -> s.matches(".+: ≈ \\d+([,.]\\d)? (KB|MB)"));
    assertThat(vm.estimateTotalProperty().get()).startsWith("Tamaño estimado: ≈ ");
  }

  @Test
  void changingTheFormatRecomputesAndClosingDiscards() {
    vm.showExport();
    drain();
    List<String> auto = List.copyOf(vm.estimates());
    vm.formatProperty().set(OutputFormat.PNG);
    assertThat(vm.estimateTotalProperty().get())
        .isEqualTo(M.get("ui.wallpapers.dialog.estimating"));
    drain();
    assertThat(vm.estimates()).hasSameSizeAs(auto);

    vm.formatProperty().set(OutputFormat.JPEG);
    vm.closeExport(); // la estimación en curso se descarta
    drain();
    assertThat(vm.estimateTotalProperty().get()).isEmpty();
    assertThat(vm.estimates()).isEmpty();
  }

  @Test
  void humanSizes() {
    assertThat(vm.humanSize(512)).isEqualTo("1 KB");
    assertThat(vm.humanSize(300 * 1024)).isEqualTo("300 KB");
    assertThat(vm.humanSize(3_565_158)).isEqualTo("3,4 MB");
  }
}
