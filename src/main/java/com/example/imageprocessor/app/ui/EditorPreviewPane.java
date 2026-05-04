package com.example.imageprocessor.app.ui;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;

import java.awt.image.BufferedImage;

/**
 * Panel de previsualización central.
 *
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

    // ── Constructor ───────────────────────────────────────────────────────

    public EditorPreviewPane() {
        configureImageView(processedView);
        configureImageView(originalView);

        // LEFT content — imagen original (sólo en modo comparar)
        Label originalBadge = makeCompareBadge("ORIGINAL", Pos.TOP_LEFT);
        leftContent = new StackPane(originalView, originalBadge);
        leftContent.getStyleClass().addAll("preview-pane", "compare-pane");

        // RIGHT content — imagen procesada (siempre visible)
        compareResultLabel = makeCompareBadge("RESULTADO", Pos.TOP_RIGHT);
        compareResultLabel.setVisible(false);
        rightContent = new StackPane(processedView, compareResultLabel);
        rightContent.getStyleClass().addAll("preview-pane", "compare-pane");

        leftScrollPane  = buildScrollPane(leftContent);
        rightScrollPane = buildScrollPane(rightContent);

        // Viewport resize → refrescar dimensiones de zoom
        leftScrollPane .viewportBoundsProperty().addListener((obs, o, b) -> updateZoomDimensions());
        rightScrollPane.viewportBoundsProperty().addListener((obs, o, b) -> updateZoomDimensions());

        // Rueda del ratón — cada panel zooms de forma independiente
        leftScrollPane .addEventFilter(ScrollEvent.SCROLL, e -> { activatePane(true);  onScrollZoom(e, true);  });
        rightScrollPane.addEventFilter(ScrollEvent.SCROLL, e -> { activatePane(false); onScrollZoom(e, false); });

        // Click → activar panel para los botones de toolbar
        leftContent .setOnMouseClicked(e -> activatePane(true));
        rightContent.setOnMouseClicked(e -> activatePane(false));

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

    /** Actualiza la imagen del panel de resultado. */
    public void showProcessed(BufferedImage image) {
        processedView.setImage(image == null ? null : SwingFXUtils.toFXImage(image, null));
    }

    /** Actualiza la imagen del panel original (sólo relevante en modo comparar). */
    public void showOriginal(BufferedImage image) {
        originalView.setImage(image == null ? null : SwingFXUtils.toFXImage(image, null));
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

    // ── Zoom — API pública (delegada desde TopBar / teclado) ─────────────

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

    /** Aplica / retira el borde de acento. Sólo visible en modo comparar. */
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
    }

    private void setRightZoom(double level) {
        rightZoomLevel = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, level));
        applyZoomToPane(rightScrollPane, rightContent, processedView, rightZoomLevel);
        if (!leftPaneActive) updateZoomIndicator();
    }

    /** Refresca ambos paneles (p.ej. al redimensionar la ventana). */
    private void updateZoomDimensions() {
        applyZoomToPane(rightScrollPane, rightContent, processedView, rightZoomLevel);
        applyZoomToPane(leftScrollPane,  leftContent,  originalView,  leftZoomLevel);
    }

    /**
     * Aplica el nivel de zoom a un panel concreto.
     *
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

