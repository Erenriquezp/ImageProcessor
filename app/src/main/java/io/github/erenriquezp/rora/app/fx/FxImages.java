package io.github.erenriquezp.rora.app.fx;

import io.github.erenriquezp.rora.app.session.FramePool;
import io.github.erenriquezp.rora.core.image.Img;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

/**
 * Conversión {@code Img → WritableImage} para el visor, pensada para ejecutarse en el worker
 * (sustituye a {@code SwingFXUtils.toFXImage}, que creaba una imagen nueva de resolución completa
 * en el hilo FX en cada refresco). Escribe por bloques de filas con un búfer reutilizado, sin
 * copiar la imagen entera.
 *
 * <p>Formato: {@code INT_ARGB_PRE} (premultiplicado aquí) o {@code INT_ARGB} (premultiplica
 * JavaFX); se elige con {@code -Drora.fx.format=argb|pre}. La diferencia de redondeo de la
 * premultiplicación solo afecta a lo que se pinta: el cuentagotas lee la {@code Img}.
 */
public final class FxImages {

  /** Formato de píxel con el que se escribe en la {@code WritableImage}. */
  public enum Format {
    INT_ARGB,
    INT_ARGB_PRE
  }

  private static final int ROWS = 64;

  private FxImages() {}

  /** Formato configurado ({@code -Drora.fx.format}); por defecto el que ganó en la medición. */
  public static Format configuredFormat() {
    String f = System.getProperty("rora.fx.format", "pre");
    return "argb".equalsIgnoreCase(f) ? Format.INT_ARGB : Format.INT_ARGB_PRE;
  }

  /** Pool de imágenes del visor (ver {@link FramePool}). */
  public static FramePool<WritableImage> newPool() {
    return new FramePool<>(
        WritableImage::new, img -> (int) img.getWidth(), img -> (int) img.getHeight());
  }

  /** Imagen del pool con el contenido de {@code img} (en el hilo que llama: el worker). */
  public static WritableImage toFx(Img img, FramePool<WritableImage> pool, Format format) {
    WritableImage target = pool.acquire(img.width(), img.height());
    write(img, target, format);
    return target;
  }

  /** Escribe {@code img} en {@code target} (mismo tamaño). */
  public static void write(Img img, WritableImage target, Format format) {
    int w = img.width();
    int h = img.height();
    if ((int) target.getWidth() != w || (int) target.getHeight() != h) {
      throw new IllegalArgumentException("Tamaños distintos");
    }
    PixelWriter pw = target.getPixelWriter();
    PixelFormat<java.nio.IntBuffer> pf =
        format == Format.INT_ARGB
            ? PixelFormat.getIntArgbInstance()
            : PixelFormat.getIntArgbPreInstance();
    int rows = Math.max(1, Math.min(h, ROWS));
    int[] buf = new int[w * rows];
    for (int y0 = 0; y0 < h; y0 += rows) {
      int n = Math.min(rows, h - y0);
      for (int r = 0; r < n; r++) {
        img.copyRow(y0 + r, buf, r * w);
      }
      if (format == Format.INT_ARGB_PRE) {
        premultiply(buf, w * n);
      }
      pw.setPixels(0, y0, w, n, pf, buf, 0, w);
    }
  }

  /** Premultiplica en el sitio los {@code n} primeros píxeles: {@code c' = (c·a + 127) / 255}. */
  public static void premultiply(int[] px, int n) {
    for (int i = 0; i < n; i++) {
      int p = px[i];
      int a = p >>> 24;
      if (a == 255) {
        continue;
      }
      if (a == 0) {
        px[i] = 0;
        continue;
      }
      int r = (((p >> 16) & 0xFF) * a + 127) / 255;
      int g = (((p >> 8) & 0xFF) * a + 127) / 255;
      int b = ((p & 0xFF) * a + 127) / 255;
      px[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }
  }
}
