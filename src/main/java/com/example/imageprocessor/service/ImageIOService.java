package com.example.imageprocessor.service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public final class ImageIOService {

    private ImageIOService() {
    }

    public static BufferedImage read(File file) throws IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("No se pudo leer la imagen: " + file.getAbsolutePath());
        }
        return image;
    }

    public static void write(BufferedImage image, File file) throws IOException {
        String format = detectFormat(file.getName());
        if (!ImageIO.write(image, format, file)) {
            throw new IOException("No hay writer para el formato: " + format);
        }
    }

    private static String detectFormat(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx == -1 || idx == fileName.length() - 1) {
            return "png";
        }
        String ext = fileName.substring(idx + 1).toLowerCase();
        return switch (ext) {
            case "jpg", "jpeg" -> "jpg";
            case "bmp" -> "bmp";
            case "gif" -> "gif";
            default -> "png";
        };
    }
}

