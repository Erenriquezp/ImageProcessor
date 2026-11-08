package io.github.erenriquezp.rora.app.shell;

import io.github.erenriquezp.rora.app.lab.LabContext;
import io.github.erenriquezp.rora.app.lab.LabTool;
import io.github.erenriquezp.rora.app.theme.Theme;
import java.util.List;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

/** Ventana del Laboratorio: una pestaña por herramienta. */
final class LabWindow {

  private LabWindow() {}

  static void open(Stage owner, List<LabTool> tools, LabContext context) {
    TabPane tabs = new TabPane();
    tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    for (LabTool tool : tools) {
      tabs.getTabs().add(new Tab(tool.title(), tool.view(context)));
    }
    Stage stage = new Stage();
    stage.initOwner(owner);
    stage.setTitle("Laboratorio");
    Scene scene = new Scene(tabs, 960, 640);
    Theme.apply(scene);
    stage.setScene(scene);
    stage.setOnHidden(e -> tools.forEach(LabTool::close));
    stage.show();
  }
}
