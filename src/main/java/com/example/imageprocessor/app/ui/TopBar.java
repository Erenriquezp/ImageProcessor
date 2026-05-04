package com.example.imageprocessor.app.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Barra de herramientas superior.
 *
 * Responsabilidades:
 *  - Construye y expone los botones de acción global (Abrir, Guardar, Reset, Aplicar).
 *  - Embebe los controles de zoom (−, indicador, +, ajustar) recibidos como parámetros.
 *  - No contiene estado de la aplicación; se comunica sólo a través de callbacks.
 */
public class TopBar {

    private final HBox view;

    /**
     * @param onOpen        Callback para abrir imagen
     * @param onSave        Callback para guardar imagen
     * @param onReset       Callback para resetear imagen
     * @param onApply       Callback para aplicar el filtro seleccionado
     * @param onZoomIn      Callback para hacer zoom in en el panel activo
     * @param onZoomOut     Callback para hacer zoom out en el panel activo
     * @param onZoomFit     Callback para resetear el zoom a "ajustar a ventana"
     * @param zoomIndicator Label de porcentaje de zoom (propiedad de EditorPreviewPane)
     * @param compareToggle Checkbox "Mostrar original" (propiedad de ImageProcessorApp)
     */
    public TopBar(
            Runnable onOpen,
            Runnable onSave,
            Runnable onReset,
            Runnable onApply,
            Runnable onZoomIn,
            Runnable onZoomOut,
            Runnable onZoomFit,
            Label    zoomIndicator,
            CheckBox compareToggle) {

        // ── Action buttons ────────────────────────────────────────────────
        Button openButton  = createToolbarButton("Abrir",          "fas-folder-open",         "Abrir imagen");
        Button saveButton  = createToolbarButton("Guardar",        "fas-file-export",          "Guardar imagen");
        Button resetButton = createToolbarButton("Reset",          "fas-power-off",            "Restablecer imagen");
        Button applyButton = createToolbarButton("Aplicar filtro", "fas-play",                 "Aplicar filtro seleccionado");
        applyButton.getStyleClass().add("btn-primary");

        openButton .setOnAction(e -> onOpen.run());
        saveButton .setOnAction(e -> onSave.run());
        resetButton.setOnAction(e -> onReset.run());
        applyButton.setOnAction(e -> onApply.run());

        // ── Zoom controls ─────────────────────────────────────────────────
        Button zoomOutBtn = createIconButton("fas-search-minus",        "Alejar  (scroll ↓)");
        Button zoomInBtn  = createIconButton("fas-search-plus",         "Acercar (scroll ↑)");
        Button zoomFitBtn = createIconButton("fas-compress-arrows-alt", "Ajustar a ventana");
        zoomOutBtn.setOnAction(e -> onZoomOut.run());
        zoomInBtn .setOnAction(e -> onZoomIn.run());
        zoomFitBtn.setOnAction(e -> onZoomFit.run());

        zoomIndicator.getStyleClass().add("zoom-indicator");
        zoomIndicator.setTooltip(new Tooltip("Click para ajustar a ventana"));
        zoomIndicator.setOnMouseClicked(e -> onZoomFit.run());

        HBox zoomBox = new HBox(2, zoomOutBtn, zoomIndicator, zoomInBtn, zoomFitBtn);
        zoomBox.setAlignment(Pos.CENTER);
        zoomBox.getStyleClass().add("zoom-box");

        // ── Compare toggle ────────────────────────────────────────────────
        compareToggle.getStyleClass().add("top-toggle");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // ── Assemble ──────────────────────────────────────────────────────
        view = new HBox(10, openButton, saveButton, resetButton, applyButton,
                spacer, zoomBox, compareToggle);
        view.setPadding(new Insets(12));
        view.getStyleClass().add("top-bar");
    }

    public HBox getView() { return view; }

    // ── Private helpers ───────────────────────────────────────────────────

    private static Button createToolbarButton(String text, String iconLiteral, String tooltipText) {
        Button button = new Button(text);
        FontIcon icon = new FontIcon(iconLiteral);
        icon.getStyleClass().add("toolbar-icon");
        button.setGraphic(icon);
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setGraphicTextGap(8);
        button.setTooltip(new Tooltip(tooltipText));
        button.getStyleClass().add("toolbar-button");
        return button;
    }

    private static Button createIconButton(String iconLiteral, String tooltipText) {
        Button btn = new Button();
        FontIcon icon = new FontIcon(iconLiteral);
        icon.getStyleClass().add("toolbar-icon");
        btn.setGraphic(icon);
        btn.setTooltip(new Tooltip(tooltipText));
        btn.getStyleClass().addAll("toolbar-button", "zoom-btn");
        return btn;
    }
}

