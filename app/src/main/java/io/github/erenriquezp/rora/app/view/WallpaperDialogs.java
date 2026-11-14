package io.github.erenriquezp.rora.app.view;

import atlantafx.base.theme.Styles;
import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.viewmodel.WallpapersViewModel;
import io.github.erenriquezp.rora.core.io.ExportSettings.MetadataPolicy;
import io.github.erenriquezp.rora.wallpapers.plan.OutputFormat;
import java.io.File;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

/** Diálogos de Wallpapers: exportación y su informe. Los abre y cierra el estado del ViewModel. */
public final class WallpaperDialogs {

  private WallpaperDialogs() {}

  /**
   * Muestra los diálogos cuando el ViewModel lo pide.
   *
   * @param theme aplica el tema a cada escena nueva
   */
  public static void install(
      Window owner,
      WallpapersViewModel<?> vm,
      Messages m,
      Subscriptions subs,
      java.util.function.Consumer<Scene> theme) {
    Stage[] export = {null};
    Stage[] report = {null};
    subs.add(
        vm.dialogOpenProperty()
            .subscribe(
                open -> {
                  if (open && export[0] == null) {
                    export[0] =
                        stage(
                            owner,
                            m.get("ui.wallpapers.dialog.title"),
                            exportPane(owner, vm, m),
                            theme);
                    export[0].setOnHidden(e -> vm.closeExport());
                    export[0].show();
                  } else if (!open && export[0] != null) {
                    Stage s = export[0];
                    export[0] = null;
                    s.setOnHidden(null);
                    s.close();
                  }
                }));
    subs.add(
        vm.reportProperty()
            .subscribe(
                r -> {
                  if (report[0] != null) {
                    Stage s = report[0];
                    report[0] = null;
                    s.setOnHidden(null);
                    s.close();
                  }
                  if (r != null) {
                    report[0] =
                        stage(
                            owner,
                            m.get("ui.wallpapers.report.title"),
                            reportPane(vm, m, r),
                            theme);
                    report[0].setOnHidden(e -> vm.closeReport());
                    report[0].show();
                  }
                }));
  }

  private static Stage stage(
      Window owner, String title, Parent content, java.util.function.Consumer<Scene> theme) {
    Stage s = new Stage();
    s.initOwner(owner);
    s.initModality(Modality.WINDOW_MODAL);
    s.setTitle(title);
    Scene scene = new Scene(content);
    theme.accept(scene);
    s.setScene(scene);
    s.setResizable(false);
    return s;
  }

  private static Parent exportPane(Window owner, WallpapersViewModel<?> vm, Messages m) {
    TextField folder = new TextField();
    folder.textProperty().bindBidirectional(vm.folderProperty());
    folder.setPrefColumnCount(28);
    Button choose = new Button(m.get("ui.wallpapers.dialog.choose"));
    choose.setOnAction(
        e -> {
          DirectoryChooser c = new DirectoryChooser();
          File current = new File(vm.folderProperty().get());
          if (current.isDirectory()) {
            c.setInitialDirectory(current);
          }
          File f = c.showDialog(owner);
          if (f != null) {
            vm.folderProperty().set(f.getAbsolutePath());
          }
        });
    HBox folderRow = new HBox(6, folder, choose);
    HBox.setHgrow(folder, Priority.ALWAYS);

    TextField folderTemplate = new TextField();
    folderTemplate.textProperty().bindBidirectional(vm.folderTemplateProperty());
    TextField fileTemplate = new TextField();
    fileTemplate.textProperty().bindBidirectional(vm.fileTemplateProperty());
    Label tokens = new Label(m.get("ui.wallpapers.dialog.tokens"));
    tokens.getStyleClass().add("hint");
    tokens.setWrapText(true);
    Label preview = new Label();
    preview.getStyleClass().add("hint");
    preview.textProperty().bind(vm.namePreviewProperty());

    ComboBox<OutputFormat> format = new ComboBox<>();
    format.getItems().setAll(OutputFormat.values());
    format.setConverter(
        converter(
            f ->
                switch (f) {
                  case AUTO -> m.get("ui.wallpapers.dialog.format.auto");
                  case JPEG -> m.get("ui.wallpapers.dialog.format.jpeg");
                  case PNG -> m.get("ui.wallpapers.dialog.format.png");
                }));
    format.valueProperty().bindBidirectional(vm.formatProperty());
    Spinner<Integer> quality = new Spinner<>(1, 100, vm.qualityProperty().get());
    quality.setEditable(true);
    quality.valueProperty().addListener((o, a, b) -> vm.qualityProperty().set(b));
    ComboBox<MetadataPolicy> metadata = new ComboBox<>();
    metadata.getItems().setAll(MetadataPolicy.values());
    metadata.setConverter(
        converter(
            p ->
                switch (p) {
                  case KEEP -> m.get("ui.export.metadata.keep");
                  case REMOVE_LOCATION -> m.get("ui.export.metadata.location");
                  case REMOVE_ALL -> m.get("ui.export.metadata.all");
                }));
    metadata.valueProperty().bindBidirectional(vm.metadataProperty());

    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);
    int r = 0;
    grid.addRow(r++, label(m.get("ui.wallpapers.dialog.folder")), folderRow);
    grid.addRow(r++, label(m.get("ui.wallpapers.dialog.folderTemplate")), folderTemplate);
    grid.addRow(r++, label(m.get("ui.wallpapers.dialog.fileTemplate")), fileTemplate);
    grid.add(tokens, 1, r++);
    grid.add(preview, 1, r++);
    grid.addRow(r++, label(m.get("ui.wallpapers.dialog.format")), format);
    grid.addRow(r++, label(m.get("ui.wallpapers.dialog.quality")), quality);
    grid.addRow(r, label(m.get("ui.export.metadata")), metadata);

    Button cancel = new Button(m.get("ui.action.cancel"));
    cancel.setOnAction(e -> vm.closeExport());
    Button ok = new Button();
    ok.textProperty().bind(vm.exportLabelProperty());
    ok.getStyleClass().add(Styles.ACCENT);
    ok.setDefaultButton(true);
    ok.disableProperty().bind(vm.folderProperty().isEmpty());
    ok.setOnAction(e -> vm.confirmExport());
    Region gap = new Region();
    HBox.setHgrow(gap, Priority.ALWAYS);
    HBox buttons = new HBox(8, gap, cancel, ok);
    Label total = new Label();
    total.getStyleClass().add("hint");
    total.setWrapText(true);
    total.textProperty().bind(vm.estimateTotalProperty());
    VBox perTarget = new VBox(2);
    Runnable fillRows =
        () ->
            perTarget
                .getChildren()
                .setAll(
                    vm.estimates().stream()
                        .map(
                            t -> {
                              Label l = new Label(t);
                              l.getStyleClass().add("hint");
                              return l;
                            })
                        .toList());
    vm.estimates().addListener((javafx.beans.Observable o) -> fillRows.run());
    fillRows.run();
    ScrollPane rows = new ScrollPane(perTarget);
    rows.setFitToWidth(true);
    rows.setMaxHeight(110);
    rows.getStyleClass().add("estimate-rows");
    rows.visibleProperty().bind(javafx.beans.binding.Bindings.isNotEmpty(vm.estimates()));
    rows.managedProperty().bind(rows.visibleProperty());
    VBox estimate = new VBox(6, total, rows);
    VBox box = new VBox(16, grid, estimate, buttons);
    box.setPadding(new Insets(20));
    box.setPrefWidth(620);
    box.getStyleClass().add("dialog-pane");
    return box;
  }

  private static Parent reportPane(
      WallpapersViewModel<?> vm, Messages m, WallpapersViewModel.Report r) {
    Label summary = new Label(r.summary());
    summary.getStyleClass().add("panel-title");
    VBox failures = new VBox(4);
    for (String f : r.failures()) {
      Label l = new Label(f, Icons.icon("fas-times-circle"));
      l.getStyleClass().add("enhance-limit");
      l.setWrapText(true);
      failures.getChildren().add(l);
    }
    Button reveal = new Button(m.get("ui.action.reveal"));
    reveal.setOnAction(e -> vm.revealReport());
    reveal.setDisable(r.folder().isEmpty());
    Button close = new Button(m.get("ui.action.close"));
    close.getStyleClass().add(Styles.ACCENT);
    close.setDefaultButton(true);
    close.setOnAction(e -> vm.closeReport());
    Region gap = new Region();
    HBox.setHgrow(gap, Priority.ALWAYS);
    HBox buttons = new HBox(8, gap, reveal, close);
    buttons.setAlignment(Pos.CENTER_RIGHT);
    VBox box = new VBox(14, summary, failures, buttons);
    box.setPadding(new Insets(20));
    box.setPrefWidth(460);
    box.getStyleClass().add("dialog-pane");
    return box;
  }

  private static Label label(String text) {
    Label l = new Label(text);
    l.getStyleClass().add("control-label");
    return l;
  }

  private static <T> StringConverter<T> converter(java.util.function.Function<T, String> f) {
    return new StringConverter<>() {
      @Override
      public String toString(T value) {
        return value == null ? "" : f.apply(value);
      }

      @Override
      public T fromString(String s) {
        return null;
      }
    };
  }
}
