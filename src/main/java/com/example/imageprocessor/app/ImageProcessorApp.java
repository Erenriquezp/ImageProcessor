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
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Objects;

public class ImageProcessorApp extends Application {

    private static final String APP_TITLE = "ImageProcessor - Taller 2";
    private static final String MAIN_STYLESHEET = "/com/example/imageprocessor/styles.css";
    private static final String DEFAULT_STATUS = "Carga una imagen para comenzar.";
    private static final int PREVIEW_FIT_WIDTH = 980;
    private static final int PREVIEW_FIT_HEIGHT = 720;

    private BufferedImage originalImage;
    private BufferedImage processedImage;

    private final ImageView editorImageView = new ImageView();
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

    private HBox buildTopBar(Stage stage) {
        Button openButton = new Button("Abrir");
        Button saveButton = new Button("Guardar");
        Button resetButton = new Button("Reset");
        Button applyButton = new Button("Aplicar filtro");

        openButton.setOnAction(e -> openImage(stage));
        saveButton.setOnAction(e -> saveImage(stage));
        resetButton.setOnAction(e -> resetImage());
        applyButton.setOnAction(e -> applySelectedFilter());

        HBox bar = new HBox(10, openButton, saveButton, resetButton, applyButton, beforeAfterCheck);
        bar.setPadding(new Insets(12));
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    private VBox buildLeftPanel() {
        VBox panel = editorFilterPane.getView();
        panel.setPadding(new Insets(14));
        panel.setPrefWidth(320);
        panel.getStyleClass().add("left-panel");
        return panel;
    }

    private TabPane buildCenterTabs() {
        configurePreviewImageView(editorImageView);

        StackPane editorPane = new StackPane(editorImageView);
        editorPane.setPadding(new Insets(16));
        editorPane.getStyleClass().add("preview-pane");

        Tab editorTab = new Tab("Editor", editorPane);
        editorTab.setClosable(false);

        Tab gradientTab = new Tab("Generador", gradientGeneratorPane.getView());
        gradientTab.setClosable(false);

        TabPane tabs = new TabPane(editorTab, gradientTab);
        tabs.getStyleClass().add("center-tabs");
        return tabs;
    }

    private HBox buildBottomBar() {
        HBox bottom = new HBox(statusLabel);
        bottom.setPadding(new Insets(10, 14, 10, 14));
        bottom.getStyleClass().add("bottom-bar");
        return bottom;
    }

    private void configureListeners() {
        beforeAfterCheck.selectedProperty().addListener((obs, oldV, selected) -> refreshEditorView(selected));
    }

    private void openImage(Stage stage) {
        FileChooser chooser = ImageFileChooserFactory.createOpenImageChooser();
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }

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
        if (candidate == null) {
            setStatus("No hay imagen para guardar.");
            return;
        }

        FileChooser chooser = ImageFileChooserFactory.createSaveImageChooser();

        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }

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

        processedImage = originalImage;
        beforeAfterCheck.setSelected(false);
        refreshEditorView(false);
        setStatus("Imagen restablecida al estado original.");
    }

    private void applySelectedFilter() {
        if (originalImage == null) {
            setStatus("Carga una imagen antes de aplicar filtros.");
            return;
        }

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

    private void refreshEditorView(boolean showOriginal) {
        BufferedImage imageToShow = showOriginal && originalImage != null ? originalImage : processedImage;
        showImage(editorImageView, imageToShow);
    }

    private void configurePreviewImageView(ImageView imageView) {
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setFitWidth(PREVIEW_FIT_WIDTH);
        imageView.setFitHeight(PREVIEW_FIT_HEIGHT);
    }

    private void loadGeneratedImageInEditor(BufferedImage image) {
        setEditorImages(image);
        showImage(editorImageView, processedImage);
    }

    private BufferedImage processFilter(FilterType filter) {
        return switch (filter) {
            case GRAYSCALE -> ImageProcessor.grayscale(originalImage);
            case NEGATIVE -> ImageProcessor.negative(originalImage);
            case BRIGHTNESS -> ImageProcessor.brightness(originalImage, editorFilterPane.getBrightnessValue());
            case HSV -> ImageProcessor.hsv(
                    originalImage,
                    editorFilterPane.getSaturationValue(),
                    editorFilterPane.getValueFactor()
            );
            case FROSTED -> ImageProcessor.frosted(originalImage);
            case CIRCULAR_FADE -> ImageProcessor.circularFade(originalImage);
            case RETRO1 -> ImageProcessor.retro1(originalImage, editorFilterPane.getRetroLevels());
            case BW_THRESHOLD -> ImageProcessor.bwThreshold(originalImage, editorFilterPane.getThresholdValue());
            case RECOLOR -> applyRecolorFilter();
            case STRETCH_4_BITS -> ImageProcessor.stretch4Bits(originalImage, editorFilterPane.getStretchMode());
            case CONVOLUTION -> ImageProcessor.convolution(originalImage, editorFilterPane.getKernel());
            case NONE -> originalImage;
        };
    }

    private BufferedImage applyRecolorFilter() {
        int rgb = fxColorToRgb(editorFilterPane.getTintColor());
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return ImageProcessor.recolor(originalImage, r, g, b);
    }

    private void setEditorImages(BufferedImage image) {
        originalImage = image;
        processedImage = image;
    }

    private BufferedImage getImageToSave() {
        BufferedImage generatedImage = gradientGeneratorPane.getGeneratedImage();
        return processedImage != null ? processedImage : generatedImage;
    }


    private void showImage(ImageView imageView, BufferedImage image) {
        imageView.setImage(image == null ? null : SwingFXUtils.toFXImage(image, null));
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void setErrorStatus(String prefix, Exception ex) {
        setStatus(prefix + ": " + ex.getMessage());
    }

    private static int fxColorToRgb(Color c) {
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        return (r << 16) | (g << 8) | b;
    }

    public static void main(String[] args) {
        launch(args);
    }
}

