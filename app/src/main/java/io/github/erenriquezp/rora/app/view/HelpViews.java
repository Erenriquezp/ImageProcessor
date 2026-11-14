package io.github.erenriquezp.rora.app.view;

import atlantafx.base.theme.Styles;
import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.viewmodel.HelpViewModel;
import java.io.File;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

/** Menú de ayuda, "Acerca de" e informe de diagnóstico. Los diálogos los abre el ViewModel. */
public final class HelpViews {

  private static final String ICON = "/io/github/erenriquezp/rora/app-icon.png";

  private HelpViews() {}

  /** Botón {@code ?} de la barra superior con el menú de ayuda. */
  public static MenuButton helpButton(HelpViewModel vm, Messages m, MenuItem updatesOrNull) {
    MenuButton b = new MenuButton();
    b.setGraphic(new FontIcon("fas-question-circle"));
    b.getStyleClass().addAll(Styles.BUTTON_ICON, "help-button");
    b.setTooltip(new Tooltip(m.get("ui.help.button")));
    b.setAccessibleText(m.get("ui.help.button"));

    MenuItem about = item(m.get("ui.help.about"), vm::showAbout);
    MenuItem report = item(m.get("ui.help.report"), vm::openReport);
    MenuItem logs = item(m.get("ui.help.logs"), vm::openLogsFolder);
    MenuItem data = item(m.get("ui.help.data"), vm::openDataFolder);
    MenuItem clear = item(m.get("ui.help.clear"), vm::clearLogsAndTemporaries);
    CheckMenuItem verbose = new CheckMenuItem(m.get("ui.help.verbose"));
    verbose.selectedProperty().bindBidirectional(vm.verboseProperty());
    verbose.setDisable(!vm.verboseAvailable());
    MenuItem licenses = item(m.get("ui.help.licenses"), vm::openLicenses);
    MenuItem issue = item(m.get("ui.help.issue"), vm::reportProblem);
    b.getItems().add(about);
    if (updatesOrNull != null) {
      b.getItems().add(updatesOrNull);
    }
    b.getItems()
        .addAll(
            report,
            new SeparatorMenuItem(),
            logs,
            data,
            clear,
            verbose,
            new SeparatorMenuItem(),
            licenses,
            issue);
    return b;
  }

  private static MenuItem item(String text, Runnable action) {
    MenuItem i = new MenuItem(text);
    i.setOnAction(e -> action.run());
    return i;
  }

  /** Abre y cierra "Acerca de" y el informe según el ViewModel. */
  public static void install(
      Window owner, HelpViewModel vm, Messages m, Subscriptions subs, Consumer<Scene> theme) {
    Stage[] about = {null};
    Stage[] report = {null};
    subs.add(
        vm.aboutVisibleProperty()
            .subscribe(
                open -> {
                  if (open && about[0] == null) {
                    about[0] = stage(owner, m.get("ui.about.title"), aboutPane(vm, m), theme);
                    about[0].setOnHidden(e -> vm.closeAbout());
                    about[0].show();
                  } else if (!open && about[0] != null) {
                    Stage s = about[0];
                    about[0] = null;
                    s.hide();
                  }
                }));
    subs.add(
        vm.reportVisibleProperty()
            .subscribe(
                open -> {
                  if (open && report[0] == null) {
                    Stage s = stage(owner, m.get("ui.report.title"), null, theme);
                    s.getScene().setRoot(reportPane(s, vm, m));
                    s.setResizable(true);
                    s.setOnHidden(e -> vm.closeReport());
                    report[0] = s;
                    s.show();
                  } else if (!open && report[0] != null) {
                    Stage s = report[0];
                    report[0] = null;
                    s.hide();
                  }
                }));
  }

  private static Stage stage(Window owner, String title, Parent content, Consumer<Scene> theme) {
    Stage s = new Stage();
    s.initOwner(owner);
    s.initModality(Modality.WINDOW_MODAL);
    s.setTitle(title);
    Scene scene = new Scene(content == null ? new VBox() : content);
    theme.accept(scene);
    s.setScene(scene);
    s.setResizable(false);
    return s;
  }

  private static Parent aboutPane(HelpViewModel vm, Messages m) {
    ImageView icon = new ImageView();
    var in = HelpViews.class.getResourceAsStream(ICON);
    if (in != null) {
      icon.setImage(new Image(in, 64, 64, true, true));
    }
    Label name = new Label(vm.appName());
    name.getStyleClass().add(Styles.TITLE_3);
    Label version = new Label(vm.versionLine());
    Label stack = new Label(vm.stackLine());
    stack.getStyleClass().add("hint");
    Label license = new Label(vm.licenseLine());
    license.getStyleClass().add("hint");
    VBox head = new VBox(4, name, version, stack, license);
    HBox top = new HBox(16, icon, head);
    top.setAlignment(Pos.CENTER_LEFT);

    TabPane tabs =
        new TabPane(
            tab(m.get("ui.about.credits"), vm.creditsProperty()),
            tab(m.get("ui.about.models"), vm.modelsProperty()),
            tab(m.get("ui.about.licenses"), vm.licensesProperty()));
    tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    tabs.setPrefSize(640, 320);
    VBox.setVgrow(tabs, Priority.ALWAYS);

    Button legal = new Button(m.get("ui.about.openLegal"));
    legal.setOnAction(e -> vm.openLicenses());
    Button close = new Button(m.get("ui.action.close"));
    close.setDefaultButton(true);
    close.setOnAction(e -> vm.closeAbout());
    Region gap = new Region();
    HBox.setHgrow(gap, Priority.ALWAYS);
    HBox buttons = new HBox(10, legal, gap, close);
    VBox box = new VBox(16, top, tabs, buttons);
    box.setPadding(new Insets(20));
    box.getStyleClass().add("about-dialog");
    return box;
  }

  private static Tab tab(String title, javafx.beans.value.ObservableStringValue text) {
    TextArea area = new TextArea();
    area.setEditable(false);
    area.setWrapText(true);
    area.textProperty().bind(text);
    area.getStyleClass().add("about-text");
    return new Tab(title, area);
  }

  private static Parent reportPane(Stage stage, HelpViewModel vm, Messages m) {
    Label note = new Label(m.get("ui.report.note"));
    note.setWrapText(true);
    note.getStyleClass().add("hint");
    TextArea text = new TextArea();
    text.textProperty().bindBidirectional(vm.reportTextProperty());
    text.getStyleClass().add("report-text");
    text.setPrefColumnCount(80);
    text.setPrefRowCount(24);
    text.editableProperty().bind(vm.reportBusyProperty().not());
    VBox.setVgrow(text, Priority.ALWAYS);
    ProgressIndicator busy = new ProgressIndicator();
    busy.setPrefSize(18, 18);
    busy.visibleProperty().bind(vm.reportBusyProperty());
    busy.managedProperty().bind(busy.visibleProperty());

    Button copy = new Button(m.get("ui.action.copy"), new FontIcon("fas-copy"));
    copy.disableProperty().bind(vm.reportBusyProperty());
    copy.setOnAction(e -> vm.copyReport());
    Button save = new Button(m.get("ui.report.save"), new FontIcon("fas-save"));
    save.disableProperty().bind(vm.reportBusyProperty());
    save.setOnAction(
        e -> {
          FileChooser c = new FileChooser();
          c.setTitle(m.get("ui.report.title"));
          c.setInitialFileName(vm.defaultReportName());
          c.getExtensionFilters().add(new FileChooser.ExtensionFilter("TXT", "*.txt"));
          File f = c.showSaveDialog(stage);
          if (f != null) {
            vm.saveReport(f.toPath());
          }
        });
    Button close = new Button(m.get("ui.action.close"));
    close.setCancelButton(true);
    close.setOnAction(e -> vm.closeReport());
    Region gap = new Region();
    HBox.setHgrow(gap, Priority.ALWAYS);
    HBox buttons = new HBox(10, copy, save, busy, gap, close);
    buttons.setAlignment(Pos.CENTER_LEFT);
    VBox box = new VBox(12, note, text, buttons);
    box.setPadding(new Insets(16));
    box.setPrefSize(760, 560);
    box.getStyleClass().add("report-dialog");
    return box;
  }
}
