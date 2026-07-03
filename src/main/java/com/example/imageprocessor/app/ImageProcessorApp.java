package com.example.imageprocessor.app;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import com.example.imageprocessor.app.ui.BlendingPane;
import com.example.imageprocessor.app.ui.EditorFilterPane;
import com.example.imageprocessor.app.ui.EditorPreviewPane;
import com.example.imageprocessor.app.ui.GradientGeneratorPane;
import com.example.imageprocessor.app.ui.HistogramPane;
import com.example.imageprocessor.app.ui.ImageFileChooserFactory;
import com.example.imageprocessor.app.ui.TopBar;
import com.example.imageprocessor.app.ui.TripleBlendingPane;
import com.example.imageprocessor.app.ui.WindowBar;
import com.example.imageprocessor.app.ui.WindowResizer;
import com.example.imageprocessor.domain.ConvolutionKernel;
import com.example.imageprocessor.domain.FilterType;
import com.example.imageprocessor.domain.StretchMode;
import com.example.imageprocessor.service.BufferAcumulacion_LOAD;
import com.example.imageprocessor.service.ImageIOService;
import com.example.imageprocessor.service.ImageProcessor;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * Punto de entrada de la aplicación y coordinador principal.
 * Responsabilidades:
 * - Estado de sesión: {@code originalImage} y {@code processedImage}.
 * - Construcción y cableado de los componentes de UI.
 * - Operaciones de imagen: abrir, guardar, resetear, aplicar filtros.
 * - Coordinación entre la vista ({@link EditorPreviewPane}) y los servicios.
 */
public class ImageProcessorApp extends Application {

    private static final String APP_TITLE = "ImageProcessor";
    private static final String STYLESHEET = "/com/example/imageprocessor/styles.css";
    private static final String APP_ICON = "/com/example/imageprocessor/app-icon.png";
    private static final String DEFAULT_MSG = "Carga una imagen para comenzar.";

    // ── Session state ─────────────────────────────────────────────────────
    private BufferedImage originalImage; // nunca se modifica tras la carga
    private BufferedImage processedImage; // lo que se muestra (preview en vivo o committed)
    private BufferedImage committedImage; // resultado consolidado; base para el siguiente filtro
    private String currentFormat = "—"; // origen de la imagen actual (PNG, JPG, …)

    // ── Pila de historial (undo / redo) ───────────────────────────────────
    private static final int HISTORY_LIMIT = 30;
    private final Deque<BufferedImage> undoStack = new ArrayDeque<>();
    private final Deque<BufferedImage> redoStack = new ArrayDeque<>();
    private final BooleanProperty canUndo = new SimpleBooleanProperty(false);
    private final BooleanProperty canRedo = new SimpleBooleanProperty(false);

    // ── Preview en vivo (cómputo en hilo de fondo, coalescido por generación) ─
    private final ExecutorService previewExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "live-preview");
        t.setDaemon(true);
        return t;
    });
    private final AtomicLong previewGen = new AtomicLong();

    // ── UI components ─────────────────────────────────────────────────────
    private final EditorPreviewPane previewPane = new EditorPreviewPane();
    private final EditorFilterPane editorFilterPane = new EditorFilterPane();
    private final CheckBox compareToggle = new CheckBox("Mostrar original");
    private final Label statusLabel = new Label(DEFAULT_MSG);
    private final Label historyLabel = new Label("");
    private final Label fileNameLabel = new Label("");
    private final Label zoomStatusLabel = new Label("100%");
    private final Region cursorSwatch = new Region();
    private final Label cursorLabel = new Label("");
    private final Label formatLabel = new Label("—");
    private final Label dimsLabel = new Label("—");
    private GradientGeneratorPane gradientPane;
    private HistogramPane histogramPane;
    private BlendingPane blendingPane;
    private TripleBlendingPane tripleBlendingPane;

    // ── Application lifecycle ─────────────────────────────────────────────

    @Override
    public void start(Stage stage) {
        stage.setTitle(APP_TITLE);
        stage.initStyle(StageStyle.UNDECORATED); // chrome propio (ver WindowBar)

        // ── App icon (reutilizado en la barra de título) ────────────────────
        Image appIcon = null;
        var iconUrl = getClass().getResourceAsStream(APP_ICON);
        if (iconUrl != null) {
            appIcon = new Image(iconUrl);
            stage.getIcons().add(appIcon);
        }

        gradientPane = new GradientGeneratorPane(this::loadGeneratedImageInEditor, this::setStatus);
        histogramPane = new HistogramPane(() -> processedImage, this::loadGeneratedImageInEditor, this::setStatus);
        blendingPane = new BlendingPane(
                () -> processedImage,
                () -> ImageFileChooserFactory.createOpenImageChooser().showOpenDialog(stage),
                this::loadGeneratedImageInEditor,
                this::setStatus);
        tripleBlendingPane = new TripleBlendingPane(
                () -> processedImage,
                () -> ImageFileChooserFactory.createOpenImageChooser().showOpenDialog(stage),
                () -> ImageFileChooserFactory.createOpenImageChooser().showOpenDialog(stage),
                this::loadGeneratedImageInEditor,
                this::setStatus);

        TopBar topBar = new TopBar(
                () -> openImage(stage),
                () -> saveImage(stage),
                this::resetImage,
                this::applySelectedFilter,
                this::undo,
                this::redo,
                canUndo,
                canRedo,
                previewPane::zoomIn,
                previewPane::zoomOut,
                previewPane::resetZoom,
                previewPane.getZoomIndicator(),
                compareToggle,
                fileNameLabel);

        // Barra de título personalizada (sobre la toolbar) para el chrome propio
        WindowBar windowBar = new WindowBar(stage, APP_TITLE);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setTop(new VBox(windowBar.getView(), topBar.getView()));
        root.setLeft(buildLeftPanel());
        root.setCenter(buildCenterTabs());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1400, 860);
        scene.setFill(Color.web("#141416"));
        final String cssUrl = Objects.requireNonNull(
                getClass().getResource(STYLESHEET),
                "No se encontró el stylesheet principal").toExternalForm();
        scene.getStylesheets().add(cssUrl);

        // Redimensionar arrastrando bordes/esquinas (ventana sin bordes)
        WindowResizer.install(stage, scene, 960, 620, () -> windowBar.maximizedProperty().get());

        stage.setScene(scene);
        stage.show();

        // ── Inject dark stylesheet into every sub-window (e.g. ColorPicker
        // custom-color dialog, which has its own Stage + Scene). ──────────
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                for (Window w : change.getAddedSubList()) {
                    // Skip PopupWindow subclasses (Tooltip, ContextMenu, etc.):
                    // they inherit CSS from their owner scene automatically.
                    // Only top-level Stage windows need explicit injection.
                    if (!(w instanceof Stage))
                        continue;

                    Scene subScene = w.getScene();
                    if (subScene != null) {
                        injectStylesheet(subScene, cssUrl);
                    } else {
                        // Scene not yet attached — wait for it
                        w.sceneProperty().addListener((obs2, oldS, newS) -> {
                            if (newS != null)
                                injectStylesheet(newS, cssUrl);
                        });
                    }
                }
            }
        });

        compareToggle.selectedProperty().addListener(
                (obs, old, selected) -> refreshView(selected));

        // Cargar imágenes arrastradas sobre el panel de previsualización
        previewPane.setOnImageDropped(this::loadImageFile);

        // Barra de estado: zoom % + coordenadas y RGB bajo el cursor
        previewPane.setOnZoomChanged(this::showZoom);
        previewPane.setOnCursorReadout(this::showCursorReadout);

        // Doble clic en un filtro de la lista → aplicarlo directamente
        editorFilterPane.setOnFilterActivated(this::applySelectedFilter);

        // Preview en vivo: cualquier cambio de filtro/parámetro recalcula la vista
        editorFilterPane.setOnParamChanged(this::schedulePreview);

        // Atajos de teclado: Ctrl+Z deshacer, Ctrl+Y / Ctrl+Shift+Z rehacer
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN), this::undo);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN), this::redo);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN), this::redo);
    }

    /**
     * Injects our dark stylesheet into {@code target} and forces an
     * immediate dark background on both the Scene fill and the root node's
     * inline style. The inline style is applied synchronously (before the
     * first CSS layout pass) so there is zero white-flash on sub-windows
     * such as the CustomColorDialog Stage.
     *
     * <p>
     * Note: the inline style value (#17171a) intentionally matches the
     * {@code .custom-color-dialog} rule in color-picker.css so that
     * removing the inline style (via a later CSS pass) produces no visible
     * change.
     */
    private static void injectStylesheet(Scene target, String cssUrl) {
        // 1. Darken the Scene fill first — this is the very first thing
        // rendered when a new window appears (before any node layout).
        target.setFill(Color.web("#17171a"));

        // 2. Apply the same colour directly on the root node as an inline
        // style so it takes effect in the *current* render frame.
        // Inline styles have the highest CSS specificity, but since our
        // CSS rule targets the same value this causes no visual conflict.
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
        Tab editorTab = new Tab("Editor", previewPane.getView());
        editorTab.setClosable(false);
        Tab gradientTab = new Tab("Generador", gradientPane.getView());
        gradientTab.setClosable(false);
        Tab histogramTab = new Tab("Histograma", histogramPane.getView());
        histogramTab.setClosable(false);
        Tab blendingTab = new Tab("Blending", blendingPane.getView());
        blendingTab.setClosable(false);
        Tab tripleBlendingTab = new Tab("Triple Blending", tripleBlendingPane.getView());
        tripleBlendingTab.setClosable(false);

        TabPane tabs = new TabPane(editorTab, gradientTab, histogramTab, blendingTab, tripleBlendingTab);
        tabs.getStyleClass().add("center-tabs");
        return tabs;
    }

    private HBox buildStatusBar() {
        zoomStatusLabel.getStyleClass().add("status-info");
        cursorLabel.getStyleClass().add("status-info");
        cursorSwatch.getStyleClass().add("status-swatch");
        historyLabel.getStyleClass().add("status-info");
        formatLabel.getStyleClass().add("status-info");
        dimsLabel.getStyleClass().add("status-info");

        // ── Cursor readout (swatch + "x, y rgb(…)"), visible sólo al pasar sobre la
        // imagen
        HBox cursorBox = new HBox(7, cursorSwatch, cursorLabel);
        cursorBox.setAlignment(Pos.CENTER_LEFT);
        cursorBox.visibleProperty().bind(cursorLabel.textProperty().isNotEmpty());
        cursorBox.managedProperty().bind(cursorBox.visibleProperty());
        Label cursorSep = statusSep();
        cursorSep.visibleProperty().bind(cursorBox.visibleProperty());
        cursorSep.managedProperty().bind(cursorBox.visibleProperty());

        // ── History "Pasos: N", visible sólo cuando hay historial (separador delante)
        historyLabel.visibleProperty().bind(historyLabel.textProperty().isNotEmpty());
        historyLabel.managedProperty().bind(historyLabel.visibleProperty());
        Label historySep = statusSep();
        historySep.visibleProperty().bind(historyLabel.visibleProperty());
        historySep.managedProperty().bind(historyLabel.visibleProperty());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(statusLabel, spacer,
                cursorBox, cursorSep,
                zoomStatusLabel,
                historySep, historyLabel,
                statusSep(), formatLabel, statusSep(), dimsLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 14, 10, 14));
        bar.getStyleClass().add("bottom-bar");
        return bar;
    }

    private static Label statusSep() {
        Label s = new Label("·");
        s.getStyleClass().add("status-sep");
        return s;
    }

    /** Actualiza la lectura del píxel bajo el cursor (o la oculta si es null). */
    private void showCursorReadout(EditorPreviewPane.PixelReadout p) {
        if (p == null) {
            cursorLabel.setText("");
            return;
        }
        cursorSwatch.setStyle("-fx-background-color: rgb(" + p.r() + "," + p.g() + "," + p.b() + ");");
        cursorLabel.setText(p.x() + ", " + p.y() + "   rgb(" + p.r() + ", " + p.g() + ", " + p.b() + ")");
    }

    private void showZoom(double level) {
        zoomStatusLabel.setText(Math.round(level * 100) + "%");
    }

    // ── Image operations ──────────────────────────────────────────────────

    private void openImage(Stage stage) {
        FileChooser chooser = ImageFileChooserFactory.createOpenImageChooser();
        loadImageFile(chooser.showOpenDialog(stage));
    }

    /** Carga un archivo de imagen (desde el diálogo o arrastrar-y-soltar). */
    private void loadImageFile(File file) {
        if (file == null)
            return;
        try {
            setSessionImages(ImageIOService.read(file));
            currentFormat = extensionOf(file);
            fileNameLabel.setText(file.getName());
            schedulePreview(); // refleja el filtro seleccionado (o muestra la imagen base)
            setStatus("Imagen cargada: " + file.getName());
        } catch (Exception ex) {
            setErrorStatus("Error al abrir imagen", ex);
        }
    }

    private static String extensionOf(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1
                ? name.substring(dot + 1).toUpperCase()
                : "—";
    }

    private void saveImage(Stage stage) {
        BufferedImage candidate = processedImage != null
                ? processedImage
                : gradientPane.getGeneratedImage();
        if (candidate == null) {
            setStatus("No hay imagen para guardar.");
            return;
        }

        FileChooser chooser = ImageFileChooserFactory.createSaveImageChooser();
        File file = chooser.showSaveDialog(stage);
        if (file == null)
            return;
        try {
            ImageIOService.write(candidate, file);
            setStatus("Imagen guardada en: " + file.getName());
        } catch (Exception ex) {
            setErrorStatus("Error al guardar", ex);
        }
    }

    private void resetImage() {
        if (originalImage == null) {
            setStatus("No hay imagen cargada para resetear.");
            return;
        }
        committedImage = originalImage;
        undoStack.clear();
        redoStack.clear();
        updateHistoryState();
        compareToggle.setSelected(false);
        showCommitted();
        setStatus("Imagen restablecida al estado original.");
    }

    /**
     * Consolida (commit) el filtro seleccionado: lo aplica sobre la imagen
     * base, lo apila en el historial y deja la selección en "Sin filtro" para
     * que el siguiente filtro parta del resultado recién consolidado.
     */
    private void applySelectedFilter() {
        if (originalImage == null) {
            setStatus("Carga una imagen antes de aplicar filtros.");
            return;
        }

        FilterType filter = editorFilterPane.getSelectedFilter();
        if (filter == null || filter == FilterType.NONE) {
            setStatus("Sin filtro seleccionado.");
            return;
        }
        try {
            BufferedImage result = processFilter(filter, committedImage, snapshotParams());
            pushHistory(committedImage);
            committedImage = result;
            redoStack.clear();
            updateHistoryState();
            // showCommitted() deselecciona y muestra el resultado recién consolidado,
            // de modo que el siguiente filtro parte de esta nueva base.
            showCommitted();
            setStatus("Filtro aplicado: " + filter + " · pasos: " + undoStack.size());
        } catch (Exception ex) {
            setErrorStatus("Error al aplicar filtro", ex);
        }
    }

    // ── Undo / Redo ───────────────────────────────────────────────────────

    private void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        redoStack.push(committedImage);
        committedImage = undoStack.pop();
        updateHistoryState();
        showCommitted();
        setStatus(undoStack.isEmpty()
                ? "Deshacer · imagen original"
                : "Deshacer · pasos: " + undoStack.size());
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        undoStack.push(committedImage);
        committedImage = redoStack.pop();
        updateHistoryState();
        showCommitted();
        setStatus("Rehacer · pasos: " + undoStack.size());
    }

    private void pushHistory(BufferedImage image) {
        undoStack.push(image);
        while (undoStack.size() > HISTORY_LIMIT)
            undoStack.removeLast();
    }

    private void updateHistoryState() {
        canUndo.set(!undoStack.isEmpty());
        canRedo.set(!redoStack.isEmpty());
        historyLabel.setText(undoStack.isEmpty() ? "" : "Pasos: " + undoStack.size());
    }

    /** Muestra la imagen consolidada (cancela cualquier preview en vuelo). */
    private void showCommitted() {
        editorFilterPane.clearSelection(); // no-op si ya está en NONE
        previewGen.incrementAndGet(); // invalida previews en background
        processedImage = committedImage;
        refreshView(compareToggle.isSelected());
    }

    // ── Live preview ──────────────────────────────────────────────────────

    /**
     * Recalcula el preview del filtro seleccionado sobre la imagen base, en un
     * hilo de fondo, descartando resultados obsoletos (coalescing por
     * generación) para mantener la UI fluida al arrastrar sliders.
     */
    private void schedulePreview() {
        if (originalImage == null)
            return;

        FilterType filter = editorFilterPane.getSelectedFilter();
        if (filter == null || filter == FilterType.NONE) {
            previewGen.incrementAndGet();
            processedImage = committedImage;
            refreshView(compareToggle.isSelected());
            return;
        }

        final long gen = previewGen.incrementAndGet();
        final BufferedImage base = committedImage;
        final FilterParams params = snapshotParams();

        previewExec.submit(() -> {
            if (gen != previewGen.get())
                return; // ya superado en cola → no computar
            final BufferedImage result;
            try {
                result = processFilter(filter, base, params);
            } catch (Exception ex) {
                return; // entrada inválida transitoria → ignorar
            }
            if (gen != previewGen.get())
                return; // resultado obsoleto
            Platform.runLater(() -> {
                if (gen != previewGen.get())
                    return;
                processedImage = result;
                refreshView(compareToggle.isSelected());
            });
        });
    }

    private void loadGeneratedImageInEditor(BufferedImage image) {
        setSessionImages(image);
        currentFormat = "GEN";
        fileNameLabel.setText("Sin título");
        schedulePreview(); // refleja el filtro seleccionado (o muestra la imagen base)
    }

    // ── View coordination ─────────────────────────────────────────────────

    /**
     * Sincroniza la vista con el estado de sesión.
     * Delega la lógica de compare mode y zoom en {@link EditorPreviewPane}.
     */
    private void refreshView(boolean showOriginal) {
        boolean comparing = showOriginal && originalImage != null;
        previewPane.setCompareMode(comparing);
        if (comparing)
            previewPane.showOriginal(originalImage);
        previewPane.showProcessed(processedImage);
        updateImageInfo();
    }

    /** Refresca los indicadores de formato y dimensiones de la barra de estado. */
    private void updateImageInfo() {
        if (processedImage != null) {
            formatLabel.setText(currentFormat);
            dimsLabel.setText(processedImage.getWidth() + " × " + processedImage.getHeight() + " px");
        } else {
            formatLabel.setText("—");
            dimsLabel.setText("—");
        }
    }

    private void setSessionImages(BufferedImage image) {
        originalImage = image;
        processedImage = image;
        committedImage = image;
        undoStack.clear();
        redoStack.clear();
        updateHistoryState();
    }

    // ── Filter dispatch ───────────────────────────────────────────────────

    /** Snapshot inmutable de los parámetros de la UI (leído en el hilo FX). */
    private record FilterParams(
            int brightness, float saturation, float valueFactor,
            int retroLevels, int retro2Levels, boolean r2r, boolean r2g, boolean r2b,
            int grayQuantLevels, int threshold, int alpha,
            int tintR, int tintG, int tintB,
            StretchMode stretchMode, ConvolutionKernel kernel) {
    }

    /** Captura los parámetros actuales — debe llamarse en el hilo de JavaFX. */
    private FilterParams snapshotParams() {
        Color c = editorFilterPane.getTintColor();
        return new FilterParams(
                editorFilterPane.getBrightnessValue(),
                editorFilterPane.getSaturationValue(),
                editorFilterPane.getValueFactor(),
                editorFilterPane.getRetroLevels(),
                editorFilterPane.getRetro2Levels(),
                editorFilterPane.isRetro2ChannelR(),
                editorFilterPane.isRetro2ChannelG(),
                editorFilterPane.isRetro2ChannelB(),
                editorFilterPane.getGrayQuantLevels(),
                editorFilterPane.getThresholdValue(),
                editorFilterPane.getAlphaValue(),
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255),
                editorFilterPane.getStretchMode(),
                editorFilterPane.getKernel());
    }

    /**
     * Aplica {@code filter} sobre {@code base} usando el snapshot {@code p}.
     * Es estático y puro: no toca estado de sesión, por lo que puede ejecutarse
     * con seguridad fuera del hilo de JavaFX (preview en background).
     */
    private static BufferedImage processFilter(FilterType filter, BufferedImage base, FilterParams p) {
        return switch (filter) {
            case GRAYSCALE -> ImageProcessor.grayscale(base);
            case NEGATIVE -> ImageProcessor.negative(base);
            case BRIGHTNESS -> ImageProcessor.brightness(base, p.brightness());
            case HSV -> ImageProcessor.hsv(base, p.saturation(), p.valueFactor());
            case FROSTED -> ImageProcessor.frosted(base);
            case CIRCULAR_FADE -> ImageProcessor.circularFade(base);
            case ALPHA_GLOBAL -> ImageProcessor.alphaGlobal(base, p.alpha());
            case RETRO1 -> ImageProcessor.retro1(base, p.retroLevels());
            case RETRO2 -> ImageProcessor.retro2(base, p.retro2Levels(), p.r2r(), p.r2g(), p.r2b());
            case GRAYSCALE_QUANTIZED -> ImageProcessor.grayscaleQuantized(base, p.grayQuantLevels());
            case BW_THRESHOLD -> ImageProcessor.bwThreshold(base, p.threshold());
            case RECOLOR -> ImageProcessor.recolor(base, p.tintR(), p.tintG(), p.tintB());
            case BUFFER_ACUMULATION_ADD -> ImageProcessor.bufferAccumulation(base, BufferAcumulacion_LOAD.Mode.ADD);
            case BUFFER_ACUMULATION_ACUM -> ImageProcessor.bufferAccumulation(base, BufferAcumulacion_LOAD.Mode.ACUM);
            case BUFFER_ACUMULATION_MULT -> ImageProcessor.bufferAccumulation(base, BufferAcumulacion_LOAD.Mode.MULT);
            case STRETCH_4_BITS -> ImageProcessor.stretch4Bits(base, p.stretchMode());
            case CONVOLUTION -> ImageProcessor.convolution(base, p.kernel());
            // ── Color Matrix ──────────────────────────────────────────────
            case SEPIA -> ImageProcessor.sepia(base);
            case COOL_TONE -> ImageProcessor.coolTone(base);
            case WARM_TONE -> ImageProcessor.warmTone(base);
            case POLAROID -> ImageProcessor.polaroid(base);
            case KODACHROME -> ImageProcessor.kodachrome(base);
            case NONE -> base;
        };
    }

    // ── Status bar ────────────────────────────────────────────────────────

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void setErrorStatus(String prefix, Exception ex) {
        setStatus(prefix + ": " + ex.getMessage());
    }

    @Override
    public void stop() {
        previewExec.shutdownNow();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
