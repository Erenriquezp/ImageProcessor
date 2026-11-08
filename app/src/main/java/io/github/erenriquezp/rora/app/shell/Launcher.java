package io.github.erenriquezp.rora.app.shell;

import io.github.erenriquezp.rora.app.ImageProcessorApp;
import java.util.Arrays;
import javafx.application.Application;

/** Elige la interfaz: la nueva con {@code --ui=next} o {@code -Drora.ui=next}. */
public final class Launcher {

  private Launcher() {}

  static boolean wantsNewUi(String[] args, String property) {
    return "next".equals(property) || Arrays.asList(args).contains("--ui=next");
  }

  public static void main(String[] args) {
    if (wantsNewUi(args, System.getProperty("rora.ui"))) {
      Application.launch(StudioApp.class, args);
    } else {
      Application.launch(ImageProcessorApp.class, args);
    }
  }
}
