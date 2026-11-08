package io.github.erenriquezp.rora.service;

import io.github.erenriquezp.rora.core.image.Img;
import java.awt.image.BufferedImage;

/** Reenvío al puente del producto, para el código y los tests heredados de este paquete. */
final class AwtImg {

  private AwtImg() {}

  static Img toImg(BufferedImage bi) {
    return io.github.erenriquezp.rora.app.imaging.AwtImg.toImg(bi);
  }

  static BufferedImage toBufferedImage(Img img) {
    return io.github.erenriquezp.rora.app.imaging.AwtImg.toBufferedImage(img);
  }
}
