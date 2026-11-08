package io.github.erenriquezp.rora.app.imaging;

import io.github.erenriquezp.rora.core.io.AlphaFlatten;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Lectura y escritura heredadas, sin gestión de color, orientación ni metadatos. La interfaz usa
 * {@code app.session.StudioIo}; esto queda para el Laboratorio (fondos de blending) y los tests
 * diferenciales.
 *
 * @deprecated usar {@code app.session.StudioIo}
 */
@Deprecated
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
        BufferedImage output = format.equals("jpg") ? withoutAlpha(image) : image;
        if (!ImageIO.write(output, format, file)) {
            throw new IOException("No hay writer para el formato: " + format);
        }
    }

    /** El escritor JPEG del JDK no admite alfa: se aplana sobre blanco. */
    private static BufferedImage withoutAlpha(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        int[] px = image.getRGB(0, 0, w, h, null, 0, w);
        AlphaFlatten.flatten(px, 0xFFFFFF);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, w, h, px, 0, w);
        return out;
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

