package com.example.imageprocessor.app.ui;

import com.example.imageprocessor.service.ImageIOService;
import com.example.imageprocessor.service.ImageProcessor;
import javafx.beans.binding.Bindings;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Panel for alpha-blending the editor's current image with a user-chosen
 * background image.
 *
 * <p>Wiring pattern — receives all external dependencies as callbacks at
 * construction; holds no reference to {@code ImageProcessorApp} or
 * {@code Stage}.
 *
 * <p>Preview behaviour:
 * <ul>
 *   <li>No images loaded → overlay hint "load both images".</li>
 *   <li>Only foreground available → overlay hint "choose a background".</li>
 *   <li>Only background loaded → shows the background with an overlay hint
 *       "load an editor image to complete the blend".</li>
 *   <li>Both images present → blend is computed and shown immediately;
 *       the alpha slider updates the preview in real time.</li>
 * </ul>
 */
public class BlendingPane {

    private final Supplier<BufferedImage> imageSource;
    private final Supplier<File>          backgroundChooser;
    private final Consumer<BufferedImage> onUseInEditor;
    private final Consumer<String>        onStatus;

    // ── Controls ──────────────────────────────────────────────────────────────
    private final Slider    alphaSlider         = new Slider(0.0, 1.0, 0.5);
    private final Label     backgroundNameLabel = new Label("Ninguna imagen cargada");
    private final ImageView blendView           = new ImageView();
    /** Overlay hint shown inside the preview area when images are missing. */
    private final Label     overlayLabel        = new Label();
    private final VBox      view;

    // ── State ─────────────────────────────────────────────────────────────────
    /** Background image currently loaded, or {@code null}. */
    private BufferedImage backgroundImage;
    /** Last generated blend result, or {@code null}. */
    private BufferedImage blendedImage;

    public BlendingPane(Supplier<BufferedImage> imageSource,
                        Supplier<File>          backgroundChooser,
                        Consumer<BufferedImage> onUseInEditor,
                        Consumer<String>        onStatus) {
        this.imageSource       = imageSource;
        this.backgroundChooser = backgroundChooser;
        this.onUseInEditor     = onUseInEditor;
        this.onStatus          = onStatus;

        // ── Preview image view ────────────────────────────────────────────────
        blendView.setPreserveRatio(true);
        blendView.setSmooth(true);

        // ── Overlay label — sits on top of blendView inside the StackPane ─────
        overlayLabel.getStyleClass().add("field-label");
        overlayLabel.setWrapText(true);
        overlayLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        overlayLabel.setStyle(
                "-fx-background-color: rgba(23,23,26,0.72);" +
                "-fx-padding: 12 20 12 20;" +
                "-fx-background-radius: 8;" +
                "-fx-text-fill: #a0a0aa;" +
                "-fx-font-size: 13px;"
        );
        overlayLabel.setMaxWidth(420);

        // ── Image wrapper — StackPane so overlay floats above preview ─────────
        StackPane imageWrapper = new StackPane(blendView, overlayLabel);
        imageWrapper.getStyleClass().add("gradient-preview");
        VBox.setVgrow(imageWrapper, Priority.ALWAYS);

        blendView.fitWidthProperty() .bind(imageWrapper.widthProperty() .subtract(32));
        blendView.fitHeightProperty().bind(imageWrapper.heightProperty().subtract(32));

        // ── Live alpha slider → re-blend whenever both images are present ──────
        alphaSlider.valueProperty().addListener((obs, oldVal, newVal) -> updatePreview());

        view = new VBox(12, buildControls(), imageWrapper);
        view.setPadding(new Insets(16));
        view.getStyleClass().add("preview-pane");

        // Show initial overlay state
        updatePreview();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public VBox getView() { return view; }

    // ── Controls builder ──────────────────────────────────────────────────────

    private VBox buildControls() {
        // ── Section header ───────────────────────────────────────────────────
        Label sectionHeader = new Label("BLENDING DE IMÁGENES");
        sectionHeader.getStyleClass().add("section-title");

        Separator headerSep = new Separator();
        headerSep.setOpacity(0.4);

        // ── Background loader ────────────────────────────────────────────────
        Button loadBgButton = new Button("Cargar fondo…");
        loadBgButton.setOnAction(e -> loadBackground());

        backgroundNameLabel.getStyleClass().add("field-label");
        backgroundNameLabel.setWrapText(true);

        // ── Alpha slider ─────────────────────────────────────────────────────
        alphaSlider.setPrefWidth(Region.USE_COMPUTED_SIZE);
        alphaSlider.setMaxWidth(Double.MAX_VALUE);

        Label alphaValueLabel = new Label();
        alphaValueLabel.getStyleClass().add("param-value-label");
        alphaValueLabel.textProperty().bind(
                Bindings.createStringBinding(
                        () -> String.format("%.2f", alphaSlider.getValue()),
                        alphaSlider.valueProperty()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox alphaHeader = new HBox(4, fieldLabel("Factor alpha (fondo)"), spacer, alphaValueLabel);
        alphaHeader.setAlignment(Pos.CENTER_LEFT);

        Button resetAlpha = new Button("↺");
        resetAlpha.getStyleClass().add("param-reset-btn");
        resetAlpha.setOnAction(e -> alphaSlider.setValue(0.5));

        HBox.setHgrow(alphaSlider, Priority.ALWAYS);
        HBox alphaRow = new HBox(6, alphaSlider, resetAlpha);
        alphaRow.setAlignment(Pos.CENTER_LEFT);

        VBox alphaBox = new VBox(4, alphaHeader, alphaRow);
        alphaBox.getStyleClass().addAll("filter-section", "filter-card");

        // ── Action buttons ───────────────────────────────────────────────────
        Button blendButton = new Button("Aplicar blending");
        Button useButton   = new Button("Usar en editor");

        blendButton.setOnAction(e -> applyBlend());
        useButton.setOnAction(e -> useInEditor());

        // ── Assemble ─────────────────────────────────────────────────────────
        VBox controls = new VBox(8,
                sectionHeader,
                headerSep,
                fieldLabel("Imagen de fondo"),
                new HBox(8, loadBgButton, backgroundNameLabel),
                alphaBox,
                new HBox(8, blendButton, useButton)
        );
        controls.setAlignment(Pos.TOP_LEFT);
        controls.getStyleClass().add("gradient-controls");
        return controls;
    }

    // ── Preview logic ─────────────────────────────────────────────────────────

    /**
     * Central preview update method. Evaluates which images are available and:
     * <ul>
     *   <li>Both present → blends immediately, hides overlay.</li>
     *   <li>Only background → shows background, overlay requests foreground.</li>
     *   <li>Only foreground → clears view, overlay requests background.</li>
     *   <li>Neither → clears view, overlay requests both.</li>
     * </ul>
     */
    private void updatePreview() {
        BufferedImage source = imageSource.get();
        boolean hasFg = source != null;
        boolean hasBg = backgroundImage != null;

        if (hasFg && hasBg) {
            // ── Both ready: compute blend and show ────────────────────────────
            float alpha = (float) alphaSlider.getValue();
            blendedImage = ImageProcessor.blend(source, backgroundImage, alpha);
            blendView.setImage(SwingFXUtils.toFXImage(blendedImage, null));
            setOverlay(null);

        } else if (hasBg) {
            // ── Only background: show it and ask for the editor image ─────────
            blendedImage = null;
            blendView.setImage(SwingFXUtils.toFXImage(backgroundImage, null));
            setOverlay("Carga una imagen en el editor para completar el blending.");

        } else if (hasFg) {
            // ── Only foreground: clear view and ask for a background ──────────
            blendedImage = null;
            blendView.setImage(null);
            setOverlay("Selecciona una imagen de fondo para ver la previsualización.");

        } else {
            // ── Nothing loaded yet ────────────────────────────────────────────
            blendedImage = null;
            blendView.setImage(null);
            setOverlay("Carga una imagen en el editor y selecciona un fondo para comenzar.");
        }
    }

    /** Shows {@code message} in the overlay label, or hides it when {@code null}. */
    private void setOverlay(String message) {
        if (message == null) {
            overlayLabel.setVisible(false);
            overlayLabel.setManaged(false);
        } else {
            overlayLabel.setText(message);
            overlayLabel.setVisible(true);
            overlayLabel.setManaged(true);
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void loadBackground() {
        File file = backgroundChooser.get();
        if (file == null) return;
        try {
            backgroundImage = ImageIOService.read(file);
            backgroundNameLabel.setText(file.getName());
            onStatus.accept("Fondo cargado: " + file.getName());
            updatePreview();   // ← auto-preview immediately after load
        } catch (Exception ex) {
            onStatus.accept("Error al cargar el fondo: " + ex.getMessage());
        }
    }

    /** Explicit blend triggered by the "Aplicar blending" button. */
    private void applyBlend() {
        BufferedImage source = imageSource.get();
        if (source == null) {
            onStatus.accept("Carga una imagen en el editor antes de aplicar el blending.");
            return;
        }
        if (backgroundImage == null) {
            onStatus.accept("Carga una imagen de fondo antes de aplicar el blending.");
            return;
        }
        updatePreview();
        onStatus.accept(String.format("Blending aplicado (alpha = %.2f).", alphaSlider.getValue()));
    }

    private void useInEditor() {
        if (blendedImage == null) {
            onStatus.accept("Aplica el blending antes de enviarlo al editor.");
            return;
        }
        onUseInEditor.accept(blendedImage);
        onStatus.accept("Resultado de blending cargado en el editor.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Label fieldLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("field-label");
        return l;
    }
}
