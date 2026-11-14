package io.github.erenriquezp.rora.app.viewmodel;

import io.github.erenriquezp.rora.app.diag.AppDirs;
import io.github.erenriquezp.rora.app.diag.AppInfo;
import io.github.erenriquezp.rora.app.diag.DiagnosticReport;
import io.github.erenriquezp.rora.app.diag.GlobalErrors;
import io.github.erenriquezp.rora.app.diag.HsErrScanner;
import io.github.erenriquezp.rora.app.diag.Logs;
import io.github.erenriquezp.rora.app.diag.Notices;
import io.github.erenriquezp.rora.app.diag.Redactor;
import io.github.erenriquezp.rora.app.i18n.Messages;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Menú de ayuda: "Acerca de", informe de diagnóstico, carpetas de la app, limpieza, registro
 * detallado y la reacción a errores globales. Nada sale del equipo: el informe solo se copia o se
 * guarda cuando el usuario lo pide, y siempre el texto que ve en el editor.
 */
public final class HelpViewModel implements GlobalErrors.Listener {

  /** Página de incidencias del proyecto (se abre en el navegador del usuario). */
  public static final String ISSUES_URL = "https://github.com/Erenriquezp/ImageProcessor/issues";

  /** Lo que la vista aporta: portapapeles, explorador, navegador y datos de la interfaz. */
  public interface Host {
    void copyToClipboard(String text);

    void openFolder(Path dir);

    void browse(String url);

    /** Se llama en el hilo de la interfaz. */
    UiFacts uiFacts();
  }

  /** Datos que solo conoce la interfaz (pantallas, aceleración, imagen y receta). */
  public record UiFacts(
      String uiLocale,
      String javafx,
      Optional<Boolean> hardwareAcceleration,
      List<String> screens,
      Optional<String> image,
      List<String> recipeSteps,
      String ai,
      long cacheBytes) {}

  /** Dependencias de proceso; en tests, con directorios temporales y sin registro. */
  public record Context(
      AppInfo info,
      AppDirs dirs,
      Redactor redactor,
      Optional<Logs> logs,
      Optional<GlobalErrors> errors,
      boolean packaged,
      Optional<Path> installedLegal,
      Supplier<ZonedDateTime> now) {}

  private final Messages m;
  private final Executor publisher;
  private final Executor background;
  private final NotificationsViewModel<?> notifications;
  private final ObjectProperty<Prompt> prompt;
  private final Context ctx;
  private final Host host;

  private final BooleanProperty aboutVisible = new SimpleBooleanProperty(false);
  private final StringProperty credits = new SimpleStringProperty("");
  private final StringProperty models = new SimpleStringProperty("");
  private final StringProperty licenses = new SimpleStringProperty("");
  private final BooleanProperty reportVisible = new SimpleBooleanProperty(false);
  private final BooleanProperty reportBusy = new SimpleBooleanProperty(false);
  private final StringProperty reportText = new SimpleStringProperty("");
  private final BooleanProperty verbose = new SimpleBooleanProperty(false);
  private Optional<String> pendingHsErr = Optional.empty();
  private long reportGeneration;

  public HelpViewModel(
      Messages messages,
      Executor publisher,
      Executor background,
      NotificationsViewModel<?> notifications,
      ObjectProperty<Prompt> prompt,
      Context ctx,
      Host host) {
    this.m = messages;
    this.publisher = publisher;
    this.background = background;
    this.notifications = notifications;
    this.prompt = prompt;
    this.ctx = ctx;
    this.host = host;
    verbose.addListener((o, a, b) -> ctx.logs().ifPresent(l -> l.setVerbose(b)));
  }

  // ── Acerca de ─────────────────────────────────────────────────────────────

  public String appName() {
    return m.get("ui.app.title");
  }

  public String versionLine() {
    return m.format("ui.about.version", ctx.info().displayVersion());
  }

  public String stackLine() {
    return m.format(
        "ui.about.stack",
        Runtime.version().feature()
            + "."
            + Runtime.version().interim()
            + "."
            + Runtime.version().update(),
        Notices.javafxVersion().orElse("?"),
        Notices.moduleVersion("com.microsoft.onnxruntime").orElse("1.31.0"));
  }

  public String licenseLine() {
    return m.get("ui.about.license");
  }

  public BooleanProperty aboutVisibleProperty() {
    return aboutVisible;
  }

  public StringProperty creditsProperty() {
    return credits;
  }

  public StringProperty modelsProperty() {
    return models;
  }

  public StringProperty licensesProperty() {
    return licenses;
  }

  /** Muestra "Acerca de"; los textos se leen en segundo plano la primera vez. */
  public void showAbout() {
    aboutVisible.set(true);
    if (!licenses.get().isEmpty()) {
      return;
    }
    background.execute(
        () -> {
          String c = credits();
          String md = Notices.text(Notices.MODELS);
          String lic = Notices.text(Notices.THIRD_PARTY) + "\n\n" + Notices.text(Notices.SOURCE);
          publisher.execute(
              () -> {
                credits.set(c);
                models.set(md);
                licenses.set(lic);
              });
        });
  }

  static String credits() {
    return """
        IBM Plex Sans — SIL Open Font License 1.1 (IBM Corp.)
        Font Awesome Free 6.5.2 — iconos CC BY 4.0, fuentes SIL OFL 1.1, código MIT (Fonticons, Inc.)
        AtlantaFX 2.1.0 — MIT · Ikonli 12.4.0 — Apache-2.0
        TwelveMonkeys ImageIO 3.15.3 — BSD-3-Clause
        ONNX Runtime 1.31.0 — MIT (Microsoft)
        OpenJFX 25 y Eclipse Temurin 25 — GPLv2 con Classpath Exception
        Real-ESRGAN realesr-general-x4v3 — BSD-3-Clause (Xintao Wang)
        YuNet (OpenCV Zoo) — MIT (Shiqi Yu)
        """;
  }

  public void closeAbout() {
    aboutVisible.set(false);
  }

  /** Carpeta {@code legal} de la instalación; sin ella (desarrollo), la pestaña de licencias. */
  public void openLicenses() {
    Optional<Path> legal = ctx.installedLegal().filter(Files::isDirectory);
    if (legal.isPresent()) {
      host.openFolder(legal.get());
    } else {
      showAbout();
    }
  }

  // ── Informe de diagnóstico ────────────────────────────────────────────────

  public BooleanProperty reportVisibleProperty() {
    return reportVisible;
  }

  public BooleanProperty reportBusyProperty() {
    return reportBusy;
  }

  /** El texto del editor: lo que se copia o se guarda es exactamente esto. */
  public StringProperty reportTextProperty() {
    return reportText;
  }

  /** Abre el informe; se construye en segundo plano. */
  public void openReport() {
    UiFacts ui = host.uiFacts();
    long generation = ++reportGeneration;
    Optional<String> hsErr = pendingHsErr;
    reportBusy.set(true);
    reportText.set(m.get("ui.report.building"));
    reportVisible.set(true);
    background.execute(
        () -> {
          DiagnosticReport.SystemFacts s = DiagnosticReport.system();
          String tail =
              ctx.logs()
                  .map(l -> l.tail(DiagnosticReport.LOG_LINES, DiagnosticReport.LOG_BYTES))
                  .orElse("");
          String text =
              DiagnosticReport.build(
                  new DiagnosticReport.Facts(
                      ctx.info(),
                      ctx.packaged(),
                      ui.uiLocale(),
                      s.java(),
                      s.jvmFlags(),
                      ui.javafx(),
                      ui.hardwareAcceleration(),
                      s.os(),
                      s.logicalCpus(),
                      s.cpuId(),
                      s.physicalTotal(),
                      s.physicalFree(),
                      s.heapMax(),
                      s.heapUsed(),
                      ui.cacheBytes(),
                      ui.screens(),
                      ui.ai(),
                      ui.image(),
                      ui.recipeSteps(),
                      ctx.errors().flatMap(GlobalErrors::lastError),
                      hsErr,
                      tail,
                      ctx.now().get()),
                  ctx.redactor());
          publisher.execute(
              () -> {
                if (generation == reportGeneration) {
                  reportText.set(text);
                  reportBusy.set(false);
                }
              });
        });
  }

  public void copyReport() {
    if (reportBusy.get()) {
      return;
    }
    host.copyToClipboard(reportText.get());
    notifications.info(m.get("ui.report.copied"));
  }

  /** Nombre propuesto al guardar. */
  public String defaultReportName() {
    String v = ctx.info().numericVersion();
    return "rora-diagnostico-" + (v.isEmpty() ? "dev" : v) + ".txt";
  }

  /** Guarda el texto del editor en {@code file} (elegido por el usuario). */
  public void saveReport(Path file) {
    if (file == null || reportBusy.get()) {
      return;
    }
    String text = reportText.get();
    background.execute(
        () -> {
          try {
            Files.writeString(file, text, StandardCharsets.UTF_8);
            publisher.execute(() -> notifications.info(m.get("ui.report.saved")));
          } catch (IOException | RuntimeException e) {
            publisher.execute(
                () ->
                    notifications.error(
                        m.get("ui.report.saveFailed"),
                        ctx.redactor().redact(String.valueOf(e.getMessage()))));
          }
        });
  }

  public void closeReport() {
    reportVisible.set(false);
    pendingHsErr = Optional.empty();
  }

  // ── Carpetas, limpieza y registro ─────────────────────────────────────────

  public void openLogsFolder() {
    openCreating(ctx.dirs().logs());
  }

  public void openDataFolder() {
    openCreating(ctx.dirs().local());
  }

  private void openCreating(Path dir) {
    background.execute(
        () -> {
          try {
            Files.createDirectories(dir);
          } catch (IOException e) {
            // Si no se puede crear, el explorador mostrará el error.
          }
          publisher.execute(() -> host.openFolder(dir));
        });
  }

  /** Pregunta y borra temporales, registros antiguos y fallos vistos. Nada más. */
  public void clearLogsAndTemporaries() {
    prompt.set(
        new Prompt(
            m.get("ui.clear.title"),
            m.get("ui.clear.body"),
            List.of(
                new Prompt.Choice(
                    m.get("ui.clear.confirm"),
                    true,
                    () -> {
                      prompt.set(null);
                      background.execute(
                          () -> {
                            int n = clear(ctx.dirs());
                            publisher.execute(
                                () -> notifications.info(m.format("ui.clear.done", n)));
                          });
                    }),
                new Prompt.Choice(m.get("ui.action.cancel"), false, () -> prompt.set(null)))));
  }

  /**
   * Borra el contenido de {@code tmp} (salvo su bloqueo), los registros rotados y los {@code
   * hs_err} vistos. El registro en uso, la caché de IA, los presets y las preferencias no se tocan.
   *
   * @return archivos borrados
   */
  static int clear(AppDirs dirs) {
    int n = 0;
    n += deleteMatching(dirs.tmp(), name -> !name.equals(".lock"));
    n +=
        deleteMatching(
            dirs.logs(),
            name ->
                name.matches("rora-[1-9]\\d*\\.log")
                    || name.matches(
                        "hs_err_pid\\d+\\.log" + HsErrScanner.SEEN.replace(".", "\\.")));
    return n;
  }

  private static int deleteMatching(Path dir, java.util.function.Predicate<String> keep) {
    if (!Files.isDirectory(dir)) {
      return 0;
    }
    int n = 0;
    try (Stream<Path> s = Files.walk(dir)) {
      List<Path> files =
          s.filter(p -> !p.equals(dir))
              .sorted((a, b) -> b.getNameCount() - a.getNameCount())
              .toList();
      for (Path p : files) {
        if (!p.toAbsolutePath().normalize().startsWith(dir.toAbsolutePath().normalize())) {
          continue;
        }
        if (Files.isRegularFile(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)
            && keep.test(p.getFileName().toString())) {
          try {
            Files.delete(p);
            n++;
          } catch (IOException e) {
            // En uso por otra instancia: se queda.
          }
        } else if (Files.isDirectory(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
          try {
            Files.delete(p); // solo si quedó vacía
          } catch (IOException e) {
            // no vacía o en uso
          }
        }
      }
    } catch (IOException e) {
      return n;
    }
    return n;
  }

  public BooleanProperty verboseProperty() {
    return verbose;
  }

  /** Sin registro en archivo (desarrollo) no hay nada que detallar. */
  public boolean verboseAvailable() {
    return ctx.logs().isPresent();
  }

  public void reportProblem() {
    host.browse(ISSUES_URL);
  }

  // ── Fallos ────────────────────────────────────────────────────────────────

  /** Al arrancar: si la sesión anterior murió por un fallo nativo, lo ofrece en el informe. */
  public void checkPreviousCrash() {
    background.execute(
        () -> {
          HsErrScanner scanner = new HsErrScanner(ctx.dirs().logs(), ctx.redactor());
          Optional<Path> file = scanner.newestUnseen();
          if (file.isEmpty()) {
            return;
          }
          String summary = scanner.summary(file.get());
          try {
            scanner.markSeen(file.get());
          } catch (IOException e) {
            // se volverá a ofrecer en el próximo arranque
          }
          publisher.execute(
              () ->
                  notifications.info(
                      m.get("ui.crash.previous"),
                      new NotificationsViewModel.Action(
                          m.get("ui.crash.view"),
                          () -> {
                            pendingHsErr = Optional.of(summary);
                            openReport();
                          })));
        });
  }

  @Override
  public void onError(Throwable error) {
    prompt.set(
        new Prompt(
            m.get("ui.error.unexpected.title"),
            m.get("ui.error.unexpected.body"),
            List.of(
                new Prompt.Choice(
                    m.get("ui.error.unexpected.report"),
                    true,
                    () -> {
                      prompt.set(null);
                      openReport();
                    }),
                new Prompt.Choice(
                    m.get("ui.error.unexpected.continue"), false, () -> prompt.set(null)))));
  }

  @Override
  public void onOutOfMemory(boolean repeated) {
    notifications.warning(
        repeated ? m.get("ui.error.memoryRepeated") : m.get("ui.error.memoryGlobal"));
  }
}
