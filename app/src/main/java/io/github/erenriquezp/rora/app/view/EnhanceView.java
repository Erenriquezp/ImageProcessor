package io.github.erenriquezp.rora.app.view;

import atlantafx.base.theme.Styles;
import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.viewmodel.EnhanceViewModel;
import javafx.beans.value.ObservableStringValue;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Cajón Mejorar: solo cablea controles con su ViewModel. */
public final class EnhanceView {

  private EnhanceView() {}

  public static VBox build(EnhanceViewModel<?> vm, Messages m, Subscriptions subs) {
    Label title = new Label(m.get("ui.enhance.title"));
    title.getStyleClass().add("panel-title");

    Button action = new Button();
    action.getStyleClass().add(Styles.ACCENT);
    action.setMaxWidth(Double.MAX_VALUE);
    action.textProperty().bind(vm.actionProperty());
    action.disableProperty().bind(vm.canStartProperty().not());
    action.setOnAction(e -> vm.start());
    action.visibleProperty().bind(vm.runningProperty().not());
    action.managedProperty().bind(action.visibleProperty());

    Node intensity = Controls.of(vm.intensity(), m, subs);
    intensity.disableProperty().bind(vm.runningProperty());

    Label legal = new Label(m.get("ui.enhance.legal"), Icons.icon("fas-exclamation-triangle"));
    legal.getStyleClass().add("enhance-legal");
    legal.setWrapText(true);

    VBox body =
        new VBox(
            10,
            scaleRow(vm, m, subs),
            text(vm.dimensionsProperty(), "hint"),
            text(vm.limitProperty(), "enhance-limit"),
            intensity,
            text(vm.estimateProperty(), "hint"),
            text(vm.warningProperty(), "enhance-limit"),
            text(vm.noticeProperty(), "enhance-notice"),
            action,
            progressRow(vm, m),
            legal,
            advanced(vm, m));
    ScrollPane scroll = new ScrollPane(body);
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.getStyleClass().add("context-scroll");
    VBox.setVgrow(scroll, Priority.ALWAYS);
    VBox box = new VBox(title, scroll);
    box.getStyleClass().add("drawer");
    return box;
  }

  private static Node scaleRow(EnhanceViewModel<?> vm, Messages m, Subscriptions subs) {
    Label label = new Label(m.get("ui.enhance.scale"));
    label.getStyleClass().add("control-label");
    ToggleGroup group = new ToggleGroup();
    RadioButton x2 = scale(vm, m, group, EnhanceViewModel.X2);
    RadioButton x4 = scale(vm, m, group, EnhanceViewModel.X4);
    x2.disableProperty().bind(vm.runningProperty());
    x4.disableProperty().bind(vm.runningProperty().or(vm.x4AllowedProperty().not()));
    subs.add(
        vm.scaleProperty()
            .subscribe(s -> (EnhanceViewModel.X4.equals(s) ? x4 : x2).setSelected(true)));
    HBox options = new HBox(16, x2, x4);
    options.setAlignment(Pos.CENTER_LEFT);
    return new VBox(4, label, options);
  }

  private static RadioButton scale(
      EnhanceViewModel<?> vm, Messages m, ToggleGroup group, String value) {
    RadioButton b = new RadioButton(m.get("op.ai.enhance.scale." + value));
    b.setToggleGroup(group);
    b.setOnAction(e -> vm.setScale(value));
    return b;
  }

  private static Label text(ObservableStringValue value, String style) {
    Label l = new Label();
    l.getStyleClass().add(style);
    l.setWrapText(true);
    l.textProperty().bind(value);
    l.visibleProperty().bind(l.textProperty().isNotEmpty());
    l.managedProperty().bind(l.visibleProperty());
    return l;
  }

  private static Node progressRow(EnhanceViewModel<?> vm, Messages m) {
    ProgressBar bar = new ProgressBar();
    bar.setMaxWidth(Double.MAX_VALUE);
    bar.progressProperty().bind(vm.progressProperty());
    Label detail = new Label();
    detail.getStyleClass().add("hint");
    detail.textProperty().bind(vm.progressTextProperty());
    Button cancel = new Button(m.get("ui.action.cancel"));
    cancel.setOnAction(e -> vm.cancel());
    HBox line = new HBox(8, detail, cancel);
    line.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(detail, Priority.ALWAYS);
    detail.setMaxWidth(Double.MAX_VALUE);
    VBox box = new VBox(6, bar, line);
    box.visibleProperty().bind(vm.runningProperty());
    box.managedProperty().bind(box.visibleProperty());
    return box;
  }

  private static Node advanced(EnhanceViewModel<?> vm, Messages m) {
    Label label = new Label(m.get("ui.enhance.threads"));
    label.getStyleClass().add("control-label");
    Spinner<Integer> threads =
        new Spinner<>(1, Runtime.getRuntime().availableProcessors(), vm.threadsProperty().get(), 1);
    threads.setAccessibleText(m.get("ui.enhance.threads"));
    threads.valueProperty().addListener((o, was, now) -> vm.threadsProperty().set(now));
    threads.disableProperty().bind(vm.runningProperty());
    HBox line = new HBox(10, label, threads);
    line.setAlignment(Pos.CENTER_LEFT);
    TitledPane pane = new TitledPane(m.get("ui.enhance.advanced"), line);
    pane.setAnimated(false);
    pane.setExpanded(false);
    return pane;
  }
}
