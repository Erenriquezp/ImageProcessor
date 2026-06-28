package com.example.imageprocessor.app.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.Locale;

/**
 * Selector de color propio que reemplaza el {@code ColorPicker} nativo de
 * JavaFX y su rejilla web-safe (que rompía el tema oscuro).
 *
 * <p>Un botón-muestra abre un popover oscuro con:
 * <ul>
 *   <li>cuadrado Saturación/Brillo arrastrable,</li>
 *   <li>barra de tono (Hue),</li>
 *   <li>campo HEX editable,</li>
 *   <li>fila de swatches curados.</li>
 * </ul>
 *
 * El estado autoritativo es HSB (evita la pérdida de precisión de convertir
 * {@link Color} ↔ HSB en cada interacción, p.ej. con grises donde el tono es
 * indefinido). El popover hereda el stylesheet de la escena dueña.
 */
public class ColorChooser {

    private static final double SB_W = 224, SB_H = 150, HUE_W = 224, HUE_H = 14;
    private static final double SB_R = 6;   // radio del thumb del cuadrado

    /** Swatches curados — alineados con la paleta del tema, no web-safe. */
    private static final List<String> PRESETS = List.of(
            "#FFFFFF", "#C9CCD1", "#8A8F98", "#4A8FD6", "#5AA0E0", "#4ACC88",
            "#E8913A", "#E84A8F", "#9A6ADC", "#E0C341", "#E05050", "#1A1A1E");

    private final ObjectProperty<Color> value = new SimpleObjectProperty<>();

    // HSB autoritativo
    private double hue, sat, bri;
    private boolean updating = false;   // evita re-derivar HSB cuando el cambio salió de aquí

    // Botón-disparador (parece un campo)
    private final HBox   button   = new HBox(8);
    private final Region swatch   = new Region();
    private final Label  hexLabel = new Label();

    // Popover
    private final Popup     popup    = new Popup();
    private final Pane      sbArea   = new Pane();
    private final Region    sbThumb  = new Region();
    private final Pane      hueArea  = new Pane();
    private final Region    hueThumb = new Region();
    private final TextField hexField = new TextField();

    public ColorChooser(Color initial) {
        setHsbFrom(initial);

        // ── Botón ──
        swatch.getStyleClass().add("cc-swatch");
        sizeOf(swatch, 22, 16);
        hexLabel.getStyleClass().add("cc-hex-label");

        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);

        FontIcon chevron = new FontIcon("fas-chevron-down");
        chevron.getStyleClass().add("cc-chevron");
        chevron.setIconSize(9);

        button.getStyleClass().add("cc-button");
        button.setAlignment(Pos.CENTER_LEFT);
        button.getChildren().setAll(swatch, hexLabel, grow, chevron);
        button.setOnMouseClicked(e -> togglePopup());

        buildPopup();

        // Estado inicial del property + UI
        updating = true;
        value.set(Color.hsb(hue, sat, bri));
        updating = false;
        refreshAll();

        // Cambios externos (setValue / reset) re-sincronizan el HSB interno
        value.addListener((o, ov, nv) -> {
            if (!updating && nv != null) setHsbFrom(nv);
            refreshAll();
        });
    }

    // ── API pública (drop-in para el antiguo ColorPicker) ──────────────────
    public Region getView()                  { return button; }
    public Color  getValue()                 { return value.get(); }
    public ObjectProperty<Color> valueProperty() { return value; }

    public void setValue(Color c) {
        if (c == null) return;
        setHsbFrom(c);
        updating = true;
        value.set(Color.hsb(hue, sat, bri));
        updating = false;
        refreshAll();
    }

    // ── Construcción del popover ───────────────────────────────────────────
    private void buildPopup() {
        // Cuadrado Saturación/Brillo
        sbArea.getStyleClass().add("cc-sb-area");
        sizeOf(sbArea, SB_W, SB_H);
        sbThumb.getStyleClass().add("cc-thumb");
        sbThumb.setMouseTransparent(true);
        sizeOf(sbThumb, SB_R * 2, SB_R * 2);
        sbArea.getChildren().add(sbThumb);
        sbArea.setOnMousePressed(this::handleSb);
        sbArea.setOnMouseDragged(this::handleSb);

        // Barra de tono
        hueArea.getStyleClass().add("cc-hue-area");
        sizeOf(hueArea, HUE_W, HUE_H);
        hueThumb.getStyleClass().add("cc-hue-thumb");
        hueThumb.setMouseTransparent(true);
        sizeOf(hueThumb, 6, HUE_H + 4);
        hueThumb.setLayoutY(-2);
        hueArea.getChildren().add(hueThumb);
        hueArea.setOnMousePressed(this::handleHue);
        hueArea.setOnMouseDragged(this::handleHue);

        // Campo HEX
        Label hexCaption = new Label("HEX");
        hexCaption.getStyleClass().add("cc-field-caption");
        hexField.getStyleClass().add("cc-hex-field");
        hexField.setPrefWidth(94);
        hexField.setOnAction(e -> applyHex());
        hexField.focusedProperty().addListener((o, was, is) -> { if (!is) applyHex(); });
        HBox hexRow = new HBox(8, hexCaption, hexField);
        hexRow.setAlignment(Pos.CENTER_LEFT);

        // Swatches curados
        FlowPane presets = new FlowPane(6, 6);
        presets.getStyleClass().add("cc-presets");
        for (String hex : PRESETS) {
            Region sw = new Region();
            sw.getStyleClass().add("cc-preset");
            sw.setStyle("-fx-background-color: " + hex + ";");
            sizeOf(sw, 20, 20);
            sw.setOnMouseClicked(e -> setValue(Color.web(hex)));
            presets.getChildren().add(sw);
        }

        VBox content = new VBox(12, sbArea, hueArea, hexRow, presets);
        content.getStyleClass().add("cc-popup");
        content.setPadding(new Insets(14));
        popup.getContent().setAll(content);
        popup.setAutoHide(true);
    }

    // ── Interacción ────────────────────────────────────────────────────────
    private void handleSb(MouseEvent e) {
        sat = clamp01(e.getX() / SB_W);
        bri = 1.0 - clamp01(e.getY() / SB_H);
        commit();
    }

    private void handleHue(MouseEvent e) {
        hue = clamp01(e.getX() / HUE_W) * 360.0;
        commit();
    }

    private void applyHex() {
        try {
            setValue(Color.web(hexField.getText().trim()));
        } catch (IllegalArgumentException | NullPointerException ex) {
            refreshAll();   // entrada inválida → revertir el campo al valor actual
        }
    }

    /** Empuja el HSB interno al property sin re-derivarlo de vuelta. */
    private void commit() {
        updating = true;
        value.set(Color.hsb(hue, sat, bri));
        updating = false;
        // el listener ya llamó refreshAll() con updating=true
    }

    private void togglePopup() {
        if (popup.isShowing()) { popup.hide(); return; }
        Point2D p = button.localToScreen(0, button.getHeight() + 4);
        if (p != null) popup.show(button, p.getX(), p.getY());
    }

    // ── Refresco visual ────────────────────────────────────────────────────
    private void refreshAll() {
        Color c    = Color.hsb(hue, sat, bri);
        Color pure = Color.hsb(hue, 1.0, 1.0);
        String hex = toHex(c);

        swatch.setStyle("-fx-background-color: " + hex + ";");
        hexLabel.setText(hex);

        sbArea.setStyle("-fx-background-color: "
                + "linear-gradient(to bottom, transparent, black), "
                + "linear-gradient(to right, white, " + toHex(pure) + ");");

        sbThumb.setLayoutX(sat * SB_W - SB_R);
        sbThumb.setLayoutY((1.0 - bri) * SB_H - SB_R);
        sbThumb.setStyle("-fx-background-color: " + hex + ";");

        hueThumb.setLayoutX((hue / 360.0) * HUE_W - 3);

        if (!hexField.isFocused()) hexField.setText(hex);
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private void setHsbFrom(Color c) {
        if (c == null) c = Color.web("#AA5AFF");
        hue = c.getHue();
        sat = c.getSaturation();
        bri = c.getBrightness();
    }

    private static void sizeOf(Region r, double w, double h) {
        r.setMinSize(w, h);
        r.setPrefSize(w, h);
        r.setMaxSize(w, h);
    }

    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }

    private static String toHex(Color c) {
        return String.format(Locale.ROOT, "#%02X%02X%02X",
                (int) Math.round(c.getRed()   * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue()  * 255));
    }
}
