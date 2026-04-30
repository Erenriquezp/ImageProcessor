package com.example.imageprocessor.app.ui;

import com.example.imageprocessor.domain.ConvolutionKernel;
import com.example.imageprocessor.domain.FilterType;
import com.example.imageprocessor.domain.StretchMode;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class EditorFilterPane {

    private final ComboBox<FilterType> filterCombo = new ComboBox<>();
    private final Slider brightnessSlider = new Slider(-100, 100, 40);
    private final Slider saturationSlider = new Slider(0, 2, 1.3);
    private final Slider valueSlider = new Slider(0, 2, 0.9);
    private final Spinner<Integer> levelsSpinner = new Spinner<>(2, 255, 4);
    private final Slider thresholdSlider = new Slider(0, 255, 127);
    private final ColorPicker tintPicker = new ColorPicker(Color.web("#AA5AFF"));
    private final ComboBox<StretchMode> stretchModeCombo = new ComboBox<>();
    private final ComboBox<ConvolutionKernel> kernelCombo = new ComboBox<>();

    private final VBox brightnessBox = new VBox(6);
    private final VBox hsvBox = new VBox(6);
    private final VBox levelsBox = new VBox(6);
    private final VBox thresholdBox = new VBox(6);
    private final VBox tintBox = new VBox(6);
    private final VBox stretchBox = new VBox(6);
    private final VBox kernelBox = new VBox(6);

    private final VBox view;

    public EditorFilterPane() {
        initializeToolSelectors();
        configureFilterControls();

        view = new VBox(12,
                new Label("Herramientas"),
                new Label("Filtro"),
                filterCombo,
                brightnessBox,
                hsvBox,
                levelsBox,
                thresholdBox,
                tintBox,
                stretchBox,
                kernelBox
        );

        filterCombo.valueProperty().addListener((obs, oldV, newV) -> updateDynamicControlVisibility());
        updateDynamicControlVisibility();
    }

    public VBox getView() {
        return view;
    }

    public FilterType getSelectedFilter() {
        return filterCombo.getValue();
    }

    public int getBrightnessValue() {
        return (int) Math.round(brightnessSlider.getValue());
    }

    public float getSaturationValue() {
        return (float) saturationSlider.getValue();
    }

    public float getValueFactor() {
        return (float) valueSlider.getValue();
    }

    public int getRetroLevels() {
        return levelsSpinner.getValue();
    }

    public int getThresholdValue() {
        return (int) Math.round(thresholdSlider.getValue());
    }

    public Color getTintColor() {
        return tintPicker.getValue();
    }

    public StretchMode getStretchMode() {
        return stretchModeCombo.getValue();
    }

    public ConvolutionKernel getKernel() {
        return kernelCombo.getValue();
    }

    private void initializeToolSelectors() {
        filterCombo.getItems().setAll(FilterType.values());
        filterCombo.getSelectionModel().select(FilterType.NONE);

        stretchModeCombo.getItems().setAll(StretchMode.values());
        stretchModeCombo.getSelectionModel().select(StretchMode.DECIMAL);

        kernelCombo.getItems().setAll(ConvolutionKernel.values());
        kernelCombo.getSelectionModel().select(ConvolutionKernel.SHARPEN);
    }

    private void configureFilterControls() {
        brightnessBox.getChildren().setAll(new Label("Brillo"), brightnessSlider);
        hsvBox.getChildren().setAll(new Label("Saturation"), saturationSlider, new Label("Valor"), valueSlider);
        levelsBox.getChildren().setAll(new Label("Niveles (retro)"), levelsSpinner);
        thresholdBox.getChildren().setAll(new Label("Umbral"), thresholdSlider);
        tintBox.getChildren().setAll(new Label("Tinte de re coloracion"), tintPicker);
        stretchBox.getChildren().setAll(new Label("Metodo de estiramiento"), stretchModeCombo);
        kernelBox.getChildren().setAll(new Label("Kernel"), kernelCombo);
    }

    private void updateDynamicControlVisibility() {
        FilterType selected = filterCombo.getValue();

        updateVisibility(brightnessBox, selected == FilterType.BRIGHTNESS);
        updateVisibility(hsvBox, selected == FilterType.HSV);
        updateVisibility(levelsBox, selected == FilterType.RETRO1);
        updateVisibility(thresholdBox, selected == FilterType.BW_THRESHOLD);
        updateVisibility(tintBox, selected == FilterType.RECOLOR);
        updateVisibility(stretchBox, selected == FilterType.STRETCH_4_BITS);
        updateVisibility(kernelBox, selected == FilterType.CONVOLUTION);
    }

    private void updateVisibility(Region region, boolean visible) {
        region.setVisible(visible);
        region.setManaged(visible);
    }
}

