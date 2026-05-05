package com.example.imageprocessor.app;

import com.example.imageprocessor.app.ui.EditorFilterPane;
import com.example.imageprocessor.app.ui.EditorPreviewPane;
import com.example.imageprocessor.app.ui.GradientGeneratorPane;
import com.example.imageprocessor.app.ui.ImageFileChooserFactory;
import com.example.imageprocessor.app.ui.TopBar;
import com.example.imageprocessor.domain.FilterType;
import com.example.imageprocessor.service.ImageIOService;
import com.example.imageprocessor.service.ImageProcessor;
import javafx.application.Application;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Objects;

/**
 * Punto de entrada de la aplicación y coordinador principal.
 * Responsabilidades:
 *  - Estado de sesión: {@code originalImage} y {@code processedImage}.
 *  - Construcción y cableado de los componentes de UI.
 *  - Operaciones de imagen: abrir, guardar, resetear, aplicar filtros.
 *  - Coordinación entre la vista ({@link EditorPreviewPane}) y los servicios.
 */
public class ImageProcessorApp extends Application {

    private static final String APP_TITLE  = "ImageProcessor - Taller 2";
    private static final String STYLESHEET = "/com/example/imageprocessor/styles.css";
    private static final String APP_ICON   = "/com/example/imageprocessor/app-icon.png";
    private static final String DEFAULT_MSG = "Carga una imagen para comenzar.";

    // ── Session state ─────────────────────────────────────────────────────
    private BufferedImage originalImage;
    private BufferedImage processedImage;

    // ── UI components ─────────────────────────────────────────────────────
    private final EditorPreviewPane previewPane      = new EditorPreviewPane();
    private final EditorFilterPane  editorFilterPane = new EditorFilterPane();
    private final CheckBox          compareToggle    = new CheckBox("Mostrar original");
    private final Label             statusLabel      = new Label(DEFAULT_MSG);
    private GradientGeneratorPane   gradientPane;

    // ── Application lifecycle ─────────────────────────────────────────────

    @Override
    public void start(Stage stage) {
        stage.setTitle(APP_TITLE);

        // ── App icon ──────────────────────────────────────────────────────
        var iconUrl = getClass().getResourceAsStream(APP_ICON);
        if (iconUrl != null) {
            stage.getIcons().add(new Image(iconUrl));
        }

        gradientPane = new GradientGeneratorPane(this::loadGeneratedImageInEditor, this::setStatus);

        TopBar topBar = new TopBar(
                () -> openImage(stage),
                () -> saveImage(stage),
                this::resetImage,
                this::applySelectedFilter,
                previewPane::zoomIn,
                previewPane::zoomOut,
                previewPane::resetZoom,
                previewPane.getZoomIndicator(),
                compareToggle
        );

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setTop(topBar.getView());
        root.setLeft(buildLeftPanel());
        root.setCenter(buildCenterTabs());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1400, 860);
        final String cssUrl = Objects.requireNonNull(
                getClass().getResource(STYLESHEET),
                "No se encontró el stylesheet principal"
        ).toExternalForm();
        scene.getStylesheets().add(cssUrl);

        stage.setScene(scene);
        stage.show();

        // ── Inject dark stylesheet into every sub-window (e.g. ColorPicker
        //    custom-color dialog, which has its own Stage + Scene). ──────────
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                for (Window w : change.getAddedSubList()) {
                    // Skip PopupWindow subclasses (Tooltip, ContextMenu, etc.):
                    // they inherit CSS from their owner scene automatically.
                    // Only top-level Stage windows need explicit injection.
                    if (!(w instanceof Stage)) continue;

                    Scene subScene = w.getScene();
                    if (subScene != null) {
                        injectStylesheet(subScene, cssUrl);
                    } else {
                        // Scene not yet attached — wait for it
                        w.sceneProperty().addListener((obs2, oldS, newS) -> {
                            if (newS != null) injectStylesheet(newS, cssUrl);
                        });
                    }
                }
            }
        });

        compareToggle.selectedProperty().addListener(
                (obs, old, selected) -> refreshView(selected));
    }

    /**
     * Injects our dark stylesheet into {@code target} and forces an
     * immediate dark background on both the Scene fill and the root node's
     * inline style.  The inline style is applied synchronously (before the
     * first CSS layout pass) so there is zero white-flash on sub-windows
     * such as the CustomColorDialog Stage.
     *
     * <p>Note: the inline style value (#17171a) intentionally matches the
     * {@code .custom-color-dialog} rule in color-picker.css so that
     * removing the inline style (via a later CSS pass) produces no visible
     * change.
     */
    private static void injectStylesheet(Scene target, String cssUrl) {
        // 1. Darken the Scene fill first — this is the very first thing
        //    rendered when a new window appears (before any node layout).
        target.setFill(Color.web("#17171a"));

        // 2. Apply the same colour directly on the root node as an inline
        //    style so it takes effect in the *current* render frame.
        //    Inline styles have the highest CSS specificity, but since our
        //    CSS rule targets the same value this causes no visual conflict.
        if (target.getRoot() != null) {
            target.getRoot().setStyle("-fx-background-color: #17171a;");
        }

        // 3. Add the stylesheet (idempotent — skip if already present).
        if (!target.getStylesheets().contains(cssUrl)) {
            target.getStylesheets().add(cssUrl);
        }
    }

    // ── Layout helpers ────────────────────────────────────────────────────

    private VBox buildLeftPanel() {
        VBox panel = editorFilterPane.getView();
        panel.setPadding(new Insets(14));
        panel.setPrefWidth(300);
        panel.setMinWidth(300);
        panel.setMaxWidth(300);
        panel.getStyleClass().add("left-panel");
        return panel;
    }

    private TabPane buildCenterTabs() {
        Tab editorTab   = new Tab("Editor",    previewPane.getView());
        editorTab.setClosable(false);
        Tab gradientTab = new Tab("Generador", gradientPane.getView());
        gradientTab.setClosable(false);

        TabPane tabs = new TabPane(editorTab, gradientTab);
        tabs.getStyleClass().add("center-tabs");
        return tabs;
    }

    private HBox buildStatusBar() {
        HBox bar = new HBox(statusLabel);
        bar.setPadding(new Insets(10, 14, 10, 14));
        bar.getStyleClass().add("bottom-bar");
        return bar;
    }

    // ── Image operations ──────────────────────────────────────────────────

    private void openImage(Stage stage) {
        FileChooser chooser = ImageFileChooserFactory.createOpenImageChooser();
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        try {
            setSessionImages(ImageIOService.read(file));
            refreshView(compareToggle.isSelected());
            setStatus("Imagen cargada: " + file.getName());
        } catch (Exception ex) {
            setErrorStatus("Error al abrir imagen", ex);
        }
    }

    private void saveImage(Stage stage) {
        BufferedImage candidate = processedImage != null
                ? processedImage : gradientPane.getGeneratedImage();
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
        compareToggle.setSelected(false);
        refreshView(false);
        setStatus("Imagen restablecida al estado original.");
    }

    private void applySelectedFilter() {
        if (originalImage == null) { setStatus("Carga una imagen antes de aplicar filtros."); return; }

        FilterType filter = editorFilterPane.getSelectedFilter();
        if (filter == null || filter == FilterType.NONE) {
            processedImage = originalImage;
            refreshView(compareToggle.isSelected());
            setStatus("Sin filtro seleccionado.");
            return;
        }
        try {
            processedImage = processFilter(filter);
            refreshView(compareToggle.isSelected());
            setStatus("Filtro aplicado: " + filter);
        } catch (Exception ex) {
            setErrorStatus("Error al aplicar filtro", ex);
        }
    }

    private void loadGeneratedImageInEditor(BufferedImage image) {
        setSessionImages(image);
        refreshView(compareToggle.isSelected());
    }

    // ── View coordination ─────────────────────────────────────────────────

    /**
     * Sincroniza la vista con el estado de sesión.
     * Delega la lógica de compare mode y zoom en {@link EditorPreviewPane}.
     */
    private void refreshView(boolean showOriginal) {
        boolean comparing = showOriginal && originalImage != null;
        previewPane.setCompareMode(comparing);
        if (comparing) previewPane.showOriginal(originalImage);
        previewPane.showProcessed(processedImage);
    }

    private void setSessionImages(BufferedImage image) {
        originalImage  = image;
        processedImage = image;
    }

    // ── Filter dispatch ───────────────────────────────────────────────────

    private BufferedImage processFilter(FilterType filter) {
        return switch (filter) {
            case GRAYSCALE           -> ImageProcessor.grayscale(originalImage);
            case NEGATIVE            -> ImageProcessor.negative(originalImage);
            case BRIGHTNESS          -> ImageProcessor.brightness(originalImage, editorFilterPane.getBrightnessValue());
            case HSV                 -> ImageProcessor.hsv(originalImage, editorFilterPane.getSaturationValue(), editorFilterPane.getValueFactor());
            case FROSTED             -> ImageProcessor.frosted(originalImage);
            case CIRCULAR_FADE       -> ImageProcessor.circularFade(originalImage);
            case ALPHA_GLOBAL        -> ImageProcessor.alphaGlobal(originalImage, editorFilterPane.getAlphaValue());
            case RETRO1              -> ImageProcessor.retro1(originalImage, editorFilterPane.getRetroLevels());
            case RETRO2              -> ImageProcessor.retro2(originalImage,
                                            editorFilterPane.getRetro2Levels(),
                                            editorFilterPane.isRetro2ChannelR(),
                                            editorFilterPane.isRetro2ChannelG(),
                                            editorFilterPane.isRetro2ChannelB());
            case GRAYSCALE_QUANTIZED -> ImageProcessor.grayscaleQuantized(originalImage, editorFilterPane.getGrayQuantLevels());
            case BW_THRESHOLD        -> ImageProcessor.bwThreshold(originalImage, editorFilterPane.getThresholdValue());
            case RECOLOR             -> applyRecolorFilter();
            case STRETCH_4_BITS      -> ImageProcessor.stretch4Bits(originalImage, editorFilterPane.getStretchMode());
            case CONVOLUTION         -> ImageProcessor.convolution(originalImage, editorFilterPane.getKernel());
            case NONE                -> originalImage;
        };
    }

    private BufferedImage applyRecolorFilter() {
        Color c = editorFilterPane.getTintColor();
        int r = (int) Math.round(c.getRed()   * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue()  * 255);
        return ImageProcessor.recolor(originalImage, r, g, b);
    }

    // ── Status bar ────────────────────────────────────────────────────────

    private void setStatus(String message) { statusLabel.setText(message); }

    private void setErrorStatus(String prefix, Exception ex) {
        setStatus(prefix + ": " + ex.getMessage());
    }

    public static void main(String[] args) { launch(args); }
}
