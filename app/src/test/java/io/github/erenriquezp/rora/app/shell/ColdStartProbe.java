package io.github.erenriquezp.rora.app.shell;

import io.github.erenriquezp.rora.app.ImageProcessorApp;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/** Herramienta de desarrollo: mide el arranque en frío de la UI antigua o la nueva. */
public final class ColdStartProbe {

  private ColdStartProbe() {}

  private static void report(String which) {
    Platform.runLater(
        () ->
            Platform.runLater(
                () -> {
                  long ms =
                      System.currentTimeMillis()
                          - ProcessHandle.current().info().startInstant().orElseThrow().toEpochMilli();
                  System.out.println("COLD_START " + which + " " + ms);
                  System.exit(0);
                }));
  }

  /** UI antigua. */
  public static class OldUi extends ImageProcessorApp {
    @Override
    public void start(Stage stage) {
      super.start(stage);
      report("old");
    }
  }

  /** UI nueva. */
  public static class NewUi extends StudioApp {
    @Override
    protected LayoutStore layoutStore() {
      return LayoutStore.inMemory();
    }

    @Override
    public void start(Stage stage) {
      super.start(stage);
      report("new");
    }
  }

  public static void main(String[] args) {
    Application.launch(args[0].equals("old") ? OldUi.class : NewUi.class, args);
  }
}
