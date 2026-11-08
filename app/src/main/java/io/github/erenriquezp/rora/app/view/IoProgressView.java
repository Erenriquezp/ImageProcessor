package io.github.erenriquezp.rora.app.view;

import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.viewmodel.IoProgressViewModel;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/** Barra de progreso de abrir y exportar, con su botón de cancelar. */
public final class IoProgressView extends HBox {

  public IoProgressView(IoProgressViewModel<?> vm, Messages m) {
    getStyleClass().add("io-progress");
    setAlignment(Pos.CENTER_LEFT);
    setSpacing(12);
    setMaxSize(420, Region.USE_PREF_SIZE);
    Label title = new Label();
    title.textProperty().bind(vm.titleProperty());
    title.setMinWidth(0);
    HBox.setHgrow(title, Priority.ALWAYS);
    title.setMaxWidth(Double.MAX_VALUE);
    ProgressBar bar = new ProgressBar();
    bar.setPrefWidth(120);
    bar.progressProperty().bind(vm.fractionProperty());
    Button cancel = new Button(m.get("ui.action.cancel"));
    cancel.setOnAction(e -> vm.cancel());
    getChildren().addAll(title, bar, cancel);
    visibleProperty().bind(vm.visibleProperty());
    managedProperty().bind(vm.visibleProperty());
  }
}
