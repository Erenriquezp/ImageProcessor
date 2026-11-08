package io.github.erenriquezp.rora.app.session;

import io.github.erenriquezp.rora.app.imaging.ImageIOService;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Carga y guardado heredados, sin gestión de color, orientación ni metadatos. La interfaz usa
 * {@link StudioIo}; esto queda para los tests diferenciales.
 *
 * @deprecated usar {@link StudioIo}
 */
@Deprecated
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
