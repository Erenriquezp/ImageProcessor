package io.github.erenriquezp.rora.app.diag;

import java.util.Optional;

/**
 * Lo que {@code Launcher} instala para todo el proceso (registro en archivo y manejador global).
 * Los tests y las herramientas que arrancan la interfaz sin {@code Launcher} lo ven vacío.
 */
public final class Diagnostics {

  private static volatile Logs logs;
  private static volatile GlobalErrors errors;

  private Diagnostics() {}

  public static void install(Logs installedLogs, GlobalErrors installedErrors) {
    logs = installedLogs;
    errors = installedErrors;
  }

  public static Optional<Logs> logs() {
    return Optional.ofNullable(logs);
  }

  public static Optional<GlobalErrors> errors() {
    return Optional.ofNullable(errors);
  }

  /** {@code -Drora.packaged=true}: la opción la pone el lanzador de la versión instalada. */
  public static boolean packaged() {
    return Boolean.getBoolean("rora.packaged");
  }
}
