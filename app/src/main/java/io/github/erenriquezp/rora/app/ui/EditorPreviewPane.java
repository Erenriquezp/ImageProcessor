package io.github.erenriquezp.rora.app.ui;

import io.github.erenriquezp.rora.app.fx.FxImages;
import io.github.erenriquezp.rora.app.session.EditSession;
import io.github.erenriquezp.rora.app.session.FramePool;
import io.github.erenriquezp.rora.core.image.Img;
import javafx.beans.binding.Bindings;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Panel de previsualización central.
 * Responsabilidades:
 *  - Muestra la imagen procesada (siempre) y la original (modo comparar).
 *  - Gestiona el modo comparar (50/50) con borde de foco en el panel activo.
 *  - Zoom independiente por panel vía rueda del ratón o botones externos.
 *  - Expone {@link #getZoomIndicator()} para que la TopBar lo embeba.
 */
public class EditorPreviewPane {

    // ── Image views ───────────────────────────────────────────────────────
    private final ImageView processedView = new ImageView();
    private final ImageView originalView  = new ImageView();

    // ── Layout ────────────────────────────────────────────────────────────
    private final ScrollPane leftScrollPane;
    private final ScrollPane rightScrollPane;
    private final StackPane  leftContent;
    private final StackPane  rightContent;
    private final Region     compareDivider;
    private final Label      compareResultLabel;
    private final HBox       editorHBox;

    // ── Zoom ──────────────────────────────────────────────────────────────
    private double  leftZoomLevel  = 1.0;
    private double  rightZoomLevel = 1.0;
    private boolean leftPaneActive = false;
    private boolean compareModeOn  = false;

    private static final double ZOOM_MIN  = 1.0;
    private static final double ZOOM_MAX  = 8.0;
    private static final double ZOOM_STEP = 0.20;

    private final Label zoomIndicator = new Label("Ajustar");

    // ── Status readouts (cursor pixel + zoom) ─────────────────────────────
    /** Lectura de un píxel bajo el cursor: posición y color RGB. */
    public record PixelReadout(int x, int y, int r, int g, int b, boolean approximate) {}

    /** Píxel de la imagen completa sobre el que se ha hecho clic. */
    @FunctionalInterface
    public interface PixelPick { void pick(int x, int y); }
    private PixelPick onPixelPicked;

    // ── Frames mostrados (píxeles para el cuentagotas) y reutilización de imágenes ─
    private final FramePool<WritableImage> processedPool = FxImages.newPool();
    private final FramePool<WritableImage> originalPool  = FxImages.newPool();
    private EditSession.Frame<WritableImage> processedFrame;
    private EditSession.Frame<WritableImage> originalFrame;
    private Runnable onViewportChanged;
    private Consumer<PixelReadout> onCursorReadout;   // null cuando el cursor sale de la imagen
    private DoubleConsumer         onZoomChanged;

    // ── Empty state & drag-and-drop ───────────────────────────────────────
    private static final String DRAGOVER_CLASS = "preview-scroll-pane-dragover";
    private final VBox emptyState = buildEmptyState();
    private Consumer<File> onImageDropped;

    // ── Constructor ───────────────────────────────────────────────────────

    public EditorPreviewPane() {
        configureImageView(processedView);
        configureImageView(originalView);

        // LEFT content — imagen original (solo en modo comparar)
        // El tablero (checker) va detrás de la imagen para revelar la
        // transparencia (filtros alpha) en lugar de ocultarla sobre negro.
        Label originalBadge = makeCompareBadge("ORIGINAL", Pos.TOP_LEFT);
        leftContent = new StackPane(makeCheckerBacking(originalView), originalView, originalBadge);
        leftContent.getStyleClass().addAll("preview-pane", "compare-pane");

        // RIGHT content — imagen procesada (siempre visible) + placeholder vacío
        compareResultLabel = makeCompareBadge("RESULTADO", Pos.TOP_RIGHT);
        compareResultLabel.setVisible(false);
        rightContent = new StackPane(makeCheckerBacking(processedView), processedView,
                emptyState, compareResultLabel);
        rightContent.getStyleClass().addAll("preview-pane", "compare-pane");

        leftScrollPane  = buildScrollPane(leftContent);
        rightScrollPane = buildScrollPane(rightContent);

        // Viewport resize → refrescar dimensiones de zoom
        leftScrollPane .viewportBoundsProperty().addListener((obs, o, b) -> updateZoomDimensions());
        rightScrollPane.viewportBoundsProperty().addListener((obs, o, b) -> {
            updateZoomDimensions();
            fireViewportChanged();
        });
        FxImages.bindPulses(processedView, processedPool);
        FxImages.bindPulses(originalView, originalPool);

        // Rueda del ratón — cada panel zooms de forma independiente
        leftScrollPane .addEventFilter(ScrollEvent.SCROLL, e -> { activatePane(true);  onScrollZoom(e, true);  });
        rightScrollPane.addEventFilter(ScrollEvent.SCROLL, e -> { activatePane(false); onScrollZoom(e, false); });

        // Click → activar panel para los botones de toolbar
        leftContent .setOnMouseClicked(e -> activatePane(true));
        rightContent.setOnMouseClicked(e -> activatePane(false));

        // Lectura de píxel bajo el cursor (coordenadas + RGB) para la barra de estado
        processedView.setOnMouseMoved(e -> fireReadout(e, processedView));
        originalView .setOnMouseMoved(e -> fireReadout(e, originalView));
        processedView.setOnMouseExited(e -> clearReadout());
        // Clic sobre el resultado → valor exacto del píxel (además de activar el panel)
        processedView.setOnMouseClicked(this::firePick);
        originalView .setOnMouseExited(e -> clearReadout());

        // Arrastrar y soltar una imagen sobre el panel de resultado
        enableDragAndDrop(rightScrollPane);

        // Estado inicial: panel izquierdo colapsado, derecho crece
        leftScrollPane.setMinWidth(0);
        leftScrollPane.setPrefWidth(0);
        leftScrollPane.setMaxWidth(0);
        HBox.setHgrow(rightScrollPane, Priority.ALWAYS);

        compareDivider = new Region();
        compareDivider.setMinWidth(0);
        compareDivider.setPrefWidth(0);
        compareDivider.setMaxWidth(0);
        compareDivider.getStyleClass().add("compare-divider");

        editorHBox = new HBox(leftScrollPane, compareDivider, rightScrollPane);
        editorHBox.setSpacing(0);
        editorHBox.getStyleClass().add("editor-hbox");
    }

    // ── Public API ────────────────────────────────────────────────────────

    /** Devuelve el nodo raíz de este panel (para embeber en un Tab). */
    public Node getView() { return editorHBox; }

    /** Label de porcentaje de zoom — se embebe en la TopBar. */
    public Label getZoomIndicator() { return zoomIndicator; }

    /** Callback con la lectura del píxel bajo el cursor (o {@code null} al salir). */
    public void setOnCursorReadout(Consumer<PixelReadout> handler) { this.onCursorReadout = handler; }

    /** Callback con el nivel de zoom del panel activo (1.0 = 100%). */
    public void setOnZoomChanged(DoubleConsumer handler) {
        this.onZoomChanged = handler;
        if (handler != null) handler.accept(leftPaneActive ? leftZoomLevel : rightZoomLevel);
    }

    // ── Cursor pixel readout ──────────────────────────────────────────────

    private void clearReadout() {
        if (onCursorReadout != null) onCursorReadout.accept(null);
    }

    /**
     * Mapea la posición del ratón al píxel y reporta su color: las coordenadas son las de la imagen
     * completa; el color sale de los píxeles del frame (marcado como aproximado si es el proxy).
     */
    private void fireReadout(MouseEvent e, ImageView view) {
        if (onCursorReadout == null) return;
        EditSession.Frame<WritableImage> frame = view == processedView ? processedFrame : originalFrame;
        double dispW = view.getBoundsInLocal().getWidth();
        double dispH = view.getBoundsInLocal().getHeight();
        if (frame == null || dispW <= 0 || dispH <= 0) { onCursorReadout.accept(null); return; }

        Img pixels = frame.pixels();
        int sx = clampIndex(e.getX() / dispW * pixels.width(),  pixels.width());
        int sy = clampIndex(e.getY() / dispH * pixels.height(), pixels.height());
        int argb = pixels.argb(sx, sy);
        onCursorReadout.accept(new PixelReadout(
                clampIndex(e.getX() / dispW * frame.fullWidth(),  frame.fullWidth()),
                clampIndex(e.getY() / dispH * frame.fullHeight(), frame.fullHeight()),
                (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF,
                frame.approximate()));
    }

    private void firePick(MouseEvent e) {
        EditSession.Frame<WritableImage> frame = processedFrame;
        double dispW = processedView.getBoundsInLocal().getWidth();
        double dispH = processedView.getBoundsInLocal().getHeight();
        if (onPixelPicked == null || frame == null || dispW <= 0 || dispH <= 0
                || !e.isStillSincePress()) {
            return;
        }
        onPixelPicked.pick(
                clampIndex(e.getX() / dispW * frame.fullWidth(),  frame.fullWidth()),
                clampIndex(e.getY() / dispH * frame.fullHeight(), frame.fullHeight()));
    }

    private static int clampIndex(double v, int size) {
        return Math.max(0, Math.min(size - 1, (int) v));
    }

    /** Callback con el píxel de la imagen completa sobre el que se hace clic. */
    public void setOnPixelPicked(PixelPick handler) { this.onPixelPicked = handler; }

    /** Callback cuando cambia el tamaño del visor o el zoom (para recalcular el proxy). */
    public void setOnViewportChanged(Runnable handler) { this.onViewportChanged = handler; }

    private void fireViewportChanged() {
        if (onViewportChanged != null) onViewportChanged.run();
    }

    /** Ancho del visor en píxeles lógicos (0 antes de la primera maquetación). */
    public int viewportWidth() {
        Bounds b = rightScrollPane.getViewportBounds();
        return b == null ? 0 : (int) Math.ceil(b.getWidth());
    }

    /** Alto del visor en píxeles lógicos. */
    public int viewportHeight() {
        Bounds b = rightScrollPane.getViewportBounds();
        return b == null ? 0 : (int) Math.ceil(b.getHeight());
    }

    /** Escala DPI de la ventana. */
    public double outputScale() {
        var scene = editorHBox.getScene();
        return scene == null || scene.getWindow() == null ? 1.0 : scene.getWindow().getOutputScaleX();
    }

    /** Zoom efectivo: el mayor de los paneles visibles. */
    public double effectiveZoom() {
        return compareModeOn ? Math.max(leftZoomLevel, rightZoomLevel) : rightZoomLevel;
    }

    /** Pool del panel de resultado (el worker escribe en estas imágenes). */
    public FramePool<WritableImage> processedPool() { return processedPool; }

    /** Pool del panel original. */
    public FramePool<WritableImage> originalPool() { return originalPool; }

    /**
     * Registra el callback invocado cuando el usuario suelta un archivo de
     * imagen sobre el panel. Lo cablea {@code ImageProcessorApp}.
     */
    public void setOnImageDropped(Consumer<File> handler) { this.onImageDropped = handler; }

    /** Actualiza el panel de resultado ({@code null} si no hay imagen). */
    public void showProcessed(EditSession.Frame<WritableImage> frame) {
        processedFrame = frame;
        processedView.setImage(frame == null ? null : frame.image());
        if (frame != null) processedPool.displayed(frame.image());
        emptyState.setVisible(frame == null);
    }

    /** Actualiza el panel original (solo relevante en modo comparar). */
    public void showOriginal(EditSession.Frame<WritableImage> frame) {
        originalFrame = frame;
        originalView.setImage(frame == null ? null : frame.image());
        if (frame != null) originalPool.displayed(frame.image());
    }

    /**
     * Activa o desactiva el modo comparar.
     * Activado: ambos paneles al 50 % con zoom y borde de foco independientes.
     * Desactivado: sólo el panel derecho ocupa todo el espacio.
     */
    public void setCompareMode(boolean on) {
        compareModeOn = on;

        if (on) {
            leftScrollPane.prefWidthProperty().unbind();
            rightScrollPane.prefWidthProperty().unbind();

            leftScrollPane .prefWidthProperty().bind(editorHBox.widthProperty().subtract(2).divide(2));
            rightScrollPane.prefWidthProperty().bind(editorHBox.widthProperty().subtract(2).divide(2));
            leftScrollPane.setMaxWidth(Double.MAX_VALUE);

            compareDivider.setMinWidth(2);
            compareDivider.setPrefWidth(2);
            compareDivider.setMaxWidth(2);

            compareResultLabel.setVisible(true);
            activatePane(false); // el panel derecho (resultado) queda activo por defecto
        } else {
            leftScrollPane.prefWidthProperty().unbind();
            rightScrollPane.prefWidthProperty().unbind();

            leftScrollPane.setPrefWidth(0);
            leftScrollPane.setMaxWidth(0);

            compareDivider.setMinWidth(0);
            compareDivider.setPrefWidth(0);
            compareDivider.setMaxWidth(0);

            compareResultLabel.setVisible(false);
            leftPaneActive = false;
            updateFocusRing();
        }
    }

    // —— Zoom — API pública (delegada desde TopBar / teclado) ─────────────

    public void zoomIn() {
        if (leftPaneActive) setLeftZoom(leftZoomLevel  * (1.0 + ZOOM_STEP));
        else                setRightZoom(rightZoomLevel * (1.0 + ZOOM_STEP));
    }

    public void zoomOut() {
        if (leftPaneActive) setLeftZoom(leftZoomLevel  / (1.0 + ZOOM_STEP));
        else                setRightZoom(rightZoomLevel / (1.0 + ZOOM_STEP));
    }

    public void resetZoom() {
        if (leftPaneActive) setLeftZoom(1.0);
        else                setRightZoom(1.0);
    }

    // ── Zoom — internals ─────────────────────────────────────────────────

    /** Marca el panel indicado como activo y actualiza borde + indicador. */
    private void activatePane(boolean isLeft) {
        leftPaneActive = isLeft;
        updateFocusRing();
        updateZoomIndicator();
    }

    /** Aplica / retira el borde de acento. Solo visible en modo comparar. */
    private void updateFocusRing() {
        leftScrollPane .getStyleClass().remove("preview-scroll-pane-focused");
        rightScrollPane.getStyleClass().remove("preview-scroll-pane-focused");
        if (compareModeOn) {
            (leftPaneActive ? leftScrollPane : rightScrollPane)
                    .getStyleClass().add("preview-scroll-pane-focused");
        }
    }

    /** Sincroniza el Label de porcentaje con el zoom del panel activo. */
    private void updateZoomIndicator() {
        double level = leftPaneActive ? leftZoomLevel : rightZoomLevel;
        zoomIndicator.setText(Math.abs(level - 1.0) < 0.01
                ? "Ajustar"
                : String.format("%.0f%%", level * 100));
        if (onZoomChanged != null) onZoomChanged.accept(level);
    }

    private void onScrollZoom(ScrollEvent e, boolean isLeft) {
        double factor = e.getDeltaY() > 0 ? (1.0 + ZOOM_STEP) : (1.0 / (1.0 + ZOOM_STEP));
        if (isLeft) setLeftZoom(leftZoomLevel  * factor);
        else        setRightZoom(rightZoomLevel * factor);
        e.consume();
    }

    private void setLeftZoom(double level) {
        leftZoomLevel = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, level));
        applyZoomToPane(leftScrollPane, leftContent, originalView, leftZoomLevel);
        if (leftPaneActive) updateZoomIndicator();
        fireViewportChanged();
    }

    private void setRightZoom(double level) {
        rightZoomLevel = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, level));
        applyZoomToPane(rightScrollPane, rightContent, processedView, rightZoomLevel);
        if (!leftPaneActive) updateZoomIndicator();
        fireViewportChanged();
    }

    /** Refresca ambos paneles (p.ej. al redimensionar la ventana). */
    private void updateZoomDimensions() {
        applyZoomToPane(rightScrollPane, rightContent, processedView, rightZoomLevel);
        applyZoomToPane(leftScrollPane,  leftContent,  originalView,  leftZoomLevel);
    }

    /**
     * Aplica el nivel de zoom a un panel concreto.
     * <p>
     * zoom = 1,0 → modo fit: ScrollPane controla el tamaño del contenido
     *              (evita el bucle de retroalimentación viewport↔content).
     * zoom > 1,0 → modo explícito: el contenido supera el viewport
     *              y el ScrollPane muestra scrollbars.
     */
    private void applyZoomToPane(ScrollPane sp, StackPane content, ImageView view, double zoom) {
        if (Math.abs(zoom - 1.0) < 0.001) {
            sp.setFitToWidth(true);
            sp.setFitToHeight(true);
            content.setMinSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
            content.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
            content.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            view.fitWidthProperty().unbind();
            view.fitHeightProperty().unbind();
            view.fitWidthProperty() .bind(content.widthProperty() .subtract(16));
            view.fitHeightProperty().bind(content.heightProperty().subtract(16));
        } else {
            sp.setFitToWidth(false);
            sp.setFitToHeight(false);
            view.fitWidthProperty().unbind();
            view.fitHeightProperty().unbind();

            Bounds vp = sp.getViewportBounds();
            if (vp == null || vp.getWidth() <= 0 || vp.getHeight() <= 0) return;

            double cW = vp.getWidth()  * zoom;
            double cH = vp.getHeight() * zoom;

            content.setMinSize(cW, cH);
            content.setPrefSize(cW, cH);
            content.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

            view.setFitWidth (Math.max(1.0, cW - 16));
            view.setFitHeight(Math.max(1.0, cH - 16));
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private static void configureImageView(ImageView iv) {
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
    }

    /** Tablero de transparencia (dos grises oscuros, casillas de 8 px). */
    private static final Background CHECKER_BG = buildCheckerBackground();

    private static Background buildCheckerBackground() {
        final int s = 8, tile = s * 2;          // mosaico 16×16 = 2×2 casillas
        WritableImage img = new WritableImage(tile, tile);
        PixelWriter pw = img.getPixelWriter();
        Color light = Color.web("#42424a");
        Color dark  = Color.web("#34343b");
        for (int y = 0; y < tile; y++) {
            for (int x = 0; x < tile; x++) {
                boolean even = ((x < s) ^ (y < s));
                pw.setColor(x, y, even ? light : dark);
            }
        }
        return new Background(new BackgroundImage(img,
                BackgroundRepeat.REPEAT, BackgroundRepeat.REPEAT,
                BackgroundPosition.DEFAULT, BackgroundSize.DEFAULT));
    }

    /**
     * Region con el tablero, dimensionada exactamente al rectángulo renderizado
     * de la imagen (no al panel), de modo que el letterbox queda en canvas
     * oscuro y sólo la zona de la imagen muestra el checker. Visible sólo cuando
     * hay imagen cargada.
     */
    private static Region makeCheckerBacking(ImageView view) {
        Region r = new Region();
        r.setBackground(CHECKER_BG);
        r.setMouseTransparent(true);
        var w = Bindings.createDoubleBinding(
                () -> view.getBoundsInLocal().getWidth(),  view.boundsInLocalProperty());
        var h = Bindings.createDoubleBinding(
                () -> view.getBoundsInLocal().getHeight(), view.boundsInLocalProperty());
        r.minWidthProperty().bind(w);  r.prefWidthProperty().bind(w);  r.maxWidthProperty().bind(w);
        r.minHeightProperty().bind(h); r.prefHeightProperty().bind(h); r.maxHeightProperty().bind(h);
        r.visibleProperty().bind(view.imageProperty().isNotNull());
        return r;
    }

    /** Placeholder centrado mostrado cuando no hay imagen cargada. */
    private static VBox buildEmptyState() {
        FontIcon icon = new FontIcon("fas-image");
        icon.getStyleClass().add("empty-icon");

        Label title = new Label("Arrastra una imagen aquí");
        title.getStyleClass().add("empty-state-title");

        Label subtitle = new Label("o usa el botón Abrir de la barra superior");
        subtitle.getStyleClass().add("empty-state-subtitle");

        VBox box = new VBox(icon, title, subtitle);
        box.getStyleClass().add("empty-state");
        box.setAlignment(Pos.CENTER);
        box.setMouseTransparent(true);  // deja pasar clicks y eventos de arrastre
        return box;
    }

    /** Acepta archivos de imagen soltados sobre {@code target} y los reenvía al callback. */
    private void enableDragAndDrop(ScrollPane target) {
        target.setOnDragOver(e -> {
            if (onImageDropped != null && isImageDrag(e.getDragboard())) {
                e.acceptTransferModes(TransferMode.COPY);
                if (!target.getStyleClass().contains(DRAGOVER_CLASS)) {
                    target.getStyleClass().add(DRAGOVER_CLASS);
                }
            }
            e.consume();
        });
        target.setOnDragExited(e -> {
            target.getStyleClass().remove(DRAGOVER_CLASS);
            e.consume();
        });
        target.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean done = false;
            if (onImageDropped != null && isImageDrag(db)) {
                onImageDropped.accept(db.getFiles().get(0));
                done = true;
            }
            target.getStyleClass().remove(DRAGOVER_CLASS);
            e.setDropCompleted(done);
            e.consume();
        });
    }

    /** True si el dragboard contiene al menos un archivo con extensión de imagen. */
    private static boolean isImageDrag(Dragboard db) {
        if (!db.hasFiles() || db.getFiles().isEmpty()) return false;
        String name = db.getFiles().get(0).getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".png")  || name.endsWith(".jpg") || name.endsWith(".jpeg")
            || name.endsWith(".bmp")  || name.endsWith(".gif");
    }

    private static ScrollPane buildScrollPane(StackPane content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(false);
        sp.setFitToHeight(false);
        sp.setPannable(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.getStyleClass().add("preview-scroll-pane");
        return sp;
    }

    private static Label makeCompareBadge(String text, Pos alignment) {
        Label label = new Label(text);
        label.getStyleClass().add("compare-badge");
        StackPane.setAlignment(label, alignment);
        StackPane.setMargin(label, new Insets(10));
        return label;
    }
}

