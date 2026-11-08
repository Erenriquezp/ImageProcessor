package io.github.erenriquezp.rora.imaging.io;

import io.github.erenriquezp.rora.core.io.FormatId;
import io.github.erenriquezp.rora.core.io.FormatSniffer;
import io.github.erenriquezp.rora.core.io.ImageDecoder;
import io.github.erenriquezp.rora.core.io.ImageEncoder;
import io.github.erenriquezp.rora.core.io.ImagingException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;

/**
 * Punto de entrada a los códecs. El lector y el escritor de cada formato se eligen por clase: el
 * orden de {@code ImageIO} cambia en cuanto hay otro plugin en el module path.
 */
public final class Codecs {

  private static final String JDK = "com.sun.imageio.plugins.";

  private static final Map<FormatId, String> READERS =
      Map.of(
          FormatId.JPEG, JDK + "jpeg.JPEGImageReader",
          FormatId.PNG, JDK + "png.PNGImageReader",
          FormatId.TIFF, JDK + "tiff.TIFFImageReader",
          FormatId.BMP, JDK + "bmp.BMPImageReader",
          FormatId.GIF, JDK + "gif.GIFImageReader",
          FormatId.WEBP, "com.twelvemonkeys.imageio.plugins.webp.WebPImageReader");

  private static final Map<FormatId, String> WRITERS =
      Map.of(
          FormatId.JPEG, JDK + "jpeg.JPEGImageWriter",
          FormatId.PNG, JDK + "png.PNGImageWriter",
          FormatId.TIFF, JDK + "tiff.TIFFImageWriter");

  static {
    // Sin caché en disco: las fotos no deben dejar temporales fuera de la carpeta del usuario.
    ImageIO.setUseCache(false);
  }

  private Codecs() {}

  /** Formato de {@code file} según su contenido. */
  public static FormatId sniff(Path file) throws ImagingException {
    byte[] header = new byte[FormatSniffer.HEADER_BYTES];
    int n;
    try (InputStream in = Files.newInputStream(file)) {
      n = in.readNBytes(header, 0, header.length);
    } catch (IOException e) {
      throw new ImagingException.IoFailure("io.error.read", e.toString(), e);
    }
    return FormatSniffer.detect(header, n, file.getFileName().toString());
  }

  /** Decodificador de {@code format}. */
  public static ImageDecoder decoder(FormatId format) throws ImagingException {
    for (ImageDecoder d : ServiceLoader.load(ImageDecoder.class, Codecs.class.getClassLoader())) {
      if (d.formats().contains(format)) {
        return d;
      }
    }
    throw new ImagingException.UnsupportedFormat("io.error.unsupported", "sin decoder: " + format);
  }

  /** Codificador de {@code format}. */
  public static ImageEncoder encoder(FormatId format) throws ImagingException {
    for (ImageEncoder e : ServiceLoader.load(ImageEncoder.class, Codecs.class.getClassLoader())) {
      if (e.format() == format) {
        return e;
      }
    }
    throw new ImagingException.UnsupportedFormat("io.error.unsupported", "sin encoder: " + format);
  }

  static String imageIoName(FormatId format) {
    return format.name().toLowerCase(java.util.Locale.ROOT);
  }

  static ImageReader reader(FormatId format) throws ImagingException {
    String wanted = READERS.get(format);
    ImageReader found = null;
    for (Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName(imageIoName(format));
        it.hasNext(); ) {
      ImageReader r = it.next();
      if (found == null && r.getClass().getName().equals(wanted)) {
        found = r;
      } else {
        r.dispose();
      }
    }
    if (found == null) {
      throw new ImagingException.UnsupportedFormat("io.error.unsupported", "sin lector " + wanted);
    }
    return found;
  }

  static ImageWriter writer(FormatId format) throws ImagingException {
    String wanted = WRITERS.get(format);
    ImageWriter found = null;
    for (Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName(imageIoName(format));
        it.hasNext(); ) {
      ImageWriter w = it.next();
      if (found == null && w.getClass().getName().equals(wanted)) {
        found = w;
      } else {
        w.dispose();
      }
    }
    if (found == null) {
      throw new ImagingException.EncodingFailure("io.error.encode", "sin escritor " + wanted, null);
    }
    return found;
  }
}
