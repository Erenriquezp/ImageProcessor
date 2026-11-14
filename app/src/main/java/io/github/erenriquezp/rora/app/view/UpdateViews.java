package io.github.erenriquezp.rora.app.view;

import atlantafx.base.theme.Styles;
import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.viewmodel.UpdateViewModel;
import io.github.erenriquezp.rora.app.viewmodel.UpdateViewModel.State;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

/** Entrada "Buscar actualizaciones" del menú de ayuda y su diálogo. */
public final class UpdateViews {

  private UpdateViews() {}

  /** {@code null} si la comprobación está desactivada. */
  public static MenuItem menuItem(UpdateViewModel vm, Messages m) {
    if (!vm.enabled()) {
      return null;
    }
    MenuItem i = new MenuItem(m.get("ui.help.updates"));
    i.setOnAction(e -> vm.check());
    return i;
  }

  public static void install(
      Window owner, UpdateViewModel vm, Messages m, Subscriptions subs, Consumer<Scene> theme) {
    Stage[] dialog = {null};
    subs.add(
        vm.visibleProperty()
            .subscribe(
                open -> {
                  if (open && dialog[0] == null) {
                    Stage s = new Stage();
                    s.initOwner(owner);
                    s.initModality(Modality.WINDOW_MODAL);
                    s.setTitle(m.get("ui.update.title"));
                    Scene scene = new Scene(pane(vm, m));
                    theme.accept(scene);
                    s.setScene(scene);
                    s.setResizable(false);
                    // El resultado llega después de abrir: el diálogo crece para mostrarlo entero.
                    javafx.beans.value.ChangeListener<String> fit =
                        (o, a, b) -> javafx.application.Platform.runLater(s::sizeToScene);
                    vm.messageProperty().addListener(fit);
                    s.setOnHidden(
                        e -> {
                          vm.messageProperty().removeListener(fit);
                          vm.close();
                        });
                    dialog[0] = s;
                    s.show();
                  } else if (!open && dialog[0] != null) {
                    Stage s = dialog[0];
                    dialog[0] = null;
                    s.hide();
                  }
                }));
  }

  private static Parent pane(UpdateViewModel vm, Messages m) {
    ProgressIndicator busy = new ProgressIndicator();
    busy.setPrefSize(20, 20);
    busy.visibleProperty().bind(vm.stateProperty().isEqualTo(State.CHECKING));
    busy.managedProperty().bind(busy.visibleProperty());
    FontIcon warn = new FontIcon("fas-exclamation-triangle");
    warn.getStyleClass().add("update-warn");
    warn.visibleProperty()
        .bind(vm.majorChangeProperty().or(vm.stateProperty().isEqualTo(State.FAILED)));
    warn.managedProperty().bind(warn.visibleProperty());
    Label text = new Label();
    text.textProperty().bind(vm.messageProperty());
    text.setWrapText(true);
    text.setPrefWidth(420);
    text.setMinHeight(Region.USE_PREF_SIZE);
    HBox.setHgrow(text, Priority.ALWAYS);
    HBox top = new HBox(12, busy, warn, text);
    top.setAlignment(Pos.CENTER_LEFT);

    Button open = new Button(m.get("ui.update.open"));
    open.getStyleClass().add(Styles.ACCENT);
    open.setOnAction(e -> vm.openRelease());
    open.visibleProperty().bind(vm.stateProperty().isEqualTo(State.NEW_VERSION));
    open.managedProperty().bind(open.visibleProperty());
    Button releases = new Button(m.get("ui.update.releases"));
    releases.setOnAction(e -> vm.openReleasesPage());
    releases.visibleProperty().bind(vm.stateProperty().isEqualTo(State.FAILED));
    releases.managedProperty().bind(releases.visibleProperty());
    Button close = new Button(m.get("ui.action.close"));
    close.setCancelButton(true);
    close.setOnAction(e -> vm.close());
    Region gap = new Region();
    HBox.setHgrow(gap, Priority.ALWAYS);
    HBox buttons = new HBox(10, open, releases, gap, close);
    VBox box = new VBox(20, top, buttons);
    box.setPadding(new Insets(20));
    box.setMinWidth(480);
    box.getStyleClass().add("update-dialog");
    return box;
  }
}
