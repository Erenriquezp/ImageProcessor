package io.github.erenriquezp.rora.app.view;

import atlantafx.base.controls.ToggleSwitch;
import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.viewmodel.HistogramViewModel;
import io.github.erenriquezp.rora.core.image.LiveHistogram;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/** Histograma RGB + luminancia, con el porcentaje recortado en luces y sombras. */
public final class HistogramView extends VBox {

  private static final double HEIGHT = 92;
  private final Canvas canvas = new Canvas(276, HEIGHT);
  private LiveHistogram last;

  public HistogramView(HistogramViewModel<?> vm, Messages m, Subscriptions subs) {
    getStyleClass().add("histogram-box");
    Label title = new Label(m.get("ui.histogram.title"));
    title.getStyleClass().add("panel-title");
    Label approx = new Label(m.get("ui.histogram.approx"));
    approx.getStyleClass().add("section-note");
    subs.add(vm.approximateProperty().subscribe(on -> approx.setVisible(on)));
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox head = new HBox(title, spacer, approx);
    head.setAlignment(javafx.geometry.Pos.BASELINE_LEFT);

    StackPane box = new StackPane(canvas);
    box.getStyleClass().add("histogram-canvas-box");
    box.setMinHeight(HEIGHT);
    box.setPrefHeight(HEIGHT);
    box.widthProperty()
        .addListener(
            (obs, was, w) -> {
              canvas.setWidth(Math.max(1, w.doubleValue()));
              draw();
            });

    Label shadows = new Label();
    shadows.getStyleClass().add("hist-clip");
    Label highlights = new Label();
    highlights.getStyleClass().add("hist-clip");
    subs.add(vm.shadowsTextProperty().subscribe(shadows::setText));
    subs.add(vm.highlightsTextProperty().subscribe(highlights::setText));

    ToggleSwitch overlay = new ToggleSwitch(m.get("ui.histogram.overlay"));
    overlay.getStyleClass().add("hist-clip");
    overlay.selectedProperty().bindBidirectional(vm.overlayEnabledProperty());
    overlay.disableProperty().bind(vm.dataProperty().isNull());

    getChildren().addAll(head, box, shadows, highlights, overlay);
    subs.add(
        vm.dataProperty()
            .subscribe(
                h -> {
                  last = h;
                  draw();
                }));
  }

  private void draw() {
    GraphicsContext g = canvas.getGraphicsContext2D();
    double w = canvas.getWidth();
    g.clearRect(0, 0, w, HEIGHT);
    if (last == null || last.counted() == 0) {
      return;
    }
    int max = 1;
    // Los extremos (0 y 255) suelen ser picos que aplastarían el resto.
    for (int i = 1; i < 255; i++) {
      max = Math.max(max, Math.max(last.luma()[i], Math.max(last.red()[i], Math.max(last.green()[i], last.blue()[i]))));
    }
    channel(g, last.red(), max, w, Color.rgb(242, 90, 90, 0.55));
    channel(g, last.green(), max, w, Color.rgb(90, 214, 120, 0.55));
    channel(g, last.blue(), max, w, Color.rgb(90, 150, 255, 0.55));
    channel(g, last.luma(), max, w, Color.rgb(237, 237, 237, 0.32));
  }

  private void channel(GraphicsContext g, int[] bins, int max, double w, Color color) {
    g.setFill(color);
    double step = w / 256;
    for (int i = 0; i < 256; i++) {
      double v = Math.sqrt(Math.min(1.0, (double) bins[i] / max)) * (HEIGHT - 2);
      g.fillRect(i * step, HEIGHT - v, Math.ceil(step), v);
    }
  }
}
