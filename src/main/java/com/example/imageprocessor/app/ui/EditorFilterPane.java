package com.example.imageprocessor.app.ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.kordamp.ikonli.javafx.FontIcon;

import com.example.imageprocessor.domain.ConvolutionKernel;
import com.example.imageprocessor.domain.FilterType;
import com.example.imageprocessor.domain.StretchMode;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class EditorFilterPane {

    // ── Filter selection model (replaces the old flat ComboBox) ──────────────
    private final ObjectProperty<FilterType> selectedFilter = new SimpleObjectProperty<>(FilterType.NONE);

    // ── Filter browser UI ────────────────────────────────────────────────────
    private final TextField searchField = new TextField();
    private final VBox filterListContainer = new VBox(2);
    private final Map<FilterType, Node> filterRows = new EnumMap<>(FilterType.class);
    /**
     * Estado expandido/colapsado por categoría; persiste entre reconstrucciones.
     */
    private final Map<Category, Boolean> categoryExpanded = new EnumMap<>(Category.class);
    private Runnable onFilterActivated; // doble clic en una fila → aplicar
    private Runnable onParamChanged; // cambio de filtro o de parámetro → preview en vivo

    // ── Parameter controls ───────────────────────────────────────────────────
    private final Slider brightnessSlider = new Slider(-100, 100, 40);
    private final Slider saturationSlider = new Slider(0, 2, 1.3);
    private final Slider valueSlider = new Slider(0, 2, 0.9);
    private final Spinner<Integer> levelsSpinner = new Spinner<>(2, 255, 4);
    private final Slider thresholdSlider = new Slider(0, 255, 127);
    private final Slider alphaSlider = new Slider(0, 255, 128);
    private final ColorChooser tintPicker = new ColorChooser(Color.web("#AA5AFF"));
    private final ComboBox<StretchMode> stretchModeCombo = new ComboBox<>();
    private final ComboBox<ConvolutionKernel> kernelCombo = new ComboBox<>();

    // ── Retro 2 controls ────────────────────────────────────────────────────
    private final Spinner<Integer> retro2LevelsSpinner = new Spinner<>(2, 255, 4);
    private final CheckBox retro2CheckR = new CheckBox("Canal R (rojo)");
    private final CheckBox retro2CheckG = new CheckBox("Canal G (verde)");
    private final CheckBox retro2CheckB = new CheckBox("Canal B (azul)");

    // ── Grises cuantizados controls ─────────────────────────────────────────
    private final Spinner<Integer> grayQuantSpinner = new Spinner<>(2, 64, 8);

    // ── Parameter-group cards ────────────────────────────────────────────────
    private final VBox brightnessBox = new VBox(6);
    private final VBox hsvBox = new VBox(8);
    private final VBox levelsBox = new VBox(6);
    private final VBox retro2Box = new VBox(6);
    private final VBox grayQuantBox = new VBox(6);
    private final VBox thresholdBox = new VBox(6);
    private final VBox alphaBox = new VBox(6);
    private final VBox tintBox = new VBox(6);
    private final VBox stretchBox = new VBox(6);
    private final VBox kernelBox = new VBox(6);

    // ── Dynamic params panel ─────────────────────────────────────────────────
    private final FontIcon paramsIcon = new FontIcon("fas-sliders-h");
    private final Label paramsFilterName = new Label();
    private final Label noParamsHint = new Label("Este filtro se aplica directamente, sin ajustes.");
    private final VBox paramsPanel = new VBox(8);

    private final VBox view;

    public EditorFilterPane() {
        initializeToolSelectors();
        configureFilterControls();

        // ── Static section header ───────────────────────────────────────────
        Label sectionHeader = new Label("FILTROS");
        sectionHeader.getStyleClass().add("section-title");

        Separator headerSep = new Separator();
        headerSep.setOpacity(0.4);

        // ── Search + categorized filter browser ─────────────────────────────
        HBox searchBox = buildSearchBox();
        ScrollPane filterScroll = buildFilterScroll();

        // ── Dynamic params panel ────────────────────────────────────────────
        // Built once here, but injected into the list right below the active
        // filter row (see positionParamsPanel) instead of pinned to the bottom.
        buildParamsPanel();

        view = new VBox(10,
                sectionHeader,
                headerSep,
                searchBox,
                filterScroll);

        // Selection & search wiring
        selectedFilter.addListener((obs, o, n) -> {
            updateDynamicControlVisibility();
            updateRowSelectionStyles();
            positionParamsPanel();
            fireParamChanged(); // nueva selección → repreview
        });
        searchField.textProperty().addListener((obs, o, n) -> rebuildFilterList(n));

        rebuildFilterList("");
        updateDynamicControlVisibility();
        wireParamListeners();
    }

    // ── Public API ───────────────────────────────────────────────────────────
    public VBox getView() {
        return view;
    }

    public FilterType getSelectedFilter() {
        return selectedFilter.get();
    }

    /** Callback ejecutado al hacer doble clic en un filtro (aplicar directo). */
    public void setOnFilterActivated(Runnable handler) {
        this.onFilterActivated = handler;
    }

    /**
     * Callback ejecutado cuando cambia el filtro seleccionado o cualquier
     * parámetro.
     */
    public void setOnParamChanged(Runnable handler) {
        this.onParamChanged = handler;
    }

    /** Deselecciona el filtro activo (vuelve a "Sin filtro"). */
    public void clearSelection() {
        selectedFilter.set(FilterType.NONE);
    }

    private void fireParamChanged() {
        if (onParamChanged != null)
            onParamChanged.run();
    }

    /** Conecta todos los controles de parámetros al callback de preview en vivo. */
    private void wireParamListeners() {
        Runnable f = this::fireParamChanged;
        brightnessSlider.valueProperty().addListener((o, a, b) -> f.run());
        saturationSlider.valueProperty().addListener((o, a, b) -> f.run());
        valueSlider.valueProperty().addListener((o, a, b) -> f.run());
        thresholdSlider.valueProperty().addListener((o, a, b) -> f.run());
        alphaSlider.valueProperty().addListener((o, a, b) -> f.run());
        levelsSpinner.valueProperty().addListener((o, a, b) -> f.run());
        retro2LevelsSpinner.valueProperty().addListener((o, a, b) -> f.run());
        grayQuantSpinner.valueProperty().addListener((o, a, b) -> f.run());
        retro2CheckR.selectedProperty().addListener((o, a, b) -> f.run());
        retro2CheckG.selectedProperty().addListener((o, a, b) -> f.run());
        retro2CheckB.selectedProperty().addListener((o, a, b) -> f.run());
        stretchModeCombo.valueProperty().addListener((o, a, b) -> f.run());
        kernelCombo.valueProperty().addListener((o, a, b) -> f.run());
        tintPicker.valueProperty().addListener((o, a, b) -> f.run());
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

    public int getRetro2Levels() {
        return retro2LevelsSpinner.getValue();
    }

    public boolean isRetro2ChannelR() {
        return retro2CheckR.isSelected();
    }

    public boolean isRetro2ChannelG() {
        return retro2CheckG.isSelected();
    }

    public boolean isRetro2ChannelB() {
        return retro2CheckB.isSelected();
    }

    public int getGrayQuantLevels() {
        return grayQuantSpinner.getValue();
    }

    public int getThresholdValue() {
        return (int) Math.round(thresholdSlider.getValue());
    }

    public int getAlphaValue() {
        return (int) Math.round(alphaSlider.getValue());
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

    // ═══════════════════════════════════════════════════════════════════════
    // FILTER BROWSER — search box + categorized, selectable list
    // Categories are derived from FilterType#getDotColor() so the domain
    // layer stays untouched.
    // ═══════════════════════════════════════════════════════════════════════

    /** Visual grouping for the browser; mapped from each filter's dot color. */
    private enum Category {
        BASIC("Básicos", "#4a8fd6", "fas-sliders-h"),
        TRANSPARENCY("Transparencia", "#4acc88", "fas-tint"),
        RETRO("Retro y color", "#e8913a", "fas-th"),
        CONVOLUTION("Convolución", "#9a6adc", "fas-border-all"),
        COLOR_MATRIX("Matriz de color", "#e84a8f", "fas-palette"),
        BUFFER("Buffer De Acumulación", "#ff6b6b", "fas-wave-square");

        final String display;
        final String color;
        final String icon;

        Category(String display, String color, String icon) {
            this.display = display;
            this.color = color;
            this.icon = icon;
        }
    }

    private static Category categoryOf(FilterType ft) {
        String c = ft.getDotColor();
        if (c == null)
            return null;
        return switch (c) {
            case "#4a8fd6" -> Category.BASIC;
            case "#4acc88" -> Category.TRANSPARENCY;
            case "#e8913a" -> Category.RETRO;
            case "#9a6adc" -> Category.CONVOLUTION;
            case "#e84a8f" -> Category.COLOR_MATRIX;
            case "#ff6b6b" -> Category.BUFFER;
            default -> null;
        };
    }

    private HBox buildSearchBox() {
        FontIcon icon = new FontIcon("fas-search");
        icon.getStyleClass().add("search-icon");

        searchField.setPromptText("Buscar filtro…");
        searchField.getStyleClass().add("search-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        HBox box = new HBox(8, icon, searchField);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("search-box");
        return box;
    }

    private ScrollPane buildFilterScroll() {
        filterListContainer.getStyleClass().add("filter-list");

        ScrollPane scroll = new ScrollPane(filterListContainer);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setMinHeight(150);
        scroll.getStyleClass().add("filter-browser-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    /** Rebuilds the list, honoring the current search query. */
    private void rebuildFilterList(String query) {
        filterListContainer.getChildren().clear();
        filterRows.clear();

        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        // "Sin filtro" (NONE) always sits at the top when it matches.
        if (matches(FilterType.NONE, q)) {
            filterListContainer.getChildren().add(filterRow(FilterType.NONE));
        }

        boolean searching = !q.isEmpty();
        for (Category cat : Category.values()) {
            List<Node> rows = new ArrayList<>();
            for (FilterType ft : FilterType.values()) {
                if (categoryOf(ft) == cat && matches(ft, q)) {
                    rows.add(filterRow(ft));
                }
            }
            if (rows.isEmpty())
                continue;
            // Al buscar, forzamos expandido para que los resultados sean visibles;
            // si no, respetamos el estado guardado (expandido por defecto).
            boolean expanded = searching || categoryExpanded.getOrDefault(cat, true);
            filterListContainer.getChildren().add(categoryGroup(cat, rows, expanded, searching));
        }

        if (filterListContainer.getChildren().isEmpty()) {
            Label empty = new Label("Sin resultados para \"" + query.trim() + "\"");
            empty.getStyleClass().add("filter-empty");
            filterListContainer.getChildren().add(empty);
        }

        updateRowSelectionStyles();
        positionParamsPanel(); // re-attach AJUSTES under the active row after rebuild
    }

    private static boolean matches(FilterType ft, String q) {
        return q.isEmpty() || ft.toString().toLowerCase(Locale.ROOT).contains(q);
    }

    /**
     * Builds one collapsible category section: a clickable header
     * (chevron + category icon + title + count) over a content box holding the
     * filter rows. Icons follow the unified muted system style — only the tiny
     * category dot keeps the literal semantic colour.
     */
    private VBox categoryGroup(Category cat, List<Node> rows,
            boolean expanded, boolean searching) {
        FontIcon chevron = new FontIcon("fas-chevron-right");
        chevron.setIconSize(9);
        chevron.getStyleClass().add("filter-category-chevron");

        FontIcon icon = new FontIcon(cat.icon);
        icon.setIconSize(12);
        icon.getStyleClass().add("filter-category-icon");

        Label lbl = new Label(cat.display.toUpperCase(Locale.ROOT));
        lbl.getStyleClass().add("filter-category-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label count = new Label(Integer.toString(rows.size()));
        count.getStyleClass().add("filter-category-count");

        HBox header = new HBox(8, chevron, icon, lbl, spacer, count);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setMaxWidth(Double.MAX_VALUE);
        header.getStyleClass().add("filter-category-header");

        VBox content = new VBox(2);
        content.getStyleClass().add("filter-category-content");
        content.getChildren().setAll(rows);

        applyExpansion(content, chevron, expanded);

        // While searching the sections stay open and locked (toggling would hide
        // matches); without a query the header toggles and remembers its state.
        if (!searching) {
            header.setOnMouseClicked(e -> {
                boolean now = !content.isVisible();
                categoryExpanded.put(cat, now);
                applyExpansion(content, chevron, now);
            });
        }

        VBox group = new VBox(header, content);
        group.getStyleClass().add("filter-category-group");
        return group;
    }

    /**
     * Moves the shared "AJUSTES" panel so it sits directly below the currently
     * selected filter row, nested inside that filter's category section. When no
     * real filter is active — or it's filtered out by the search — the panel
     * detaches and stays hidden.
     */
    private void positionParamsPanel() {
        if (paramsPanel.getParent() instanceof Pane prev) {
            prev.getChildren().remove(paramsPanel);
        }
        FilterType sel = selectedFilter.get();
        Node row = filterRows.get(sel);
        boolean active = sel != null && sel != FilterType.NONE && row != null;
        updateVisibility(paramsPanel, active);
        if (active && row.getParent() instanceof VBox content) {
            int idx = content.getChildren().indexOf(row);
            content.getChildren().add(idx + 1, paramsPanel);
        }
    }

    /** Toggles a section's content visibility and rotates its chevron. */
    private static void applyExpansion(VBox content, FontIcon chevron, boolean expanded) {
        content.setVisible(expanded);
        content.setManaged(expanded);
        chevron.setRotate(expanded ? 90 : 0);
    }

    private HBox filterRow(FilterType ft) {
        Label name = new Label(ft.toString());
        name.getStyleClass().add("filter-row-name");

        HBox row = new HBox(name);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.getStyleClass().add("filter-row");
        if (ft == FilterType.NONE)
            row.getStyleClass().add("filter-row-none");
        row.setOnMouseClicked(e -> {
            selectedFilter.set(ft);
            if (e.getClickCount() == 2 && onFilterActivated != null) {
                onFilterActivated.run();
            }
        });

        filterRows.put(ft, row);
        return row;
    }

    private void updateRowSelectionStyles() {
        FilterType sel = selectedFilter.get();
        filterRows.forEach((ft, node) -> {
            node.getStyleClass().remove("filter-row-selected");
            if (ft == sel)
                node.getStyleClass().add("filter-row-selected");
        });
    }

    /**
     * Builds the bottom "AJUSTES" panel: a delimited container with a header
     * (category icon + active filter name) and the contextual parameter cards.
     */
    private VBox buildParamsPanel() {
        paramsIcon.setIconSize(13);
        paramsIcon.getStyleClass().add("params-icon");

        Label caption = new Label("AJUSTES");
        caption.getStyleClass().add("params-section-title");

        HBox titleRow = new HBox(8, paramsIcon, caption);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.getStyleClass().add("params-title-row");

        paramsFilterName.getStyleClass().add("params-filter-name");
        noParamsHint.getStyleClass().add("params-empty-hint");
        noParamsHint.setWrapText(true);

        paramsPanel.getStyleClass().add("params-panel");
        paramsPanel.getChildren().addAll(
                titleRow,
                paramsFilterName,
                noParamsHint,
                brightnessBox, hsvBox, levelsBox, retro2Box, grayQuantBox,
                thresholdBox, alphaBox, tintBox, stretchBox, kernelBox);
        return paramsPanel;
    }

    // ── Initialization ───────────────────────────────────────────────────────
    private void initializeToolSelectors() {
        stretchModeCombo.getItems().setAll(StretchMode.values());
        stretchModeCombo.getSelectionModel().select(StretchMode.DECIMAL);
        kernelCombo.getItems().setAll(ConvolutionKernel.values());
        kernelCombo.getSelectionModel().select(ConvolutionKernel.SHARPEN);
    }

    private void configureFilterControls() {
        // Param groups live flush inside the shared "params-panel" container,
        // so they only need section spacing — not their own card chrome.
        for (VBox box : new VBox[] { brightnessBox, hsvBox, levelsBox, retro2Box,
                grayQuantBox, thresholdBox, alphaBox,
                tintBox, stretchBox, kernelBox }) {
            box.getStyleClass().add("filter-section");
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
                retro2CheckR, retro2CheckG, retro2CheckB);

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
        Label nameLbl = fieldLabel(labelText);
        Region spacer = new Region();
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

    /** Fills tintBox with a label + [colorChooser ↺] control. */
    private void populateTintBox() {
        Region pickerView = tintPicker.getView();
        pickerView.setMaxWidth(Double.MAX_VALUE);
        Button resetBtn = resetButton(() -> tintPicker.setValue(Color.web("#AA5AFF")));
        HBox.setHgrow(pickerView, Priority.ALWAYS);
        HBox controlRow = new HBox(6, pickerView, resetBtn);
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
        FilterType sel = selectedFilter.get();
        updateVisibility(brightnessBox, sel == FilterType.BRIGHTNESS);
        updateVisibility(hsvBox, sel == FilterType.HSV);
        updateVisibility(levelsBox, sel == FilterType.RETRO1);
        updateVisibility(retro2Box, sel == FilterType.RETRO2);
        updateVisibility(grayQuantBox, sel == FilterType.GRAYSCALE_QUANTIZED);
        updateVisibility(thresholdBox, sel == FilterType.BW_THRESHOLD);
        updateVisibility(alphaBox, sel == FilterType.ALPHA_GLOBAL);
        updateVisibility(tintBox, sel == FilterType.RECOLOR);
        updateVisibility(stretchBox, sel == FilterType.STRETCH_4_BITS);
        updateVisibility(kernelBox, sel == FilterType.CONVOLUTION);

        boolean hasParams = brightnessBox.isManaged() || hsvBox.isManaged()
                || levelsBox.isManaged() || retro2Box.isManaged()
                || grayQuantBox.isManaged() || thresholdBox.isManaged()
                || alphaBox.isManaged() || tintBox.isManaged()
                || stretchBox.isManaged() || kernelBox.isManaged();

        // The whole "AJUSTES" panel is shown for any real filter; the hint
        // fills the gap when the filter has no adjustable parameters.
        boolean active = sel != null && sel != FilterType.NONE;
        updateVisibility(paramsPanel, active);
        updateVisibility(noParamsHint, active && !hasParams);

        if (active) {
            paramsFilterName.setText(sel.toString());
            Category cat = categoryOf(sel);
            if (cat != null) {
                // Keep the per-filter glyph but let CSS drive the colour, so the
                // params icon matches the rest of the system's icon styling.
                paramsIcon.setIconLiteral(cat.icon);
            }
        }
    }

    private static void updateVisibility(Region region, boolean visible) {
        region.setVisible(visible);
        region.setManaged(visible);
    }
}
