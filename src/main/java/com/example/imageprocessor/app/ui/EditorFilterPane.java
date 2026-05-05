package com.example.imageprocessor.app.ui;

import com.example.imageprocessor.domain.ConvolutionKernel;
import com.example.imageprocessor.domain.FilterType;
import com.example.imageprocessor.domain.StretchMode;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.function.Function;

public class EditorFilterPane {

    // ── Controls ────────────────────────────────────────────────────────────
    private final ComboBox<FilterType>        filterCombo         = new ComboBox<>();
    private final Slider                      brightnessSlider    = new Slider(-100, 100, 40);
    private final Slider                      saturationSlider    = new Slider(0, 2, 1.3);
    private final Slider                      valueSlider         = new Slider(0, 2, 0.9);
    private final Spinner<Integer>            levelsSpinner       = new Spinner<>(2, 255, 4);
    private final Slider                      thresholdSlider     = new Slider(0, 255, 127);
    private final Slider                      alphaSlider         = new Slider(0, 255, 128);
    private final ColorPicker                 tintPicker          = new ColorPicker(Color.web("#AA5AFF"));
    private final ComboBox<StretchMode>       stretchModeCombo    = new ComboBox<>();
    private final ComboBox<ConvolutionKernel> kernelCombo         = new ComboBox<>();

    // ── Retro 2 controls ────────────────────────────────────────────────────
    private final Spinner<Integer> retro2LevelsSpinner = new Spinner<>(2, 255, 4);
    private final CheckBox         retro2CheckR        = new CheckBox("Canal R (rojo)");
    private final CheckBox         retro2CheckG        = new CheckBox("Canal G (verde)");
    private final CheckBox         retro2CheckB        = new CheckBox("Canal B (azul)");

    // ── Grises cuantizados controls ─────────────────────────────────────────
    private final Spinner<Integer> grayQuantSpinner = new Spinner<>(2, 64, 8);

    // ── Parameter-group cards ────────────────────────────────────────────────
    private final VBox brightnessBox = new VBox(6);
    private final VBox hsvBox        = new VBox(8);
    private final VBox levelsBox     = new VBox(6);
    private final VBox retro2Box     = new VBox(6);
    private final VBox grayQuantBox  = new VBox(6);
    private final VBox thresholdBox  = new VBox(6);
    private final VBox alphaBox      = new VBox(6);
    private final VBox tintBox       = new VBox(6);
    private final VBox stretchBox    = new VBox(6);
    private final VBox kernelBox     = new VBox(6);

    // ── Dynamic params section header ────────────────────────────────────────
    private final Separator paramsSep    = new Separator();
    private final Label     paramsHeader = new Label("PARÁMETROS");

    private final VBox view;

    public EditorFilterPane() {
        initializeToolSelectors();
        configureFilterControls();

        // ── Static section header ───────────────────────────────────────────
        Label sectionHeader = new Label("HERRAMIENTAS");
        sectionHeader.getStyleClass().add("section-title");

        Separator headerSep = new Separator();
        headerSep.setOpacity(0.4);

        // ── Filter selector row ─────────────────────────────────────────────
        Label filterLabel = new Label("Filtro");
        filterLabel.getStyleClass().add("field-label");

        // ── Dynamic params header ───────────────────────────────────────────
        paramsHeader.getStyleClass().add("params-section-title");
        paramsSep.setOpacity(0.2);
        updateVisibility(paramsSep,    false);
        updateVisibility(paramsHeader, false);

        view = new VBox(10,
                sectionHeader,
                headerSep,
                filterLabel,
                filterCombo,
                paramsSep,
                paramsHeader,
                brightnessBox,
                hsvBox,
                levelsBox,
                retro2Box,
                grayQuantBox,
                thresholdBox,
                alphaBox,
                tintBox,
                stretchBox,
                kernelBox
        );

        filterCombo.valueProperty().addListener((obs, o, n) -> updateDynamicControlVisibility());
        updateDynamicControlVisibility();
    }

    // ── Public API ───────────────────────────────────────────────────────────
    public VBox getView() { return view; }

    public FilterType        getSelectedFilter()  { return filterCombo.getValue(); }
    public int               getBrightnessValue() { return (int) Math.round(brightnessSlider.getValue()); }
    public float             getSaturationValue() { return (float) saturationSlider.getValue(); }
    public float             getValueFactor()     { return (float) valueSlider.getValue(); }
    public int               getRetroLevels()     { return levelsSpinner.getValue(); }
    public int               getRetro2Levels()    { return retro2LevelsSpinner.getValue(); }
    public boolean           isRetro2ChannelR()   { return retro2CheckR.isSelected(); }
    public boolean           isRetro2ChannelG()   { return retro2CheckG.isSelected(); }
    public boolean           isRetro2ChannelB()   { return retro2CheckB.isSelected(); }
    public int               getGrayQuantLevels() { return grayQuantSpinner.getValue(); }
    public int               getThresholdValue()  { return (int) Math.round(thresholdSlider.getValue()); }
    public int               getAlphaValue()      { return (int) Math.round(alphaSlider.getValue()); }
    public Color             getTintColor()       { return tintPicker.getValue(); }
    public StretchMode       getStretchMode()     { return stretchModeCombo.getValue(); }
    public ConvolutionKernel getKernel()          { return kernelCombo.getValue(); }

    // ── Initialization ───────────────────────────────────────────────────────
    private void initializeToolSelectors() {
        filterCombo.getItems().setAll(FilterType.values());
        filterCombo.getSelectionModel().select(FilterType.NONE);
        filterCombo.setCellFactory(lv  -> new FilterTypeCell());
        filterCombo.setButtonCell(new FilterTypeCell());

        stretchModeCombo.getItems().setAll(StretchMode.values());
        stretchModeCombo.getSelectionModel().select(StretchMode.DECIMAL);
        kernelCombo.getItems().setAll(ConvolutionKernel.values());
        kernelCombo.getSelectionModel().select(ConvolutionKernel.SHARPEN);
    }

    private void configureFilterControls() {
        // Apply card style to every parameter group
        for (VBox box : new VBox[]{ brightnessBox, hsvBox, levelsBox, retro2Box,
                                    grayQuantBox, thresholdBox, alphaBox,
                                    tintBox, stretchBox, kernelBox }) {
            box.getStyleClass().addAll("filter-section", "filter-card");
        }

        // Slider groups — live value + reset button
        populateSliderBox(brightnessBox, brightnessSlider,
                "Brillo", v -> String.format("%+d", (int) Math.round(v)), 40);

        populateSliderBox(hsvBox, saturationSlider,
                "Saturación", v -> String.format("%.2f", v), 1.3);
        appendSliderRows(hsvBox, valueSlider,
                "Luminosidad", v -> String.format("%.2f", v), 0.9);

        // Spinner groups
        populateSpinnerBox(levelsBox, levelsSpinner, "Niveles (retro)", 4);

        // Retro 2 — spinner + styled checkboxes
        retro2CheckR.setSelected(true);
        retro2CheckG.setSelected(false);
        retro2CheckB.setSelected(false);
        retro2CheckR.getStyleClass().addAll("channel-check", "channel-check-r");
        retro2CheckG.getStyleClass().addAll("channel-check", "channel-check-g");
        retro2CheckB.getStyleClass().addAll("channel-check", "channel-check-b");
        populateSpinnerBox(retro2Box, retro2LevelsSpinner, "Niveles (glitch)", 4);
        retro2Box.getChildren().addAll(
                fieldLabel("Canales a cuantizar"),
                retro2CheckR, retro2CheckG, retro2CheckB
        );

        populateSpinnerBox(grayQuantBox, grayQuantSpinner, "Niveles de gris", 8);

        populateSliderBox(thresholdBox, thresholdSlider,
                "Umbral B/N", v -> String.format("%d", (int) Math.round(v)), 127);

        populateSliderBox(alphaBox, alphaSlider,
                "Transparencia (Alpha)", v -> String.format("%d", (int) Math.round(v)), 128);

        // ColorPicker — label + picker + reset
        populateTintBox();

        // ComboBox — label + combo + reset
        populateComboBox(stretchBox, stretchModeCombo,
                "Método de estiramiento", StretchMode.DECIMAL);
        populateComboBox(kernelBox, kernelCombo,
                "Kernel", ConvolutionKernel.SHARPEN);
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    /**
     * Fills {@code box} with a header row [label ··· live-value] and a control
     * row [slider ···· ↺], replacing all existing children.
     */
    private void populateSliderBox(VBox box, Slider slider, String labelText,
                                   Function<Double, String> fmt, double defaultVal) {
        box.getChildren().clear();
        appendSliderRows(box, slider, labelText, fmt, defaultVal);
    }

    /**
     * Appends a slider block to an existing VBox — useful for hsvBox which hosts
     * two sliders inside the same card.
     */
    private void appendSliderRows(VBox box, Slider slider, String labelText,
                                  Function<Double, String> fmt, double defaultVal) {
        slider.setPrefWidth(Region.USE_COMPUTED_SIZE);
        slider.setMaxWidth(Double.MAX_VALUE);

        // Header: label + spacer + live value
        Label nameLbl  = fieldLabel(labelText);
        Region spacer  = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label valueLbl = new Label(fmt.apply(slider.getValue()));
        valueLbl.getStyleClass().add("param-value-label");
        valueLbl.textProperty().bind(Bindings.createStringBinding(
                () -> fmt.apply(slider.getValue()), slider.valueProperty()));

        HBox headerRow = new HBox(4, nameLbl, spacer, valueLbl);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        // Control: slider + reset
        Button resetBtn = resetButton(() -> slider.setValue(defaultVal));
        HBox.setHgrow(slider, Priority.ALWAYS);
        HBox controlRow = new HBox(6, slider, resetBtn);
        controlRow.setAlignment(Pos.CENTER_LEFT);

        box.getChildren().addAll(headerRow, controlRow);
    }

    /** Fills {@code box} with a label + [spinner ↺] control. */
    private <T> void populateSpinnerBox(VBox box, Spinner<T> spinner,
                                        String labelText, T defaultVal) {
        spinner.setPrefWidth(Region.USE_COMPUTED_SIZE);
        spinner.setMaxWidth(Double.MAX_VALUE);

        Button resetBtn = resetButton(() -> spinner.getValueFactory().setValue(defaultVal));
        HBox.setHgrow(spinner, Priority.ALWAYS);
        HBox controlRow = new HBox(6, spinner, resetBtn);
        controlRow.setAlignment(Pos.CENTER_LEFT);

        box.getChildren().setAll(fieldLabel(labelText), controlRow);
    }

    /** Fills {@code box} with a label + [combo ↺] control. */
    private <T> void populateComboBox(VBox box, ComboBox<T> combo,
                                      String labelText, T defaultVal) {
        combo.setMaxWidth(Double.MAX_VALUE);
        Button resetBtn = resetButton(() -> combo.getSelectionModel().select(defaultVal));
        HBox.setHgrow(combo, Priority.ALWAYS);
        HBox controlRow = new HBox(6, combo, resetBtn);
        controlRow.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().setAll(fieldLabel(labelText), controlRow);
    }

    /** Fills tintBox with a label + [colorPicker ↺] control. */
    private void populateTintBox() {
        tintPicker.setMaxWidth(Double.MAX_VALUE);
        Button resetBtn = resetButton(() -> tintPicker.setValue(Color.web("#AA5AFF")));
        HBox.setHgrow(tintPicker, Priority.ALWAYS);
        HBox controlRow = new HBox(6, tintPicker, resetBtn);
        controlRow.setAlignment(Pos.CENTER_LEFT);
        tintBox.getChildren().setAll(fieldLabel("Tinte"), controlRow);
    }

    /** Creates a small circular reset button with the ↺ symbol. */
    private static Button resetButton(Runnable action) {
        Button btn = new Button("↺");
        btn.getStyleClass().add("param-reset-btn");
        btn.setOnAction(e -> action.run());
        return btn;
    }

    /** Creates a parameter label with the shared {@code field-label} style. */
    private static Label fieldLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("field-label");
        return l;
    }

    // ── Visibility ────────────────────────────────────────────────────────────
    private void updateDynamicControlVisibility() {
        FilterType sel = filterCombo.getValue();
        updateVisibility(brightnessBox, sel == FilterType.BRIGHTNESS);
        updateVisibility(hsvBox,        sel == FilterType.HSV);
        updateVisibility(levelsBox,     sel == FilterType.RETRO1);
        updateVisibility(retro2Box,     sel == FilterType.RETRO2);
        updateVisibility(grayQuantBox,  sel == FilterType.GRAYSCALE_QUANTIZED);
        updateVisibility(thresholdBox,  sel == FilterType.BW_THRESHOLD);
        updateVisibility(alphaBox,      sel == FilterType.ALPHA_GLOBAL);
        updateVisibility(tintBox,       sel == FilterType.RECOLOR);
        updateVisibility(stretchBox,    sel == FilterType.STRETCH_4_BITS);
        updateVisibility(kernelBox,     sel == FilterType.CONVOLUTION);

        boolean hasParams = brightnessBox.isManaged() || hsvBox.isManaged()
                || levelsBox.isManaged()    || retro2Box.isManaged()
                || grayQuantBox.isManaged() || thresholdBox.isManaged()
                || alphaBox.isManaged()     || tintBox.isManaged()
                || stretchBox.isManaged()   || kernelBox.isManaged();

        updateVisibility(paramsSep,    hasParams);
        updateVisibility(paramsHeader, hasParams);
    }

    private static void updateVisibility(Region region, boolean visible) {
        region.setVisible(visible);
        region.setManaged(visible);
    }

    // ── Inner cell for FilterType ComboBox ────────────────────────────────────
    /**
     * ListCell that shows a small colored circle before the filter name to
     * indicate its category.  Used for both the dropdown list and the button cell.
     */
    private static final class FilterTypeCell extends ListCell<FilterType> {
        @Override
        protected void updateItem(FilterType item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Label lbl = new Label(item.toString());
            lbl.setStyle("-fx-text-fill: #d2d2d4; -fx-font-size: 12.5px;");

            String dotColor = item.getDotColor();
            if (dotColor != null) {
                Circle dot = new Circle(4, Color.web(dotColor));
                HBox box = new HBox(8, dot, lbl);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
            } else {
                // NONE — no colored dot, slightly dimmer text
                lbl.setStyle("-fx-text-fill: #888890; -fx-font-size: 12.5px;");
                setGraphic(lbl);
            }
            setText(null);
        }
    }
}
