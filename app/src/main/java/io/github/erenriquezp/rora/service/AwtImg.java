package io.github.erenriquezp.rora.service;

import io.github.erenriquezp.rora.core.image.Img;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;

/**
 * Puente exacto entre {@link BufferedImage} y {@link Img}; vive fuera del núcleo para que este no
 * dependa de AWT.
 *
 * <ul>
 *   <li>{@link #toImg}: para todo píxel, {@code toImg(bi).argb(x, y) == bi.getRGB(x, y)}.
 *   <li>{@link #toBufferedImage}: {@code TYPE_INT_ARGB} idéntico al que produce el legacy con
 *       {@code setRGB} píxel a píxel.
 * </ul>
 */
final class AwtImg {

  private AwtImg() {}

  static Img toImg(BufferedImage bi) {
    int w = bi.getWidth();
    int h = bi.getHeight();
    int type = bi.getType();
    WritableRaster raster = bi.getRaster();
    if ((type == BufferedImage.TYPE_INT_ARGB || type == BufferedImage.TYPE_INT_RGB)
        && raster.getSampleModel() instanceof SinglePixelPackedSampleModel
        && raster.getDataBuffer() instanceof DataBufferInt) {
      // Para estos modelos getRGB devuelve el entero almacenado (con alpha 255 si no hay alpha).
      int[] px = (int[]) raster.getDataElements(0, 0, w, h, null);
      if (type == BufferedImage.TYPE_INT_RGB) {
        for (int i = 0; i < px.length; i++) {
          px[i] |= 0xFF000000;
        }
      }
      return Img.adopt(w, h, px);
    }
    // Camino general: el getRGB en bloque usa por píxel el mismo ColorModel.getRGB que getRGB(x,y).
    return Img.adopt(w, h, bi.getRGB(0, 0, w, h, null, 0, w));
  }

  static BufferedImage toBufferedImage(Img img) {
    BufferedImage out = new BufferedImage(img.width(), img.height(), BufferedImage.TYPE_INT_ARGB);
    // setDataElements copia en el raster sin "robar" el buffer (Java2D puede seguir acelerándolo).
    out.getRaster().setDataElements(0, 0, img.width(), img.height(), img.toArray());
    return out;
  }
}
