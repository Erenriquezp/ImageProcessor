package com.example.imageprocessor.app.ui;

import com.example.imageprocessor.service.ImageProcessor;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Panel that generates and displays an RGB histogram chart for the image
 * currently loaded in the editor.
 *
 * <p>Wiring pattern: receives callbacks at construction — holds no reference
 * to {@code ImageProcessorApp}.
 *
 * <ul>
 *   <li>{@code imageSource}   — supplies the current processed image to analyse.</li>
 *   <li>{@code onUseInEditor} — called when the user wants to load the histogram
 *       chart back into the editor as a working image.</li>
 *   <li>{@code onStatus}      — forwards status messages to the status bar.</li>
 * </ul>
 */
public class HistogramPane {

    private final Supplier<BufferedImage>  imageSource;
    private final Consumer<BufferedImage>  onUseInEditor;
    private final Consumer<String>         onStatus;

    private final ImageView histogramView  = new ImageView();
    private final VBox      view;

    /** Last generated histogram chart, or {@code null} if none yet. */
    private BufferedImage generatedHistogram;

    public HistogramPane(Supplier<BufferedImage>  imageSource,
                         Consumer<BufferedImage>  onUseInEditor,
                         Consumer<String>         onStatus) {
        this.imageSource   = imageSource;
        this.onUseInEditor = onUseInEditor;
        this.onStatus      = onStatus;

        histogramView.setPreserveRatio(true);
        histogramView.setSmooth(true);

        // Image wrapper — grows to fill remaining vertical space
        StackPane imageWrapper = new StackPane(histogramView);
        imageWrapper.getStyleClass().add("gradient-preview");
        VBox.setVgrow(imageWrapper, Priority.ALWAYS);

        histogramView.fitWidthProperty() .bind(imageWrapper.widthProperty() .subtract(32));
        histogramView.fitHeightProperty().bind(imageWrapper.heightProperty().subtract(32));

        view = new VBox(12, buildControls(), imageWrapper);
        view.setPadding(new Insets(16));
        view.getStyleClass().add("preview-pane");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public VBox getView() { return view; }

    // ── Controls builder ──────────────────────────────────────────────────────

    private VBox buildControls() {
        Button generateButton = new Button("Generar histograma");
        Button useButton      = new Button("Usar en editor");

        generateButton.setOnAction(e -> generateHistogram());
        useButton.setOnAction(e -> useInEditor());

        // Section header
        Label sectionHeader = new Label("HISTOGRAMA RGB");
        sectionHeader.getStyleClass().add("section-title");

        Separator headerSep = new Separator();
        headerSep.setOpacity(0.4);

        Label hint = new Label(
                "Muestra la distribución de frecuencias de los canales R, G y B\n" +
                "de la imagen actualmente procesada en el editor."
        );
        hint.getStyleClass().add("field-label");
        hint.setWrapText(true);

        VBox controls = new VBox(8,
                sectionHeader,
                headerSep,
                hint,
                new HBox(8, generateButton, useButton)
        );
        controls.setAlignment(Pos.TOP_LEFT);
        controls.getStyleClass().add("gradient-controls");
        return controls;
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void generateHistogram() {
        BufferedImage source = imageSource.get();
        if (source == null) {
            onStatus.accept("Carga una imagen en el editor antes de generar el histograma.");
            return;
        }
        generatedHistogram = ImageProcessor.generateHistogram(source);
        histogramView.setImage(SwingFXUtils.toFXImage(generatedHistogram, null));
        onStatus.accept("Histograma generado (" + source.getWidth() + "×" + source.getHeight() + " px).");
    }

    private void useInEditor() {
        if (generatedHistogram == null) {
            onStatus.accept("Genera el histograma antes de enviarlo al editor.");
            return;
        }
        onUseInEditor.accept(generatedHistogram);
        onStatus.accept("Histograma cargado en el editor.");
    }
}

