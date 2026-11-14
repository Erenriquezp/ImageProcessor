package io.github.erenriquezp.rora.app.shell;

import io.github.erenriquezp.rora.app.diag.AppDirs;
import io.github.erenriquezp.rora.app.diag.AppInfo;
import io.github.erenriquezp.rora.app.diag.AppTemp;
import io.github.erenriquezp.rora.app.diag.Diagnostics;
import io.github.erenriquezp.rora.app.diag.GlobalErrors;
import io.github.erenriquezp.rora.app.diag.Logs;
import io.github.erenriquezp.rora.app.diag.Redactor;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Locale;
import javafx.application.Application;

/**
 * Punto de entrada; existe aparte para no exigir JavaFX en el classpath de arranque. Antes de la
 * interfaz prepara el directorio temporal propio, el manejador global de errores y el registro en
 * archivo, por este orden: si algo falla después, ya queda registrado.
 */
public final class Launcher {

  private static final System.Logger LOG = System.getLogger("rora.app");

  private Launcher() {}

  public static void main(String[] args) {
    boolean packaged = Diagnostics.packaged();
    AppDirs dirs = AppDirs.fromSystem();
    Redactor redactor = new Redactor(Redactor.Context.fromSystem());
    if (packaged) {
      prepareTemp(dirs);
    }
    GlobalErrors errors = GlobalErrors.installDefault();
    Logs logs = null;
    try {
      logs =
          Logs.install(
              dirs.logs(),
              redactor,
              packaged,
              Logs.levelFromProperty(System.getProperty("rora.log.level")),
              ZoneId.systemDefault(),
              Logs.LIMIT_BYTES,
              Logs.COUNT);
      if (packaged) {
        logs.redirectStandardStreams();
      }
    } catch (IOException | RuntimeException e) {
      // Sin registro en archivo la app funciona igual; el error va a la consola si la hay.
      LOG.log(System.Logger.Level.WARNING, "No se pudo abrir el registro en archivo", e);
    }
    Diagnostics.install(logs, errors);
    LOG.log(System.Logger.Level.INFO, sessionLine(AppInfo.current(), packaged));
    Application.launch(StudioApp.class, args);
  }

  /** {@code java.io.tmpdir} existe antes de que nadie lo use (ORT no carga sin él). */
  private static void prepareTemp(AppDirs dirs) {
    Path tmp = AppTemp.resolve(System.getProperty("java.io.tmpdir"), dirs);
    System.setProperty("java.io.tmpdir", tmp.toString());
    try {
      AppTemp.prepare(tmp);
    } catch (IOException e) {
      LOG.log(System.Logger.Level.WARNING, "No se pudo preparar el directorio temporal", e);
    }
  }

  /** Primera línea de cada sesión: versiones y equipo, sin nada personal. */
  static String sessionLine(AppInfo info, boolean packaged) {
    Runtime rt = Runtime.getRuntime();
    return String.format(
        Locale.ROOT,
        "Rora Studio %s · Java %s · %s %s %s · %d núcleos · heap máx. %d MB · empaquetada %s",
        info.displayVersion(),
        Runtime.version(),
        System.getProperty("os.name", "?"),
        System.getProperty("os.version", "?"),
        System.getProperty("os.arch", "?"),
        rt.availableProcessors(),
        rt.maxMemory() >> 20,
        packaged ? "sí" : "no");
  }
}
