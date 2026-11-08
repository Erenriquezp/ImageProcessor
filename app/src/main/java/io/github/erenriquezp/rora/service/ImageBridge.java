package io.github.erenriquezp.rora.service;

import io.github.erenriquezp.rora.core.image.Img;
import java.awt.image.BufferedImage;

/** Acceso público al puente exacto {@code BufferedImage ↔ Img} para la capa de sesión. */
public final class ImageBridge {

  private ImageBridge() {}

  /** Exactamente {@code getRGB(x, y)} de cada píxel. */
  public static Img toImg(BufferedImage bi) {
    return AwtImg.toImg(bi);
  }

  /** {@code TYPE_INT_ARGB} idéntico a {@code setRGB} píxel a píxel. */
  public static BufferedImage toBufferedImage(Img img) {
    return AwtImg.toBufferedImage(img);
  }
}
