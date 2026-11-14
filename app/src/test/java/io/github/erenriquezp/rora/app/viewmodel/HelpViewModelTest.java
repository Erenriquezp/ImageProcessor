package io.github.erenriquezp.rora.app.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.app.diag.AppDirs;
import io.github.erenriquezp.rora.app.diag.AppInfo;
import io.github.erenriquezp.rora.app.diag.GlobalErrors;
import io.github.erenriquezp.rora.app.diag.Redactor;
import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.session.ManualExecutor;
import io.github.erenriquezp.rora.app.session.ManualScheduler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Sin toolkit ni tiempo real: publicador y trabajo en segundo plano manuales. */
class HelpViewModelTest {

  private static final Messages M = Messages.of(Locale.of("es"));
  private static final Redactor R =
      new Redactor(
          new Redactor.Context(
              "C:\\Users\\Juan Pérez", null, null, null, "juanp", "PORTATIL-JUAN"));

  @TempDir Path dir;

  private final ManualExecutor publisher = new ManualExecutor();
  private final ManualExecutor background = new ManualExecutor();
  private final ManualScheduler time = new ManualScheduler();
  private final ObjectProperty<Prompt> prompt = new SimpleObjectProperty<>();
  private final List<String> copied = new ArrayList<>();
  private final List<Path> opened = new ArrayList<>();
  private final List<String> browsed = new ArrayList<>();
  private NotificationsViewModel<Object> notifications;
  private AppDirs dirs;
  private GlobalErrors errors;
  private HelpViewModel vm;

  @BeforeEach
  void setUp() {
    dirs = new AppDirs(dir.resolve("local"), dir.resolve("roaming"));
    notifications =
        new NotificationsViewModel<>(new Env<>(null, M, time, publisher, background, background));
    errors = new GlobalErrors(() -> 0);
    vm = create(Optional.empty());
  }

  private HelpViewModel create(Optional<Path> legal) {
    return new HelpViewModel(
        M,
        publisher,
        background,
        notifications,
        prompt,
        new HelpViewModel.Context(
            new AppInfo("0.1.0", "beta"),
            dirs,
            R,
            Optional.empty(),
            Optional.of(errors),
            true,
            legal,
            () -> ZonedDateTime.of(2026, 11, 14, 9, 0, 0, 0, ZoneOffset.UTC)),
        new HelpViewModel.Host() {
          @Override
          public void copyToClipboard(String text) {
            copied.add(text);
          }

          @Override
          public void openFolder(Path d) {
            opened.add(d);
          }

          @Override
          public void browse(String url) {
            browsed.add(url);
          }

          @Override
          public HelpViewModel.UiFacts uiFacts() {
            return new HelpViewModel.UiFacts(
                "es",
                "25.0.5",
                Optional.of(true),
                List.of("1920×1080 lógicos, escala 150 %"),
                Optional.of("6.000 × 4.000 px JPEG"),
                List.of("adjust.basic"),
                "realesr-general-x4v3 1, 4 hilos",
                1024);
          }
        });
  }

  private void drain() {
    while (background.runAll() + publisher.runAll() > 0) {
      // hasta que no quede nada
    }
  }

  private List<String> notices() {
    return notifications.notices().stream().map(NotificationsViewModel.Notice::message).toList();
  }

  @Test
  void theReportIsBuiltInTheBackgroundAndNothingIsCopiedUntilAsked() {
    errors.uncaughtException(
        Thread.currentThread(),
        new IllegalStateException("No se pudo abrir C:\\Users\\Juan Pérez\\Pictures\\boda.jpg"));
    vm.openReport();
    assertThat(vm.reportVisibleProperty().get()).isTrue();
    assertThat(vm.reportBusyProperty().get()).isTrue();
    assertThat(vm.reportTextProperty().get()).isEqualTo(M.get("ui.report.building"));
    drain();
    String text = vm.reportTextProperty().get();
    assertThat(vm.reportBusyProperty().get()).isFalse();
    assertThat(text)
        .contains("Rora Studio 0.1.0 (beta)")
        .contains("Pantalla 1: 1920×1080 lógicos, escala 150 %")
        .contains("Pasos activos: adjust.basic")
        .contains("%USERPROFILE%\\Pictures\\<foto>.jpg")
        .doesNotContain("Juan")
        .doesNotContain("boda");
    assertThat(copied).isEmpty();
    assertThat(browsed).isEmpty();
  }

  @Test
  void copyAndSaveUseTheEditedText() throws Exception {
    vm.openReport();
    vm.copyReport(); // aún construyéndose: no hace nada
    assertThat(copied).isEmpty();
    drain();
    vm.reportTextProperty().set("texto revisado por el usuario");
    vm.copyReport();
    assertThat(copied).containsExactly("texto revisado por el usuario");
    Path out = dir.resolve("informe ñ.txt");
    vm.saveReport(out);
    drain();
    assertThat(Files.readString(out, StandardCharsets.UTF_8))
        .isEqualTo("texto revisado por el usuario");
    assertThat(notices()).contains(M.get("ui.report.copied"), M.get("ui.report.saved"));
    assertThat(vm.defaultReportName()).isEqualTo("rora-diagnostico-0.1.0.txt");
  }

  @Test
  void clearingOnlyTouchesTemporariesRotatedLogsAndSeenCrashes() throws Exception {
    Path tmp = Files.createDirectories(dirs.tmp().resolve("sub"));
    Files.writeString(tmp.resolve("x.bin"), "x");
    Files.writeString(dirs.tmp().resolve(".lock"), "");
    Path logs = Files.createDirectories(dirs.logs());
    for (String n :
        List.of(
            "rora-0.log", "rora-1.log", "rora-4.log", "hs_err_pid9.log.visto", "hs_err_pid8.log")) {
      Files.writeString(logs.resolve(n), n);
    }
    Path ai = Files.createDirectories(dirs.cache().resolve("ai"));
    Files.writeString(ai.resolve("artefacto.bin"), "a");
    Path presets = Files.createDirectories(dirs.roaming().resolve("wallpapers"));
    Files.writeString(presets.resolve("mio.json"), "{}");

    vm.clearLogsAndTemporaries();
    assertThat(prompt.get()).isNotNull();
    prompt.get().choices().getLast().action().run(); // cancelar
    drain();
    assertThat(logs.resolve("rora-1.log")).exists();

    vm.clearLogsAndTemporaries();
    prompt.get().choices().getFirst().action().run(); // borrar
    drain();
    assertThat(prompt.get()).isNull();
    assertThat(tmp.resolve("x.bin")).doesNotExist();
    assertThat(dirs.tmp().resolve(".lock")).exists();
    assertThat(logs.resolve("rora-0.log")).exists();
    assertThat(logs.resolve("rora-1.log")).doesNotExist();
    assertThat(logs.resolve("rora-4.log")).doesNotExist();
    assertThat(logs.resolve("hs_err_pid9.log.visto")).doesNotExist();
    assertThat(logs.resolve("hs_err_pid8.log")).exists();
    assertThat(ai.resolve("artefacto.bin")).exists();
    assertThat(presets.resolve("mio.json")).exists();
    assertThat(notices()).contains(M.format("ui.clear.done", 4));
  }

  @Test
  void foldersAreCreatedAndOpened() {
    vm.openLogsFolder();
    vm.openDataFolder();
    drain();
    assertThat(opened).containsExactly(dirs.logs(), dirs.local());
    assertThat(dirs.logs()).isDirectory();
  }

  @Test
  void aPreviousNativeCrashIsOfferedOnceAndMarkedSeen() throws Exception {
    Path logs = Files.createDirectories(dirs.logs());
    Files.writeString(
        logs.resolve("hs_err_pid77.log"),
        "#\n#  EXCEPTION_ACCESS_VIOLATION (0xc0000005)\n# Problematic frame:\n# C  [onnxruntime.dll+0x1]\n#\n\n"
            + "USERNAME=juanp\n");
    vm.checkPreviousCrash();
    drain();
    assertThat(notices()).containsExactly(M.get("ui.crash.previous"));
    assertThat(logs.resolve("hs_err_pid77.log.visto")).exists();
    notifications.notices().getFirst().action().run().run();
    drain();
    assertThat(vm.reportTextProperty().get())
        .contains("Fallo nativo de la sesión anterior")
        .contains("onnxruntime.dll+0x1")
        .doesNotContain("juanp");
    vm.closeReport();
    vm.checkPreviousCrash();
    drain();
    assertThat(notices()).hasSize(1);
  }

  @Test
  void globalErrorsAskOrWarn() {
    vm.onError(new IllegalStateException("x"));
    assertThat(prompt.get().title()).isEqualTo(M.get("ui.error.unexpected.title"));
    prompt.get().choices().getFirst().action().run();
    assertThat(vm.reportVisibleProperty().get()).isTrue();
    vm.closeReport();
    vm.onError(new IllegalStateException("y"));
    prompt.get().choices().getLast().action().run();
    assertThat(prompt.get()).isNull();
    vm.onOutOfMemory(false);
    vm.onOutOfMemory(true);
    assertThat(notices())
        .containsExactly(M.get("ui.error.memoryGlobal"), M.get("ui.error.memoryRepeated"));
  }

  @Test
  void issuesLicensesAndAbout() throws Exception {
    vm.reportProblem();
    assertThat(browsed).containsExactly(HelpViewModel.ISSUES_URL);

    vm.openLicenses(); // sin carpeta legal: "Acerca de"
    assertThat(vm.aboutVisibleProperty().get()).isTrue();
    drain();
    assertThat(vm.licensesProperty().get()).contains("Licencias de terceros").contains("jdk25u");
    assertThat(vm.creditsProperty().get())
        .contains("Font Awesome Free 6.5.2")
        .contains("IBM Plex Sans");
    assertThat(vm.modelsProperty().get()).contains("realesr-general-x4v3");
    assertThat(vm.versionLine()).isEqualTo("Versión 0.1.0 (beta)");
    assertThat(vm.stackLine()).startsWith("Java " + Runtime.version().feature());
    vm.closeAbout();

    Path legal = Files.createDirectories(dir.resolve("legal"));
    create(Optional.of(legal)).openLicenses();
    assertThat(opened).containsExactly(legal);
    assertThat(vm.verboseAvailable()).isFalse();
  }
}
