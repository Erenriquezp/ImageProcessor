package io.github.erenriquezp.rora.app.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.ai.api.AiEngine;
import io.github.erenriquezp.rora.ai.api.ModelInfo;
import io.github.erenriquezp.rora.ai.api.ModelSource;
import io.github.erenriquezp.rora.ai.store.ArtifactStore;
import io.github.erenriquezp.rora.app.diag.NativeFailures;
import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.session.EditSession;
import io.github.erenriquezp.rora.app.session.ManualExecutor;
import io.github.erenriquezp.rora.app.session.ManualScheduler;
import io.github.erenriquezp.rora.app.session.RenderCoordinator;
import io.github.erenriquezp.rora.app.session.RenderLane;
import io.github.erenriquezp.rora.app.session.ai.AiJobService;
import io.github.erenriquezp.rora.app.session.ai.AiLimits;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Si ONNX Runtime no carga por una biblioteca de Windows (el runtime de Visual C++), el aviso lo
 * dice y ofrece la página oficial; la app sigue sin IA. Una instalación rota da el mensaje
 * genérico.
 */
class VcRuntimeMessageTest {

  private static final Messages M = Messages.of(Locale.of("es"));
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
  private final List<String> browsed = new ArrayList<>();
  private EditSession<Img> session;
  private AiJobService jobs;
  private StudioViewModel<Img> vm;

  private static long ms(long m) {
    return Duration.ofMillis(m).toNanos();
  }

  private void build(AiEngine engine) throws Exception {
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
    vm =
        new StudioViewModel<>(
            new Env<>(session, M, time, publisher, background, io), img -> img, img -> img);
    vm.setBrowser(browsed::add);
    vm.viewer().setViewport(800, 600, 1);
    File f = dir.resolve("foto.png").toFile();
    ImageIO.write(new BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB), "png", f);
    vm.open(f);
    drain();
  }

  @AfterEach
  void tearDown() {
    if (vm != null) {
      vm.close();
      session.close();
      aiWorker.runAll();
    }
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

  private void enhanceFailingWith(AiFailure failure) throws Exception {
    build(
        (model, threads) -> {
          throw failure;
        });
    vm.enhance().start();
    drain();
  }

  @Test
  void aMissingNativeLibraryPointsToTheVisualCppRuntime() throws Exception {
    enhanceFailingWith(
        new AiFailure(
            AiFailure.Reason.ENGINE_UNAVAILABLE,
            "No se pudo cargar ONNX Runtime",
            new UnsatisfiedLinkError("onnxruntime.dll: Can't find dependent libraries")));
    NotificationsViewModel.Notice n = vm.notifications().notices().getLast();
    assertThat(n.kind()).isEqualTo(NotificationsViewModel.Kind.ERROR);
    assertThat(n.message()).isEqualTo(M.get("ui.error.vcRuntime"));
    assertThat(n.action().label()).isEqualTo(M.get("ui.error.vcRuntimeDownload"));
    assertThat(browsed).isEmpty();
    n.action().run().run();
    assertThat(browsed).containsExactly(NativeFailures.VC_REDIST_PAGE);
    assertThat(vm.hasImageProperty().get()).as("la app sigue con la foto").isTrue();
  }

  @Test
  void aBrokenInstallationKeepsTheGenericMessage() throws Exception {
    enhanceFailingWith(
        new AiFailure(AiFailure.Reason.ENGINE_UNAVAILABLE, "Falta la biblioteca", null));
    NotificationsViewModel.Notice n = vm.notifications().notices().getLast();
    assertThat(n.message()).contains(M.get("io.error.ai_unavailable"));
    assertThat(n.action()).isNull();
  }

  @Test
  void classification() {
    assertThat(
            NativeFailures.missingNativeLibrary(new RuntimeException(new UnsatisfiedLinkError())))
        .isTrue();
    assertThat(NativeFailures.missingNativeLibrary(new RuntimeException("otra"))).isFalse();
    assertThat(NativeFailures.missingNativeLibrary(null)).isFalse();
  }
}
