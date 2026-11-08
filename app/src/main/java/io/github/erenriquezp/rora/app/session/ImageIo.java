package io.github.erenriquezp.rora.app.session;

import io.github.erenriquezp.rora.app.imaging.ImageIOService;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Carga y guardado para ejecutar fuera del hilo FX (en una {@code Task}). La carga convierte a
 * {@link SessionSource} y suelta el {@code BufferedImage} salvo en los formatos que lo necesitan.
 */
public final class ImageIo {

  private ImageIo() {}

  public static SessionSource load(File file) throws IOException {
    return SessionSource.of(ImageIOService.read(file));
  }

  /**
   * Escribe {@code image} (del tipo que da {@link EditSession#export}) con el formato de la
   * extensión.
   */
  public static void save(BufferedImage image, File file) throws IOException {
    ImageIOService.write(image, file);
  }
}
