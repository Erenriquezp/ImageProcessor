package io.github.erenriquezp.rora.app;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import io.github.erenriquezp.rora.app.fx.FxImages;
import io.github.erenriquezp.rora.app.session.CurrentImage;
import io.github.erenriquezp.rora.app.session.EditSession;
import io.github.erenriquezp.rora.app.session.FilterParams;
import io.github.erenriquezp.rora.app.session.ImageIo;
import io.github.erenriquezp.rora.app.session.RenderCoordinator;
import io.github.erenriquezp.rora.app.session.RenderFailure;
import io.github.erenriquezp.rora.app.session.RenderLane;
import io.github.erenriquezp.rora.app.session.Scheduler;
import io.github.erenriquezp.rora.app.session.SessionSource;
import io.github.erenriquezp.rora.app.session.StallMonitor;
import io.github.erenriquezp.rora.app.ui.BlendingPane;
import io.github.erenriquezp.rora.app.ui.EditorFilterPane;
import io.github.erenriquezp.rora.app.ui.EditorPreviewPane;
import io.github.erenriquezp.rora.app.ui.GradientGeneratorPane;
import io.github.erenriquezp.rora.app.ui.HistogramPane;
import io.github.erenriquezp.rora.app.ui.ImageFileChooserFactory;
import io.github.erenriquezp.rora.app.ui.TopBar;
import io.github.erenriquezp.rora.app.ui.TripleBlendingPane;
import io.github.erenriquezp.rora.app.ui.WindowBar;
import io.github.erenriquezp.rora.app.ui.WindowResizer;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.domain.FilterType;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
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
import javafx.util.Duration;

/**
 * Punto de entrada de la aplicación y coordinador principal.
 * Responsabilidades:
 * - Estado de sesión: delegado en {@link EditSession} (receta, historial, proxy).
 * - Construcción y cableado de los componentes de UI.
 * - Operaciones de imagen: abrir, guardar, resetear, aplicar filtros.
 * - Coordinación entre la vista ({@link EditorPreviewPane}) y los servicios.
 */
public class ImageProcessorApp extends Application {

    private static final String APP_TITLE = "ImageProcessor";
    private static final String STYLESHEET = "/io/github/erenriquezp/rora/styles.css";
    private static final String APP_ICON = "/io/github/erenriquezp/rora/app-icon.png";
    private static final String DEFAULT_MSG = "Carga una imagen para comenzar.";

    // ── Session state ─────────────────────────────────────────────────────
    private static final System.Logger LOG = System.getLogger("rora.app");
    private static final long SHOW_PROGRESS_AFTER_NANOS = 300_000_000L;

    private EditSession<WritableImage> session;
    private String currentFormat = "—"; // origen de la imagen actual (PNG, JPG, …)
    private EditSession.Frame<WritableImage> lastFrame;
    private String originalShownFor = ""; // qué versión de la original muestra el panel izquierdo

    private final BooleanProperty canUndo = new SimpleBooleanProperty(false);
    private final BooleanProperty canRedo = new SimpleBooleanProperty(false);

    // ── Trabajo en segundo plano ──────────────────────────────────────────
    private final ExecutorService io =
            RenderCoordinator.singleThread("rora-io", Thread.NORM_PRIORITY);
    private long loadGeneration; // solo la última carga pedida llega a la sesión
    private final PauseTransition viewportDebounce = new PauseTransition(Duration.millis(150));
    private final PauseTransition diagnosticDebounce = new PauseTransition(Duration.millis(150));
    private OpSpec diagnosticSpec;
    private RenderLane.Ticket diagnosticTicket;
    private long renderStartedAt;
    private boolean showingProgress;
    private String restingStatus = DEFAULT_MSG;
    private StallMonitor stallMonitor;

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

        session = EditSession.create(
                Platform::runLater,
                img -> FxImages.toFx(img, previewPane.processedPool(), FxImages.configuredFormat()),
                true);
        session.addListener(new EditSession.Listener<>() {
            @Override
            public void frameReady(EditSession.Frame<WritableImage> frame) {
                onFrameReady(frame);
            }

            @Override
            public void historyChanged(int depth, boolean undoable, boolean redoable) {
                canUndo.set(undoable);
                canRedo.set(redoable);
                historyLabel.setText(depth == 0 ? "" : "Pasos: " + depth);
            }

            @Override
            public void failed(RenderFailure failure) {
                setStatus("Error al procesar la imagen: " + failure.message());
            }

            @Override
            public void progress(double fraction) {
                onProgress(fraction);
            }
        });
        startStallMonitorIfEnabled();

        CurrentImage currentImage = new CurrentImage() {
            @Override
            public boolean isAvailable() {
                return session.hasImage();
            }

            @Override
            public void fetch(Consumer<BufferedImage> onImage, Consumer<RenderFailure> onFailed) {
                session.withCurrentImage(onImage, onFailed);
            }
        };

        gradientPane = new GradientGeneratorPane(this::loadGeneratedImageInEditor, this::setStatus);
        histogramPane = new HistogramPane(currentImage, this::loadGeneratedImageInEditor, this::setStatus);
        blendingPane = new BlendingPane(
                currentImage,
                () -> ImageFileChooserFactory.createOpenImageChooser().showOpenDialog(stage),
                this::loadGeneratedImageInEditor,
                this::setStatus);
        tripleBlendingPane = new TripleBlendingPane(
                currentImage,
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
                (obs, old, selected) -> refreshCompare());

        // El proxy depende del visor: los cambios de tamaño y zoom se agrupan
        viewportDebounce.setOnFinished(e -> pushViewport());
        previewPane.setOnViewportChanged(viewportDebounce::playFromStart);
        diagnosticDebounce.setOnFinished(e -> requestDiagnostic());
        previewPane.setOnPixelPicked(this::pickExactPixel);

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
        cursorLabel.setText((p.approximate() ? "≈ " : "")
                + p.x() + ", " + p.y() + "   rgb(" + p.r() + ", " + p.g() + ", " + p.b() + ")");
    }

    private void showZoom(double level) {
        zoomStatusLabel.setText(Math.round(level * 100) + "%");
    }

    // ── Image operations ──────────────────────────────────────────────────

    private void openImage(Stage stage) {
        FileChooser chooser = ImageFileChooserFactory.createOpenImageChooser();
        loadImageFile(chooser.showOpenDialog(stage));
    }

    /** Carga un archivo de imagen (desde el diálogo o arrastrar-y-soltar) fuera del hilo FX. */
    private void loadImageFile(File file) {
        if (file == null)
            return;
        long generation = ++loadGeneration;
        setStatus("Abriendo " + file.getName() + "…");
        runIo(() -> ImageIo.load(file),
                source -> {
                    if (generation != loadGeneration)
                        return; // el usuario ya pidió otra imagen
                    installSource(source, extensionOf(file), file.getName());
                    setStatus("Imagen cargada: " + file.getName());
                },
                ex -> setErrorStatus("Error al abrir imagen", ex));
    }

    /** Ejecuta {@code work} en una {@link Task} y publica el resultado en el hilo FX. */
    private <T> void runIo(Callable<T> work, Consumer<T> onDone, Consumer<Throwable> onFailed) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> onDone.accept(task.getValue()));
        task.setOnFailed(e -> {
            LOG.log(System.Logger.Level.WARNING, "Fallo de E/S", task.getException());
            onFailed.accept(task.getException());
        });
        io.execute(task);
    }

    /** Pone {@code source} como imagen del editor. */
    private void installSource(SessionSource source, String format, String title) {
        pushViewport();
        lastFrame = null;
        session.load(source);
        currentFormat = format;
        fileNameLabel.setText(title);
        updateImageInfo();
        refreshCompare();
        schedulePreview(); // refleja el filtro seleccionado (o muestra la imagen base)
    }

    private static String extensionOf(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1
                ? name.substring(dot + 1).toUpperCase()
                : "—";
    }

    private void saveImage(Stage stage) {
        BufferedImage generated = gradientPane.getGeneratedImage();
        if (!session.hasImage() && generated == null) {
            setStatus("No hay imagen para guardar.");
            return;
        }

        FileChooser chooser = ImageFileChooserFactory.createSaveImageChooser();
        File file = chooser.showSaveDialog(stage);
        if (file == null)
            return;
        setStatus("Guardando " + file.getName() + "…");
        if (session.hasImage()) {
            session.export(
                    image -> writeImage(image, file),
                    failure -> setStatus("Error al guardar: " + failure.message()));
        } else {
            writeImage(generated, file);
        }
    }

    private void writeImage(BufferedImage image, File file) {
        runIo(() -> {
                    ImageIo.save(image, file);
                    return file;
                },
                f -> setStatus("Imagen guardada en: " + f.getName()),
                ex -> setErrorStatus("Error al guardar", ex));
    }

    private void resetImage() {
        if (!session.hasImage()) {
            setStatus("No hay imagen cargada para resetear.");
            return;
        }
        markRenderStart();
        session.reset();
        compareToggle.setSelected(false);
        editorFilterPane.clearSelection();
        setStatus("Imagen restablecida al estado original.");
    }

    /**
     * Consolida el filtro seleccionado: lo añade a la receta (una entrada de historial) y deja la
     * selección en "Sin filtro" para que el siguiente filtro parta del resultado consolidado.
     */
    private void applySelectedFilter() {
        if (!session.hasImage()) {
            setStatus("Carga una imagen antes de aplicar filtros.");
            return;
        }

        FilterType filter = editorFilterPane.getSelectedFilter();
        if (filter == null || filter == FilterType.NONE) {
            setStatus("Sin filtro seleccionado.");
            return;
        }
        try {
            OpSpec step = session.specs().of(filter, snapshotParams().withNoHighlights()).orElseThrow();
            markRenderStart();
            int depth = session.apply(step);
            editorFilterPane.clearSelection();
            setStatus("Filtro aplicado: " + filter + " · pasos: " + depth);
        } catch (RuntimeException ex) {
            setErrorStatus("Error al aplicar filtro", ex);
        }
    }

    // ── Undo / Redo ───────────────────────────────────────────────────────

    private void undo() {
        markRenderStart();
        if (!session.undo()) {
            return;
        }
        editorFilterPane.clearSelection();
        setStatus(session.undoDepth() == 0
                ? "Deshacer · imagen original"
                : "Deshacer · pasos: " + session.undoDepth());
    }

    private void redo() {
        markRenderStart();
        if (!session.redo()) {
            return;
        }
        editorFilterPane.clearSelection();
        setStatus("Rehacer · pasos: " + session.undoDepth());
    }

    // ── Live preview ──────────────────────────────────────────────────────

    /** Traduce el filtro seleccionado a paso pendiente; la sesión lo renderiza en segundo plano. */
    private void schedulePreview() {
        if (!session.hasImage())
            return;

        FilterType filter = editorFilterPane.getSelectedFilter();
        markRenderStart();
        if (filter == null || filter == FilterType.NONE) {
            cancelDiagnostic();
            session.setPending(Optional.empty());
            return;
        }
        FilterParams params = snapshotParams();
        session.setPending(session.specs().of(filter, params));
        if (filter == FilterType.HISTOGRAM_EQUALIZE) {
            // Exacto sobre la imagen completa y sin marcas; "cargando" hasta tenerlo.
            editorFilterPane.showEqualizeDiagnosticLoading();
            diagnosticSpec = session.specs().of(filter, params.withNoHighlights()).orElseThrow();
            diagnosticDebounce.playFromStart();
        } else {
            cancelDiagnostic();
        }
    }

    private void requestDiagnostic() {
        if (diagnosticSpec == null || !session.hasImage())
            return;
        diagnosticTicket = session.exposureDiagnostic(
                diagnosticSpec,
                editorFilterPane::updateEqualizeDiagnostic,
                failure -> setStatus("Error en el diagnóstico: " + failure.message()));
    }

    private void cancelDiagnostic() {
        diagnosticDebounce.stop();
        diagnosticSpec = null;
        if (diagnosticTicket != null) {
            diagnosticTicket.cancel();
            diagnosticTicket = null;
        }
    }

    private void loadGeneratedImageInEditor(BufferedImage image) {
        runIo(() -> SessionSource.of(image),
                source -> installSource(source, "GEN", "Sin título"),
                ex -> setErrorStatus("Error al cargar la imagen", ex));
    }

    /** Clic en el resultado: valor exacto del píxel (de la caché o con un render completo). */
    private void pickExactPixel(int x, int y) {
        if (!session.hasImage())
            return;
        session.exactPixel(x, y,
                argb -> showCursorReadout(new EditorPreviewPane.PixelReadout(
                        x, y, (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, false)),
                failure -> setStatus("Error al leer el píxel: " + failure.message()));
    }

    // ── View coordination ─────────────────────────────────────────────────

    private void onFrameReady(EditSession.Frame<WritableImage> frame) {
        lastFrame = frame;
        previewPane.showProcessed(frame);
        updateImageInfo();
        if (showingProgress) {
            showingProgress = false;
            statusLabel.setText(restingStatus);
        }
        refreshCompare();
    }

    /** Sincroniza el modo comparar y pide la original si lo mostrado ya no le corresponde. */
    private void refreshCompare() {
        boolean comparing = compareToggle.isSelected() && session.hasImage();
        previewPane.setCompareMode(comparing);
        if (!comparing || lastFrame == null) {
            return;
        }
        boolean fullResolution = !lastFrame.approximate() && session.proxySize().isPresent();
        String wanted = session.source().orElseThrow().id() + ":" + lastFrame.pixels().width()
                + ":" + fullResolution;
        if (wanted.equals(originalShownFor)) {
            return;
        }
        originalShownFor = wanted;
        session.original(
                fullResolution,
                img -> FxImages.toFx(img, previewPane.originalPool(), FxImages.configuredFormat()),
                previewPane::showOriginal,
                failure -> {
                    originalShownFor = "";
                    setStatus("Error al mostrar la original: " + failure.message());
                });
    }

    private void pushViewport() {
        int width = previewPane.viewportWidth();
        int height = previewPane.viewportHeight();
        if (width > 0 && height > 0) {
            session.setViewport(width, height, previewPane.outputScale(), previewPane.effectiveZoom());
            refreshCompare();
        }
    }

    private void markRenderStart() {
        renderStartedAt = System.nanoTime();
    }

    /** Progreso agrupado (≤ 30 Hz): solo si el render tarda lo bastante para que se note. */
    private void onProgress(double fraction) {
        if (showingProgress || System.nanoTime() - renderStartedAt > SHOW_PROGRESS_AFTER_NANOS) {
            showingProgress = true;
            statusLabel.setText("Procesando… " + Math.round(fraction * 100) + " %");
        }
    }

    /** Refresca los indicadores de formato y dimensiones de la barra de estado. */
    private void updateImageInfo() {
        Optional<SessionSource> source = session.source();
        if (source.isPresent()) {
            formatLabel.setText(currentFormat);
            dimsLabel.setText(source.get().width() + " × " + source.get().height() + " px");
        } else {
            formatLabel.setText("—");
            dimsLabel.setText("—");
        }
    }

    // ── Filter parameters ─────────────────────────────────────────────────

    /** Captura los parámetros actuales — debe llamarse en el hilo de JavaFX. */
    private FilterParams snapshotParams() {
        Color c = editorFilterPane.getTintColor();
        Color plane = editorFilterPane.getPlaneColor();
        Color fog = editorFilterPane.getFarFogColor();
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
                editorFilterPane.getKernel(),
                editorFilterPane.getDepthSource(),
                editorFilterPane.getDepthThreshold(),
                editorFilterPane.getPixelBlockSize(),
                editorFilterPane.getTextureScale(),
                editorFilterPane.getTextureFilterMode(),
                editorFilterPane.getAlphaTestThreshold(),
                editorFilterPane.isAlphaTestUseLuminance(),
                editorFilterPane.getMultisampleSize(),
                editorFilterPane.getStencilPattern(),
                editorFilterPane.getFragmentBlendMode(),
                editorFilterPane.getFragmentBlendAlpha(),
                editorFilterPane.getLogicOpType(),
                editorFilterPane.getLogicMask(),
                (int) Math.round(plane.getRed() * 255),
                (int) Math.round(plane.getGreen() * 255),
                (int) Math.round(plane.getBlue() * 255),
                (int) Math.round(fog.getRed() * 255),
                (int) Math.round(fog.getGreen() * 255),
                (int) Math.round(fog.getBlue() * 255),
                editorFilterPane.getEqualizeMode(),
                editorFilterPane.getGainR(),
                editorFilterPane.getGainG(),
                editorFilterPane.getGainB(),
                editorFilterPane.getLerpFactor(),
                editorFilterPane.getExtrapolateFactor(),
                editorFilterPane.getScaleValue(),
                editorFilterPane.getBiasValue(),
                editorFilterPane.getPointThreshold(),
                editorFilterPane.getSoftThresholdWidth(),
                editorFilterPane.getPointSaturation(),
                editorFilterPane.getHueRotation(),
                editorFilterPane.getColorSpaceType(),
                editorFilterPane.getEqualizeIntensity(),
                editorFilterPane.isEqualizeMarkBurned(),
                editorFilterPane.isEqualizeMarkDark());
    }

    // ── Status bar ────────────────────────────────────────────────────────

    private void setStatus(String message) {
        restingStatus = message;
        showingProgress = false;
        statusLabel.setText(message);
    }

    private void setErrorStatus(String prefix, Throwable ex) {
        setStatus(prefix + ": " + ex.getMessage());
    }

    /** Monitor de bloqueos del hilo FX, solo con {@code -Drora.fxStallMonitor=true}. */
    private void startStallMonitorIfEnabled() {
        if (!Boolean.getBoolean("rora.fxStallMonitor")) {
            return;
        }
        Thread fxThread = Thread.currentThread();
        stallMonitor = new StallMonitor(
                Scheduler.system(),
                Platform::runLater,
                () -> {
                    StringBuilder sb = new StringBuilder();
                    for (StackTraceElement frame : fxThread.getStackTrace()) {
                        sb.append("    at ").append(frame).append('\n');
                    }
                    return sb.toString();
                },
                message -> LOG.log(System.Logger.Level.WARNING, message),
                StallMonitor.DEFAULT_TICK_NANOS,
                StallMonitor.DEFAULT_THRESHOLD_NANOS);
        stallMonitor.start();
    }

    @Override
    public void stop() {
        if (stallMonitor != null) {
            stallMonitor.close();
        }
        gradientPane.close();
        histogramPane.close();
        blendingPane.close();
        tripleBlendingPane.close();
        session.close();
        io.shutdownNow();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
