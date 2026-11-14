package io.github.erenriquezp.rora.app.diag;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Carpetas de datos de la app. En Windows: {@code %LOCALAPPDATA%\RoraStudio} (caché de IA desde la
 * fase 5, registros y temporales) y {@code %APPDATA%\RoraStudio} (presets propios de Wallpapers).
 * El programa instalado vive aparte, en {@code %LOCALAPPDATA%\Programs\Rora Studio}.
 *
 * @param local datos locales de este equipo
 * @param roaming datos del usuario que Windows puede itinerar
 */
public record AppDirs(Path local, Path roaming) {

  /** Para este sistema, con la misma lógica que la caché de IA y los presets. */
  public static AppDirs fromSystem() {
    return of(
        System.getenv(), System.getProperty("os.name", ""), System.getProperty("user.home", "."));
  }

  public static AppDirs of(Map<String, String> env, String osName, String userHome) {
    String os = osName.toLowerCase(Locale.ROOT);
    if (os.contains("win")) {
      String local = env.get("LOCALAPPDATA");
      String roaming = env.get("APPDATA");
      Path l = blank(local) ? Path.of(userHome, "AppData", "Local") : Path.of(local);
      Path r = blank(roaming) ? Path.of(userHome, "AppData", "Roaming") : Path.of(roaming);
      return new AppDirs(l.resolve("RoraStudio"), r.resolve("RoraStudio"));
    }
    if (os.contains("mac")) {
      Path support = Path.of(userHome, "Library", "Application Support", "RoraStudio");
      return new AppDirs(support, support);
    }
    String state = env.get("XDG_STATE_HOME");
    String data = env.get("XDG_DATA_HOME");
    return new AppDirs(
        (blank(state) ? Path.of(userHome, ".local", "state") : Path.of(state))
            .resolve("rora-studio"),
        (blank(data) ? Path.of(userHome, ".local", "share") : Path.of(data))
            .resolve("rora-studio"));
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  public Path logs() {
    return local.resolve("logs");
  }

  public Path tmp() {
    return local.resolve("tmp");
  }

  /** Raíz de las cachés (la de IA cuelga de aquí: {@code cache\ai}). */
  public Path cache() {
    return local.resolve("cache");
  }
}
