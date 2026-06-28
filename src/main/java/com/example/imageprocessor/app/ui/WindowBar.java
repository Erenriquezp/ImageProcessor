package com.example.imageprocessor.app.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Barra de título personalizada para una ventana sin bordes
 * ({@code StageStyle.UNDECORATED}). Muestra icono + marca a la izquierda y los
 * controles minimizar / maximizar-restaurar / cerrar a la derecha.
 *
 * <p>Arrastrar la barra mueve la ventana; doble clic alterna maximizado.
 * El maximizado es manual (vía {@link Screen#getVisualBounds()}) para respetar
 * la barra de tareas, en vez de {@code stage.setMaximized()} que en ventanas
 * sin bordes puede cubrir toda la pantalla.
 */
public class WindowBar {

    private final HBox view;
    private final BooleanProperty maximized = new SimpleBooleanProperty(false);

    // Posición/tamaño previos al maximizar, para restaurar
    private double restoreX, restoreY, restoreW, restoreH;
    // Offset del cursor dentro de la ventana al iniciar el arrastre
    private double dragX, dragY;

    public WindowBar(Stage stage, String appName) {
        // Marca vectorial (el PNG de la app es una silueta negra que se pierde
        // sobre la barra oscura; aquí usamos un glifo con color de acento).
        FontIcon brandMark = new FontIcon("fas-camera-retro");
        brandMark.setIconSize(15);
        brandMark.getStyleClass().add("window-brand-icon");

        Label brand = new Label(appName);
        brand.getStyleClass().add("window-brand");

        HBox left = new HBox(9, brandMark, brand);
        left.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button minBtn   = controlButton("fas-minus",          "Minimizar");
        Button maxBtn   = controlButton("fas-window-maximize", "Maximizar");
        Button closeBtn = controlButton("fas-times",          "Cerrar");
        closeBtn.getStyleClass().add("window-close");

        FontIcon maxIcon = (FontIcon) maxBtn.getGraphic();
        maximized.addListener((o, was, is) ->
                maxIcon.setIconLiteral(is ? "fas-window-restore" : "fas-window-maximize"));

        minBtn.setOnAction(e -> stage.setIconified(true));
        maxBtn.setOnAction(e -> toggleMaximize(stage));
        closeBtn.setOnAction(e -> stage.close());

        HBox controls = new HBox(2, minBtn, maxBtn, closeBtn);
        controls.setAlignment(Pos.CENTER_RIGHT);

        view = new HBox(left, spacer, controls);
        view.setAlignment(Pos.CENTER_LEFT);
        view.setPadding(new Insets(0, 6, 0, 12));
        view.setMinHeight(34);
        view.setPrefHeight(34);
        view.getStyleClass().add("window-bar");

        // ── Arrastre para mover (deshabilitado mientras está maximizado) ──
        view.setOnMousePressed(e -> {
            dragX = e.getScreenX() - stage.getX();
            dragY = e.getScreenY() - stage.getY();
        });
        view.setOnMouseDragged(e -> {
            if (maximized.get()) return;
            stage.setX(e.getScreenX() - dragX);
            stage.setY(e.getScreenY() - dragY);
        });
        view.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                toggleMaximize(stage);
            }
        });
    }

    public HBox getView() { return view; }

    /** True mientras la ventana está maximizada (para deshabilitar el resize). */
    public BooleanProperty maximizedProperty() { return maximized; }

    private void toggleMaximize(Stage stage) {
        if (maximized.get()) {
            stage.setX(restoreX);
            stage.setY(restoreY);
            stage.setWidth(restoreW);
            stage.setHeight(restoreH);
            maximized.set(false);
        } else {
            restoreX = stage.getX();
            restoreY = stage.getY();
            restoreW = stage.getWidth();
            restoreH = stage.getHeight();
            Rectangle2D vb = Screen.getScreensForRectangle(stage.getX(), stage.getY(), 1, 1)
                    .stream().findFirst().orElse(Screen.getPrimary())
                    .getVisualBounds();
            stage.setX(vb.getMinX());
            stage.setY(vb.getMinY());
            stage.setWidth(vb.getWidth());
            stage.setHeight(vb.getHeight());
            maximized.set(true);
        }
    }

    private static Button controlButton(String iconLiteral, String tip) {
        Button b = new Button();
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(12);
        icon.getStyleClass().add("window-control-icon");
        b.setGraphic(icon);
        b.setTooltip(new Tooltip(tip));
        b.getStyleClass().add("window-control");
        return b;
    }
}
