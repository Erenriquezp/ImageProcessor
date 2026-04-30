package com.example.imageprocessor.app;

import com.example.imageprocessor.app.ui.EditorFilterPane;
import com.example.imageprocessor.app.ui.GradientGeneratorPane;
import com.example.imageprocessor.app.ui.ImageFileChooserFactory;
import com.example.imageprocessor.domain.FilterType;
import com.example.imageprocessor.service.ImageIOService;
import com.example.imageprocessor.service.ImageProcessor;
import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Objects;

public class ImageProcessorApp extends Application {

    private static final String APP_TITLE = "ImageProcessor - Taller 2";
    private static final String MAIN_STYLESHEET = "/com/example/imageprocessor/styles.css";
    private static final String DEFAULT_STATUS = "Carga una imagen para comenzar.";

    private BufferedImage originalImage;
    private BufferedImage processedImage;

    // ── Image views ────────────────────────────────────────────────────────
    /** Main view — shows the processed/working image at all times. */
    private final ImageView processedView = new ImageView();
    /** Comparison view — shown to the left only in "before/after" mode. */
    private final ImageView originalView  = new ImageView();

    // ── Layout nodes needed for comparison toggle ──────────────────────────
    private HBox      editorHBox;          // parent container for the two panes
    private StackPane leftPane;            // wraps originalView
    private StackPane rightPane;           // wraps processedView
    private Region    compareDivider;      // thin vertical bar between panes
    private Label     compareResultLabel;  // "RESULTADO" badge on the right pane

    // ── Other UI components ────────────────────────────────────────────────
    private final Label statusLabel = new Label(DEFAULT_STATUS);
    private final EditorFilterPane editorFilterPane = new EditorFilterPane();
    private final CheckBox beforeAfterCheck = new CheckBox("Mostrar original");
    private GradientGeneratorPane gradientGeneratorPane;

    @Override
    public void start(Stage stage) {
        stage.setTitle(APP_TITLE);

        gradientGeneratorPane = new GradientGeneratorPane(this::loadGeneratedImageInEditor, this::setStatus);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setTop(buildTopBar(stage));
        root.setLeft(buildLeftPanel());
        root.setCenter(buildCenterTabs());
        root.setBottom(buildBottomBar());

        Scene scene = new Scene(root, 1400, 860);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource(MAIN_STYLESHEET),
                "No se encontró el stylesheet principal"
        ).toExternalForm());

        stage.setScene(scene);
        stage.show();

        configureListeners();
    }

    // ── Top bar ────────────────────────────────────────────────────────────

    private HBox buildTopBar(Stage stage) {
        Button openButton  = createToolbarButton("Abrir",         "fas-folder-open", "Abrir imagen");
        Button saveButton  = createToolbarButton("Guardar",       "fas-file-export", "Guardar imagen");
        Button resetButton = createToolbarButton("Reset",         "fas-power-off", "Restablecer imagen");
        Button applyButton = createToolbarButton("Aplicar filtro","fas-play",        "Aplicar filtro seleccionado");
        applyButton.getStyleClass().add("btn-primary");

        openButton .setOnAction(e -> openImage(stage));
        saveButton .setOnAction(e -> saveImage(stage));
        resetButton.setOnAction(e -> resetImage());
        applyButton.setOnAction(e -> applySelectedFilter());

        beforeAfterCheck.getStyleClass().add("top-toggle");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(10, openButton, saveButton, resetButton, applyButton, spacer, beforeAfterCheck);
        bar.setPadding(new Insets(12));
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    private Button createToolbarButton(String text, String iconLiteral, String tooltipText) {
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

    // ── Left panel ─────────────────────────────────────────────────────────

    private VBox buildLeftPanel() {
        VBox panel = editorFilterPane.getView();
        panel.setPadding(new Insets(14));
        panel.setPrefWidth(300);
        panel.setMinWidth(300);
        panel.setMaxWidth(300);
        panel.getStyleClass().add("left-panel");
        return panel;
    }

    // ── Center tabs ────────────────────────────────────────────────────────

    private TabPane buildCenterTabs() {
        configureImageView(processedView);
        configureImageView(originalView);

        // LEFT pane — original image (collapsed by default)
        Label originalBadge = makeCompareBadge("ORIGINAL", Pos.TOP_LEFT);
        leftPane = new StackPane(originalView, originalBadge);
        leftPane.getStyleClass().addAll("preview-pane", "compare-pane");
        leftPane.setMinWidth(0);
        leftPane.setPrefWidth(0);
        leftPane.setMaxWidth(0);

        // RIGHT pane — processed image (always visible)
        compareResultLabel = makeCompareBadge("RESULTADO", Pos.TOP_RIGHT);
        compareResultLabel.setVisible(false);
        rightPane = new StackPane(processedView, compareResultLabel);
        rightPane.getStyleClass().addAll("preview-pane", "compare-pane");
        rightPane.setMinWidth(0);
        HBox.setHgrow(rightPane, Priority.ALWAYS);

        // Thin vertical divider between panes (collapsed by default)
        compareDivider = new Region();
        compareDivider.setMinWidth(0);
        compareDivider.setPrefWidth(0);
        compareDivider.setMaxWidth(0);
        compareDivider.getStyleClass().add("compare-divider");

        // Bind each ImageView's fit size to its own pane.
        processedView.fitWidthProperty() .bind(rightPane.widthProperty() .subtract(32));
        processedView.fitHeightProperty().bind(rightPane.heightProperty().subtract(32));
        originalView .fitWidthProperty() .bind(leftPane .widthProperty() .subtract(32));
        originalView .fitHeightProperty().bind(leftPane .heightProperty().subtract(32));

        editorHBox = new HBox(leftPane, compareDivider, rightPane);
        editorHBox.setSpacing(0);
        editorHBox.getStyleClass().add("editor-hbox");

        Tab editorTab = new Tab("Editor", editorHBox);
        editorTab.setClosable(false);

        Tab gradientTab = new Tab("Generador", gradientGeneratorPane.getView());
        gradientTab.setClosable(false);

        TabPane tabs = new TabPane(editorTab, gradientTab);
        tabs.getStyleClass().add("center-tabs");
        return tabs;
    }

    /** Creates a floating badge label anchored inside a StackPane. */
    private Label makeCompareBadge(String text, Pos alignment) {
        Label label = new Label(text);
        label.getStyleClass().add("compare-badge");
        StackPane.setAlignment(label, alignment);
        StackPane.setMargin(label, new Insets(10));
        return label;
    }

    // ── Bottom bar ─────────────────────────────────────────────────────────

    private HBox buildBottomBar() {
        HBox bottom = new HBox(statusLabel);
        bottom.setPadding(new Insets(10, 14, 10, 14));
        bottom.getStyleClass().add("bottom-bar");
        return bottom;
    }

    // ── Listeners ──────────────────────────────────────────────────────────

    private void configureListeners() {
        beforeAfterCheck.selectedProperty().addListener((obs, oldV, selected) -> refreshEditorView(selected));
    }

    // ── Image operations ───────────────────────────────────────────────────

    private void openImage(Stage stage) {
        FileChooser chooser = ImageFileChooserFactory.createOpenImageChooser();
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        try {
            setEditorImages(ImageIOService.read(file));
            refreshEditorView(beforeAfterCheck.isSelected());
            setStatus("Imagen cargada: " + file.getName());
        } catch (Exception ex) {
            setErrorStatus("Error al abrir imagen", ex);
        }
    }

    private void saveImage(Stage stage) {
        BufferedImage candidate = getImageToSave();
        if (candidate == null) { setStatus("No hay imagen para guardar."); return; }

        FileChooser chooser = ImageFileChooserFactory.createSaveImageChooser();
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;
        try {
            ImageIOService.write(candidate, file);
            setStatus("Imagen guardada en: " + file.getName());
        } catch (Exception ex) {
            setErrorStatus("Error al guardar", ex);
        }
    }

    private void resetImage() {
        if (originalImage == null) { setStatus("No hay imagen cargada para resetear."); return; }
        processedImage = originalImage;
        beforeAfterCheck.setSelected(false);
        refreshEditorView(false);
        setStatus("Imagen restablecida al estado original.");
    }

    private void applySelectedFilter() {
        if (originalImage == null) { setStatus("Carga una imagen antes de aplicar filtros."); return; }

        FilterType filter = editorFilterPane.getSelectedFilter();
        if (filter == null || filter == FilterType.NONE) {
            processedImage = originalImage;
            refreshEditorView(beforeAfterCheck.isSelected());
            setStatus("Sin filtro seleccionado.");
            return;
        }
        try {
            processedImage = processFilter(filter);
            refreshEditorView(beforeAfterCheck.isSelected());
            setStatus("Filtro aplicado: " + filter);
        } catch (Exception ex) {
            setErrorStatus("Error al aplicar filtro", ex);
        }
    }

    /**
     * Central display update.
     * - Normal mode: rightPane takes 100% via HGrow.
     * - Compare mode: BOTH panes are explicitly bound to exactly 50% of the
     *   parent HBox width, guaranteeing a perfect 50/50 split every time.
     */
    private void refreshEditorView(boolean showOriginal) {
        boolean comparing = showOriginal && originalImage != null;

        if (comparing) {
            // Unbind in case this is called multiple times
            leftPane.prefWidthProperty().unbind();
            rightPane.prefWidthProperty().unbind();

            // Explicitly bind each pane to exactly half the available width
            // (subtract the 2px divider so they never overflow)
            leftPane.prefWidthProperty() .bind(editorHBox.widthProperty().subtract(2).divide(2));
            rightPane.prefWidthProperty().bind(editorHBox.widthProperty().subtract(2).divide(2));
            leftPane.setMaxWidth(Double.MAX_VALUE);

            compareDivider.setMinWidth(2);
            compareDivider.setPrefWidth(2);
            compareDivider.setMaxWidth(2);

            compareResultLabel.setVisible(true);
            showImage(originalView, originalImage);

        } else {
            // Release bindings and collapse left pane
            leftPane.prefWidthProperty().unbind();
            rightPane.prefWidthProperty().unbind();

            leftPane.setPrefWidth(0);
            leftPane.setMaxWidth(0);

            compareDivider.setMinWidth(0);
            compareDivider.setPrefWidth(0);
            compareDivider.setMaxWidth(0);

            compareResultLabel.setVisible(false);
        }

        showImage(processedView, processedImage);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Sets preserveRatio + smooth; fit dimensions are bound elsewhere. */
    private void configureImageView(ImageView imageView) {
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
    }

    private void loadGeneratedImageInEditor(BufferedImage image) {
        setEditorImages(image);
        refreshEditorView(beforeAfterCheck.isSelected());
    }

    private BufferedImage processFilter(FilterType filter) {
        return switch (filter) {
            case GRAYSCALE    -> ImageProcessor.grayscale(originalImage);
            case NEGATIVE     -> ImageProcessor.negative(originalImage);
            case BRIGHTNESS   -> ImageProcessor.brightness(originalImage, editorFilterPane.getBrightnessValue());
            case HSV          -> ImageProcessor.hsv(originalImage, editorFilterPane.getSaturationValue(), editorFilterPane.getValueFactor());
            case FROSTED      -> ImageProcessor.frosted(originalImage);
            case CIRCULAR_FADE -> ImageProcessor.circularFade(originalImage);
            case RETRO1       -> ImageProcessor.retro1(originalImage, editorFilterPane.getRetroLevels());
            case BW_THRESHOLD -> ImageProcessor.bwThreshold(originalImage, editorFilterPane.getThresholdValue());
            case RECOLOR      -> applyRecolorFilter();
            case STRETCH_4_BITS -> ImageProcessor.stretch4Bits(originalImage, editorFilterPane.getStretchMode());
            case CONVOLUTION  -> ImageProcessor.convolution(originalImage, editorFilterPane.getKernel());
            case NONE         -> originalImage;
        };
    }

    private BufferedImage applyRecolorFilter() {
        int rgb = fxColorToRgb(editorFilterPane.getTintColor());
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >>  8) & 0xFF;
        int b =  rgb        & 0xFF;
        return ImageProcessor.recolor(originalImage, r, g, b);
    }

    private void setEditorImages(BufferedImage image) {
        originalImage  = image;
        processedImage = image;
    }

    private BufferedImage getImageToSave() {
        return processedImage != null ? processedImage : gradientGeneratorPane.getGeneratedImage();
    }

    private void showImage(ImageView imageView, BufferedImage image) {
        imageView.setImage(image == null ? null : SwingFXUtils.toFXImage(image, null));
    }

    private void setStatus(String message) { statusLabel.setText(message); }

    private void setErrorStatus(String prefix, Exception ex) {
        setStatus(prefix + ": " + ex.getMessage());
    }

    private static int fxColorToRgb(Color c) {
        int r = (int) Math.round(c.getRed()   * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue()  * 255);
        return (r << 16) | (g << 8) | b;
    }

    public static void main(String[] args) { launch(args); }
}
