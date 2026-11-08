package io.github.erenriquezp.rora.service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/** Reenvío a la E/S del producto, para los tests heredados de este paquete. */
public final class ImageIOService {

  private ImageIOService() {}

  public static BufferedImage read(File file) throws IOException {
    return io.github.erenriquezp.rora.app.imaging.ImageIOService.read(file);
  }

  public static void write(BufferedImage image, File file) throws IOException {
    io.github.erenriquezp.rora.app.imaging.ImageIOService.write(image, file);
  }
}
