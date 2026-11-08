package io.github.erenriquezp.rora.app.shell;

import javafx.application.Application;

/** Punto de entrada; existe aparte para no exigir JavaFX en el classpath de arranque. */
public final class Launcher {

  private Launcher() {}

  public static void main(String[] args) {
    Application.launch(StudioApp.class, args);
  }
}
