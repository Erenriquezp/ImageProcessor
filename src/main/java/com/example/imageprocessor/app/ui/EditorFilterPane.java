package com.example.imageprocessor.app.ui;

import com.example.imageprocessor.domain.ConvolutionKernel;
import com.example.imageprocessor.domain.FilterType;
import com.example.imageprocessor.domain.StretchMode;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class EditorFilterPane {

    private final ComboBox<FilterType>       filterCombo       = new ComboBox<>();
    private final Slider                     brightnessSlider  = new Slider(-100, 100, 40);
    private final Slider                     saturationSlider  = new Slider(0, 2, 1.3);
    private final Slider                     valueSlider       = new Slider(0, 2, 0.9);
    private final Spinner<Integer>           levelsSpinner     = new Spinner<>(2, 255, 4);
    private final Slider                     thresholdSlider   = new Slider(0, 255, 127);
    private final Slider                     alphaSlider       = new Slider(0, 255, 128);
    private final ColorPicker                tintPicker        = new ColorPicker(Color.web("#AA5AFF"));
    private final ComboBox<StretchMode>      stretchModeCombo  = new ComboBox<>();
    private final ComboBox<ConvolutionKernel> kernelCombo      = new ComboBox<>();

    // ── Retro 2 controls ────────────────────────────────────────────────────
    private final Spinner<Integer>           retro2LevelsSpinner = new Spinner<>(2, 255, 4);
    private final CheckBox                   retro2CheckR      = new CheckBox("Canal R (rojo)");
    private final CheckBox                   retro2CheckG      = new CheckBox("Canal G (verde)");
    private final CheckBox                   retro2CheckB      = new CheckBox("Canal B (azul)");

    // ── Grises cuantizados controls ─────────────────────────────────────────
    private final Spinner<Integer>           grayQuantSpinner  = new Spinner<>(2, 64, 8);

    private final VBox brightnessBox = new VBox(6);
    private final VBox hsvBox        = new VBox(6);
    private final VBox levelsBox     = new VBox(6);
    private final VBox retro2Box     = new VBox(6);
    private final VBox grayQuantBox  = new VBox(6);
    private final VBox thresholdBox  = new VBox(6);
    private final VBox alphaBox      = new VBox(6);
    private final VBox tintBox       = new VBox(6);
    private final VBox stretchBox    = new VBox(6);
    private final VBox kernelBox     = new VBox(6);

    private final VBox view;

    public EditorFilterPane() {
        initializeToolSelectors();
        configureFilterControls();

        // ── Section header ──────────────────────────────────────────────
        Label sectionHeader = new Label("HERRAMIENTAS");
        sectionHeader.getStyleClass().addAll("section-title");

        Separator headerSep = new Separator();
        headerSep.setOpacity(0.4);

        // ── Filter selector row ─────────────────────────────────────────
        Label filterLabel = new Label("Filtro");
        filterLabel.getStyleClass().add("field-label");

        view = new VBox(10,
                sectionHeader,
                headerSep,
                filterLabel,
                filterCombo,
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

        filterCombo.valueProperty().addListener((obs, oldV, newV) -> updateDynamicControlVisibility());
        updateDynamicControlVisibility();
    }

    public VBox getView() { return view; }

    public FilterType    getSelectedFilter()  { return filterCombo.getValue(); }
    public int           getBrightnessValue() { return (int) Math.round(brightnessSlider.getValue()); }
    public float         getSaturationValue() { return (float) saturationSlider.getValue(); }
    public float         getValueFactor()     { return (float) valueSlider.getValue(); }
    public int           getRetroLevels()     { return levelsSpinner.getValue(); }
    public int           getRetro2Levels()    { return retro2LevelsSpinner.getValue(); }
    public boolean       isRetro2ChannelR()   { return retro2CheckR.isSelected(); }
    public boolean       isRetro2ChannelG()   { return retro2CheckG.isSelected(); }
    public boolean       isRetro2ChannelB()   { return retro2CheckB.isSelected(); }
    public int           getGrayQuantLevels() { return grayQuantSpinner.getValue(); }
    public int           getThresholdValue()  { return (int) Math.round(thresholdSlider.getValue()); }
    public int           getAlphaValue()      { return (int) Math.round(alphaSlider.getValue()); }
    public Color         getTintColor()       { return tintPicker.getValue(); }
    public StretchMode   getStretchMode()     { return stretchModeCombo.getValue(); }
    public ConvolutionKernel getKernel()      { return kernelCombo.getValue(); }

    private void initializeToolSelectors() {
        filterCombo.getItems().setAll(FilterType.values());
        filterCombo.getSelectionModel().select(FilterType.NONE);

        stretchModeCombo.getItems().setAll(StretchMode.values());
        stretchModeCombo.getSelectionModel().select(StretchMode.DECIMAL);

        kernelCombo.getItems().setAll(ConvolutionKernel.values());
        kernelCombo.getSelectionModel().select(ConvolutionKernel.SHARPEN);
    }

    private void configureFilterControls() {
        brightnessBox.getStyleClass().add("filter-section");
        hsvBox       .getStyleClass().add("filter-section");
        levelsBox    .getStyleClass().add("filter-section");
        retro2Box    .getStyleClass().add("filter-section");
        grayQuantBox .getStyleClass().add("filter-section");
        thresholdBox .getStyleClass().add("filter-section");
        alphaBox     .getStyleClass().add("filter-section");
        tintBox      .getStyleClass().add("filter-section");
        stretchBox   .getStyleClass().add("filter-section");
        kernelBox    .getStyleClass().add("filter-section");

        brightnessBox.getChildren().setAll(fieldLabel("Brillo"),            brightnessSlider);
        hsvBox       .getChildren().setAll(fieldLabel("Saturación"),        saturationSlider,
                                           fieldLabel("Luminosidad"),       valueSlider);
        levelsBox    .getChildren().setAll(fieldLabel("Niveles (retro)"),   levelsSpinner);

        retro2CheckR.setSelected(true);
        retro2CheckG.setSelected(false);
        retro2CheckB.setSelected(false);
        retro2CheckR.getStyleClass().addAll("channel-check", "channel-check-r");
        retro2CheckG.getStyleClass().addAll("channel-check", "channel-check-g");
        retro2CheckB.getStyleClass().addAll("channel-check", "channel-check-b");
        retro2Box.getChildren().setAll(
                fieldLabel("Niveles (glitch)"), retro2LevelsSpinner,
                fieldLabel("Canales a cuantizar"),
                retro2CheckR, retro2CheckG, retro2CheckB
        );

        grayQuantBox.getChildren().setAll(fieldLabel("Niveles de gris"), grayQuantSpinner);
        thresholdBox .getChildren().setAll(fieldLabel("Umbral B/N"),        thresholdSlider);
        alphaBox     .getChildren().setAll(fieldLabel("Transparencia (Alpha)"), alphaSlider);
        tintBox      .getChildren().setAll(fieldLabel("Tinte"),             tintPicker);
        stretchBox   .getChildren().setAll(fieldLabel("Método de estiramiento"), stretchModeCombo);
        kernelBox    .getChildren().setAll(fieldLabel("Kernel"),            kernelCombo);
    }

    /** Creates a parameter label with the shared `field-label` style. */
    private static Label fieldLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("field-label");
        return l;
    }

    private void updateDynamicControlVisibility() {
        FilterType selected = filterCombo.getValue();
        updateVisibility(brightnessBox, selected == FilterType.BRIGHTNESS);
        updateVisibility(hsvBox,        selected == FilterType.HSV);
        updateVisibility(levelsBox,     selected == FilterType.RETRO1);
        updateVisibility(retro2Box,     selected == FilterType.RETRO2);
        updateVisibility(grayQuantBox,  selected == FilterType.GRAYSCALE_QUANTIZED);
        updateVisibility(thresholdBox,  selected == FilterType.BW_THRESHOLD);
        updateVisibility(alphaBox,      selected == FilterType.ALPHA_GLOBAL);
        updateVisibility(tintBox,       selected == FilterType.RECOLOR);
        updateVisibility(stretchBox,    selected == FilterType.STRETCH_4_BITS);
        updateVisibility(kernelBox,     selected == FilterType.CONVOLUTION);
    }

    private static void updateVisibility(Region region, boolean visible) {
        region.setVisible(visible);
        region.setManaged(visible);
    }
}
