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
import javafx.scene.text.TextAlignment;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Panel for blending three images (foreground + two backgrounds) with
 * independent per-image weight factors.
 *
 * <p>Wiring pattern — identical to {@link BlendingPane}; holds no reference
 * to {@code ImageProcessorApp} or {@code Stage}:
 * <ul>
 *   <li>{@code imageSource}         — supplies the foreground (processed) image.</li>
 *   <li>{@code backgroundChooser1/2} — open the OS file dialog and return the
 *       chosen {@link File}, or {@code null} if cancelled.</li>
 *   <li>{@code onUseInEditor}        — loads the blended result into the editor.</li>
 *   <li>{@code onStatus}             — forwards messages to the status bar.</li>
 * </ul>
 *
 * <p>Progressive preview behaviour:
 * <ul>
 *   <li>No images → hint "load all three".</li>
 *   <li>Only one background → shows it with hint for the rest.</li>
 *   <li>Foreground + one background → shows their 2-image blend + hint.</li>
 *   <li>Two backgrounds, no foreground → shows bg1 + hint.</li>
 *   <li>All three → full triple blend, overlay hidden.</li>
 * </ul>
 */
public class TripleBlendingPane {

    private final Supplier<BufferedImage> imageSource;
    private final Supplier<File>          backgroundChooser1;
    private final Supplier<File>          backgroundChooser2;
    private final Consumer<BufferedImage> onUseInEditor;
    private final Consumer<String>        onStatus;

    // ── Controls ──────────────────────────────────────────────────────────────
    private final Slider    alpha1Slider  = new Slider(0.0, 1.0, 0.5);
    private final Slider    alpha2Slider  = new Slider(0.0, 1.0, 0.3);
    private final Slider    alpha3Slider  = new Slider(0.0, 1.0, 0.2);
    private final Label     bg1NameLabel  = new Label("Ninguna imagen cargada");
    private final Label     bg2NameLabel  = new Label("Ninguna imagen cargada");
    private final ImageView blendView     = new ImageView();
    private final Label     overlayLabel  = new Label();
    private final VBox      view;

    // ── State ─────────────────────────────────────────────────────────────────
    private BufferedImage backgroundImage1;
    private BufferedImage backgroundImage2;
    private BufferedImage blendedImage;

    public TripleBlendingPane(Supplier<BufferedImage> imageSource,
                              Supplier<File>          backgroundChooser1,
                              Supplier<File>          backgroundChooser2,
                              Consumer<BufferedImage> onUseInEditor,
                              Consumer<String>        onStatus) {
        this.imageSource        = imageSource;
        this.backgroundChooser1 = backgroundChooser1;
        this.backgroundChooser2 = backgroundChooser2;
        this.onUseInEditor      = onUseInEditor;
        this.onStatus           = onStatus;

        // ── Preview ImageView ─────────────────────────────────────────────────
        blendView.setPreserveRatio(true);
        blendView.setSmooth(true);

        // ── Overlay label ─────────────────────────────────────────────────────
        overlayLabel.getStyleClass().add("field-label");
        overlayLabel.setWrapText(true);
        overlayLabel.setTextAlignment(TextAlignment.CENTER);
        overlayLabel.setStyle(
                "-fx-background-color: rgba(23,23,26,0.72);" +
                "-fx-padding: 12 20 12 20;" +
                "-fx-background-radius: 8;" +
                "-fx-text-fill: #a0a0aa;" +
                "-fx-font-size: 13px;"
        );
        overlayLabel.setMaxWidth(460);

        // ── Image wrapper ─────────────────────────────────────────────────────
        StackPane imageWrapper = new StackPane(blendView, overlayLabel);
        imageWrapper.getStyleClass().add("gradient-preview");
        VBox.setVgrow(imageWrapper, Priority.ALWAYS);

        blendView.fitWidthProperty() .bind(imageWrapper.widthProperty() .subtract(32));
        blendView.fitHeightProperty().bind(imageWrapper.heightProperty().subtract(32));

        // ── Live slider updates ───────────────────────────────────────────────
        alpha1Slider.valueProperty().addListener((obs, o, n) -> updatePreview());
        alpha2Slider.valueProperty().addListener((obs, o, n) -> updatePreview());
        alpha3Slider.valueProperty().addListener((obs, o, n) -> updatePreview());

        view = new VBox(12, buildControls(), imageWrapper);
        view.setPadding(new Insets(16));
        view.getStyleClass().add("preview-pane");

        updatePreview();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public VBox getView() { return view; }

    // ── Controls builder ──────────────────────────────────────────────────────

    private VBox buildControls() {
        // ── Section header ────────────────────────────────────────────────────
        Label sectionHeader = new Label("TRIPLE BLENDING");
        sectionHeader.getStyleClass().add("section-title");

        Separator headerSep = new Separator();
        headerSep.setOpacity(0.4);

        // ── Background loaders ────────────────────────────────────────────────
        Button loadBg1Button = new Button("Cargar fondo 1…");
        Button loadBg2Button = new Button("Cargar fondo 2…");
        loadBg1Button.setOnAction(e -> loadBackground(1));
        loadBg2Button.setOnAction(e -> loadBackground(2));

        bg1NameLabel.getStyleClass().add("field-label");
        bg2NameLabel.getStyleClass().add("field-label");
        bg1NameLabel.setWrapText(true);
        bg2NameLabel.setWrapText(true);

        // ── Alpha sliders card ────────────────────────────────────────────────
        VBox alphaCard = new VBox(6,
                buildSliderRow(alpha1Slider, "Peso editor (α₁)", 0.5),
                new Separator(),
                buildSliderRow(alpha2Slider, "Peso fondo 1 (α₂)", 0.3),
                new Separator(),
                buildSliderRow(alpha3Slider, "Peso fondo 2 (α₃)", 0.2)
        );
        alphaCard.getStyleClass().addAll("filter-section", "filter-card");

        // ── Action buttons ────────────────────────────────────────────────────
        Button blendButton = new Button("Aplicar triple blending");
        Button useButton   = new Button("Usar en editor");
        blendButton.setOnAction(e -> applyBlend());
        useButton.setOnAction(e -> useInEditor());

        // ── Assemble ──────────────────────────────────────────────────────────
        VBox controls = new VBox(8,
                sectionHeader,
                headerSep,
                fieldLabel("Imagen de fondo 1"),
                new HBox(8, loadBg1Button, bg1NameLabel),
                fieldLabel("Imagen de fondo 2"),
                new HBox(8, loadBg2Button, bg2NameLabel),
                fieldLabel("Factores de peso"),
                alphaCard,
                new HBox(8, blendButton, useButton)
        );
        controls.setAlignment(Pos.TOP_LEFT);
        controls.getStyleClass().add("gradient-controls");
        return controls;
    }

    /** Builds a header-row + [slider ↺] block for a single alpha channel. */
    private VBox buildSliderRow(Slider slider, String labelText, double defaultVal) {
        slider.setPrefWidth(Region.USE_COMPUTED_SIZE);
        slider.setMaxWidth(Double.MAX_VALUE);

        Label valueLbl = new Label();
        valueLbl.getStyleClass().add("param-value-label");
        valueLbl.textProperty().bind(
                Bindings.createStringBinding(
                        () -> String.format("%.2f", slider.getValue()),
                        slider.valueProperty()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(4, fieldLabel(labelText), spacer, valueLbl);
        header.setAlignment(Pos.CENTER_LEFT);

        Button resetBtn = new Button("↺");
        resetBtn.getStyleClass().add("param-reset-btn");
        resetBtn.setOnAction(e -> slider.setValue(defaultVal));

        HBox.setHgrow(slider, Priority.ALWAYS);
        HBox controlRow = new HBox(6, slider, resetBtn);
        controlRow.setAlignment(Pos.CENTER_LEFT);

        return new VBox(4, header, controlRow);
    }

    // ── Preview logic ─────────────────────────────────────────────────────────

    /**
     * Evaluates which images are available and renders the best possible preview:
     * <table>
     *   <tr><th>fg</th><th>bg1</th><th>bg2</th><th>Result</th></tr>
     *   <tr><td>✗</td><td>✗</td><td>✗</td><td>blank + hint</td></tr>
     *   <tr><td>✓</td><td>✗</td><td>✗</td><td>blank + "carga dos fondos"</td></tr>
     *   <tr><td>✗</td><td>✓</td><td>✗</td><td>bg1 + hint</td></tr>
     *   <tr><td>✗</td><td>✗</td><td>✓</td><td>bg2 + hint</td></tr>
     *   <tr><td>✓</td><td>✓</td><td>✗</td><td>blend(fg,bg1) + hint</td></tr>
     *   <tr><td>✓</td><td>✗</td><td>✓</td><td>blend(fg,bg2) + hint</td></tr>
     *   <tr><td>✗</td><td>✓</td><td>✓</td><td>bg1 + "carga imagen del editor"</td></tr>
     *   <tr><td>✓</td><td>✓</td><td>✓</td><td>full triple blend, no overlay</td></tr>
     * </table>
     */
    private void updatePreview() {
        BufferedImage fg  = imageSource.get();
        boolean hasFg  = fg  != null;
        boolean hasBg1 = backgroundImage1 != null;
        boolean hasBg2 = backgroundImage2 != null;

        if (hasFg && hasBg1 && hasBg2) {
            // ── All three → full triple blend ─────────────────────────────────
            blendedImage = ImageProcessor.tripleBlend(
                    fg, backgroundImage1, backgroundImage2,
                    (float) alpha1Slider.getValue(),
                    (float) alpha2Slider.getValue(),
                    (float) alpha3Slider.getValue());
            show(blendedImage, null);

        } else if (hasFg && hasBg1) {
            // ── fg + bg1 → partial 2-image blend, ask for bg2 ─────────────────
            blendedImage = null;
            float a = (float)(alpha1Slider.getValue()
                    / Math.max(0.001, alpha1Slider.getValue() + alpha2Slider.getValue()));
            show(ImageProcessor.blend(fg, backgroundImage1, 1f - a),
                 "Carga la segunda imagen de fondo para completar el triple blending.");

        } else if (hasFg && hasBg2) {
            // ── fg + bg2 → partial 2-image blend, ask for bg1 ─────────────────
            blendedImage = null;
            float a = (float)(alpha1Slider.getValue()
                    / Math.max(0.001, alpha1Slider.getValue() + alpha3Slider.getValue()));
            show(ImageProcessor.blend(fg, backgroundImage2, 1f - a),
                 "Carga la primera imagen de fondo para completar el triple blending.");

        } else if (hasBg1 && hasBg2) {
            // ── bg1 + bg2, no fg → show bg1, ask for editor image ─────────────
            blendedImage = null;
            show(backgroundImage1,
                 "Carga una imagen en el editor para completar el triple blending.");

        } else if (hasBg1) {
            blendedImage = null;
            show(backgroundImage1,
                 "Carga la segunda imagen de fondo y una imagen del editor para continuar.");

        } else if (hasBg2) {
            blendedImage = null;
            show(backgroundImage2,
                 "Carga la primera imagen de fondo y una imagen del editor para continuar.");

        } else if (hasFg) {
            blendedImage = null;
            show(null, "Carga dos imágenes de fondo para ver la previsualización.");

        } else {
            blendedImage = null;
            show(null, "Carga una imagen en el editor y dos imágenes de fondo para comenzar.");
        }
    }

    /** Updates {@code blendView} and the overlay in one call. */
    private void show(BufferedImage image, String overlayMessage) {
        blendView.setImage(image == null ? null : SwingFXUtils.toFXImage(image, null));
        if (overlayMessage == null) {
            overlayLabel.setVisible(false);
            overlayLabel.setManaged(false);
        } else {
            overlayLabel.setText(overlayMessage);
            overlayLabel.setVisible(true);
            overlayLabel.setManaged(true);
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void loadBackground(int slot) {
        Supplier<File> chooser = slot == 1 ? backgroundChooser1 : backgroundChooser2;
        File file = chooser.get();
        if (file == null) return;
        try {
            BufferedImage loaded = ImageIOService.read(file);
            if (slot == 1) {
                backgroundImage1 = loaded;
                bg1NameLabel.setText(file.getName());
            } else {
                backgroundImage2 = loaded;
                bg2NameLabel.setText(file.getName());
            }
            onStatus.accept("Fondo " + slot + " cargado: " + file.getName());
            updatePreview();
        } catch (Exception ex) {
            onStatus.accept("Error al cargar fondo " + slot + ": " + ex.getMessage());
        }
    }

    /** Explicit blend triggered by the "Aplicar triple blending" button. */
    private void applyBlend() {
        BufferedImage fg = imageSource.get();
        if (fg == null) {
            onStatus.accept("Carga una imagen en el editor antes de aplicar el triple blending.");
            return;
        }
        if (backgroundImage1 == null) {
            onStatus.accept("Carga la primera imagen de fondo antes de aplicar el triple blending.");
            return;
        }
        if (backgroundImage2 == null) {
            onStatus.accept("Carga la segunda imagen de fondo antes de aplicar el triple blending.");
            return;
        }
        updatePreview();
        onStatus.accept(String.format(
                "Triple blending aplicado (α₁=%.2f  α₂=%.2f  α₃=%.2f).",
                alpha1Slider.getValue(), alpha2Slider.getValue(), alpha3Slider.getValue()));
    }

    private void useInEditor() {
        if (blendedImage == null) {
            onStatus.accept("Aplica el triple blending antes de enviarlo al editor.");
            return;
        }
        onUseInEditor.accept(blendedImage);
        onStatus.accept("Resultado de triple blending cargado en el editor.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Label fieldLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("field-label");
        return l;
    }
}

