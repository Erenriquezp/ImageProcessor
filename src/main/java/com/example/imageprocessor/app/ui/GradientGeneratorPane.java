package com.example.imageprocessor.app.ui;

import com.example.imageprocessor.domain.GradientType;
import com.example.imageprocessor.service.ImageProcessor;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.awt.image.BufferedImage;
import java.util.function.Consumer;

public class GradientGeneratorPane {

    private final Consumer<BufferedImage> onUseInEditor;
    private final Consumer<String> onStatus;
    private final ImageView gradientImageView = new ImageView();
    private final VBox view;

    private BufferedImage generatedImage;

    public GradientGeneratorPane(Consumer<BufferedImage> onUseInEditor, Consumer<String> onStatus) {
        this.onUseInEditor = onUseInEditor;
        this.onStatus = onStatus;

        gradientImageView.setPreserveRatio(true);
        gradientImageView.setSmooth(true);

        // Wrap the ImageView in a StackPane that grows to fill remaining space.
        // Binding fit sizes to the wrapper makes the preview fully responsive.
        StackPane imageWrapper = new StackPane(gradientImageView);
        imageWrapper.getStyleClass().add("gradient-preview");
        VBox.setVgrow(imageWrapper, Priority.ALWAYS);

        gradientImageView.fitWidthProperty() .bind(imageWrapper.widthProperty() .subtract(32));
        gradientImageView.fitHeightProperty().bind(imageWrapper.heightProperty().subtract(32));

        view = new VBox(12, buildGradientControls(), imageWrapper);
        view.setPadding(new Insets(16));
        view.getStyleClass().add("preview-pane");
    }

    public VBox getView() { return view; }

    public BufferedImage getGeneratedImage() { return generatedImage; }

    private VBox buildGradientControls() {
        ComboBox<GradientType> gradientTypeCombo = new ComboBox<>();
        gradientTypeCombo.getItems().addAll(GradientType.values());
        gradientTypeCombo.getSelectionModel().select(GradientType.LEFT_TO_RIGHT);

        Spinner<Integer> widthSpinner  = new Spinner<>(64, 4096, 800);
        Spinner<Integer> heightSpinner = new Spinner<>(64, 4096, 600);

        ColorPicker startPicker = new ColorPicker(Color.web("#2F54FF"));
        ColorPicker endPicker   = new ColorPicker(Color.web("#FFFFFF"));

        Button generateButton = new Button("Generar degradado");
        Button useButton      = new Button("Usar en editor");

        generateButton.setOnAction(e -> generateGradientImage(
                gradientTypeCombo.getValue(),
                widthSpinner.getValue(),
                heightSpinner.getValue(),
                startPicker.getValue(),
                endPicker.getValue()
        ));

        useButton.setOnAction(e -> useGeneratedImageInEditor());

        // ── Section header ──────────────────────────────────────────────
        Label sectionHeader = new Label("GENERADOR DE DEGRADADOS");
        sectionHeader.getStyleClass().add("section-title");

        Separator headerSep = new Separator();
        headerSep.setOpacity(0.4);

        VBox controls = new VBox(8,
                sectionHeader,
                headerSep,
                fieldLabel("Tipo"),          gradientTypeCombo,
                fieldLabel("Ancho (px)"),    widthSpinner,
                fieldLabel("Alto (px)"),     heightSpinner,
                fieldLabel("Color inicial"), startPicker,
                fieldLabel("Color final"),   endPicker,
                new HBox(8, generateButton, useButton)
        );
        controls.setAlignment(Pos.TOP_LEFT);
        controls.getStyleClass().add("gradient-controls");
        return controls;
    }

    private static Label fieldLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("field-label");
        return l;
    }

    private void generateGradientImage(GradientType type, int width, int height,
                                       Color startColor, Color endColor) {
        int startRgb = fxColorToRgb(startColor);
        int endRgb   = fxColorToRgb(endColor);
        generatedImage = ImageProcessor.generateGradient(type, width, height, startRgb, endRgb);
        gradientImageView.setImage(SwingFXUtils.toFXImage(generatedImage, null));
        onStatus.accept("Degradado generado: " + width + "x" + height);
    }

    private void useGeneratedImageInEditor() {
        if (generatedImage == null) {
            onStatus.accept("Genera un degradado antes de enviarlo al editor.");
            return;
        }
        onUseInEditor.accept(generatedImage);
        onStatus.accept("Degradado cargado en el editor.");
    }

    private static int fxColorToRgb(Color c) {
        int r = (int) Math.round(c.getRed()   * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue()  * 255);
        return (r << 16) | (g << 8) | b;
    }
}
