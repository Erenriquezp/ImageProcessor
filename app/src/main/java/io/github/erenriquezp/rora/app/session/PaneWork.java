package io.github.erenriquezp.rora.app.session;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Downscale;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.domain.GradientType;
import io.github.erenriquezp.rora.service.ImageBridge;
import io.github.erenriquezp.rora.service.ImageIOService;
import io.github.erenriquezp.rora.service.ImageProcessor;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Trabajo de los paneles de blending, histograma y degradado, para ejecutar en sus {@link
 * LatestWins} (nunca en el hilo FX). Son las mismas llamadas a la fachada que hacían los paneles:
 * la interfaz no depende de ella directamente.
 */
public final class PaneWork {

  /**
   * Tope de píxeles de la copia que se publica a la interfaz en los paneles. La imagen exacta se
   * conserva aparte para "Usar en editor". Justificación medida en {@code SessionMemoryTest}.
   */
  public static final long DISPLAY_MAX_PIXELS = 4_000_000L;

  private PaneWork() {}

  public static BufferedImage blend(BufferedImage fg, BufferedImage bg, float alpha) {
    return ImageProcessor.blend(fg, bg, alpha);
  }

  public static BufferedImage tripleBlend(
      BufferedImage fg, BufferedImage bg1, BufferedImage bg2, float a1, float a2, float a3) {
    return ImageProcessor.tripleBlend(fg, bg1, bg2, a1, a2, a3);
  }

  public static BufferedImage histogram(BufferedImage image) {
    return ImageProcessor.generateHistogram(image);
  }

  public static BufferedImage gradient(
      GradientType type, int width, int height, int startRgb, int endRgb) {
    return ImageProcessor.generateGradient(type, width, height, startRgb, endRgb);
  }

  /** Lee un archivo de imagen; los errores salen como {@link UncheckedIOException}. */
  public static BufferedImage read(File file) {
    try {
      return ImageIOService.read(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e.getMessage(), e);
    }
  }

  /** Copia para mostrar: la imagen tal cual si cabe en {@code maxPixels}; si no, reducida. */
  public static Img displayCopy(BufferedImage exact, long maxPixels, CancellationToken token) {
    Img img = ImageBridge.toImg(exact);
    long pixels = (long) img.width() * img.height();
    if (maxPixels <= 0 || pixels <= maxPixels) {
      return img;
    }
    double s = Math.sqrt((double) maxPixels / pixels);
    int w = Math.max(1, (int) Math.round(img.width() * s));
    int h = Math.max(1, (int) Math.round(img.height() * s));
    return Downscale.areaAverage(img, w, h, SharedTiles.get(), token, Progress.NONE);
  }

  /** Pool de bandas de los paneles: se crea en el primer uso y vive lo que el proceso. */
  private static final class SharedTiles {
    private static final TileExecutor INSTANCE =
        TileExecutor.create(Math.max(1, TileExecutor.defaultParallelism() / 2));

    static TileExecutor get() {
      return INSTANCE;
    }
  }
}
