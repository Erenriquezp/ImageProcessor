package com.example.imageprocessor.app.ui;

import javafx.stage.FileChooser;

public final class ImageFileChooserFactory {

    private ImageFileChooserFactory() {
    }

    public static FileChooser createOpenImageChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Abrir imagen");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Imagenes",
                "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"
        ));
        return chooser;
    }

    public static FileChooser createSaveImageChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar imagen");
        chooser.setInitialFileName("resultado.png");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PNG", "*.png"),
                new FileChooser.ExtensionFilter("JPG", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("BMP", "*.bmp"),
                new FileChooser.ExtensionFilter("GIF", "*.gif")
        );
        return chooser;
    }
}

