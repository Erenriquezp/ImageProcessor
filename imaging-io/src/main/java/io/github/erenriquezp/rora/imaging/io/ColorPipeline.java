package io.github.erenriquezp.rora.imaging.io;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.io.ImagingException;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.Optional;

/** Conversión a sRGB con los perfiles del archivo. */
final class ColorPipeline {

  private static final int BAND_ROWS = 256;
  private static final int MAX_SRGB_DIFF = 1;

  private ColorPipeline() {}

  /** Perfil válido con {@code components} canales; vacío si no se puede usar. */
  static Optional<ICC_Profile> profile(byte[] icc, int components) {
    try {
      ICC_Profile p = ICC_Profile.getInstance(icc);
      return p.getNumComponents() == components ? Optional.of(p) : Optional.empty();
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** ¿El perfil RGB es, en la práctica, sRGB? Se comprueba convirtiendo una rejilla de colores. */
  static boolean equivalentToSrgb(ICC_Profile p) {
    int steps = 5;
    int n = steps * steps * steps;
    ColorModel cm = byteModel(new ICC_ColorSpace(p));
    WritableRaster r = cm.createCompatibleWritableRaster(n, 1);
    byte[] d = ((DataBufferByte) r.getDataBuffer()).getData();
    for (int i = 0, a = 0; a < steps; a++) {
      for (int b = 0; b < steps; b++) {
        for (int c = 0; c < steps; c++, i += 3) {
          d[i] = (byte) (a * 255 / (steps - 1));
          d[i + 1] = (byte) (b * 255 / (steps - 1));
          d[i + 2] = (byte) (c * 255 / (steps - 1));
        }
      }
    }
    BufferedImage out = new BufferedImage(n, 1, BufferedImage.TYPE_INT_RGB);
    new ColorConvertOp(null).filter(new BufferedImage(cm, r, false, null), out);
    int[] o = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
    for (int i = 0; i < n; i++) {
      int j = i * 3;
      if (Math.abs((o[i] >> 16 & 255) - (d[j] & 255)) > MAX_SRGB_DIFF
          || Math.abs((o[i] >> 8 & 255) - (d[j + 1] & 255)) > MAX_SRGB_DIFF
          || Math.abs((o[i] & 255) - (d[j + 2] & 255)) > MAX_SRGB_DIFF) {
        return false;
      }
    }
    return true;
  }

  private static ColorModel byteModel(ColorSpace cs) {
    return new ComponentColorModel(cs, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
  }

  /** Convierte {@code px} a sRGB por bandas, conservando el alfa. */
  static void applyRgb(int[] px, int w, int h, ICC_Profile p, CancellationToken token)
      throws ImagingException.Cancelled {
    ColorModel cm = byteModel(new ICC_ColorSpace(p));
    ColorConvertOp op = new ColorConvertOp(null);
    for (int y0 = 0; y0 < h; y0 += BAND_ROWS) {
      if (token.isCancelled()) {
        throw new ImagingException.Cancelled();
      }
      int rows = Math.min(BAND_ROWS, h - y0);
      WritableRaster r = cm.createCompatibleWritableRaster(w, rows);
      byte[] d = ((DataBufferByte) r.getDataBuffer()).getData();
      int count = w * rows;
      for (int i = 0, s = y0 * w, j = 0; i < count; i++, s++, j += 3) {
        d[j] = (byte) (px[s] >> 16);
        d[j + 1] = (byte) (px[s] >> 8);
        d[j + 2] = (byte) px[s];
      }
      BufferedImage dst = new BufferedImage(w, rows, BufferedImage.TYPE_INT_RGB);
      op.filter(new BufferedImage(cm, r, false, null), dst);
      int[] o = ((DataBufferInt) dst.getRaster().getDataBuffer()).getData();
      for (int i = 0, s = y0 * w; i < count; i++, s++) {
        px[s] = (px[s] & 0xFF000000) | (o[i] & 0x00FFFFFF);
      }
    }
  }

  /** Tabla muestra de gris → valor sRGB. */
  static int[] grayLut(ICC_Profile p) {
    ColorModel cm = byteModel(new ICC_ColorSpace(p));
    WritableRaster r = cm.createCompatibleWritableRaster(256, 1);
    byte[] d = ((DataBufferByte) r.getDataBuffer()).getData();
    for (int i = 0; i < 256; i++) {
      d[i] = (byte) i;
    }
    BufferedImage out = new BufferedImage(256, 1, BufferedImage.TYPE_INT_RGB);
    new ColorConvertOp(null).filter(new BufferedImage(cm, r, false, null), out);
    int[] o = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
    int[] lut = new int[256];
    for (int i = 0; i < 256; i++) {
      lut[i] = o[i] >> 8 & 255; // verde: el gris neutro da R = G = B salvo redondeo
    }
    return lut;
  }

  /** Bandas CMYK (tintas 0..255) a ARGB con la fórmula entera, sin perfil. */
  static int[] cmykFormula(Raster r, CancellationToken token) throws ImagingException.Cancelled {
    int w = r.getWidth();
    int h = r.getHeight();
    int[] out = new int[w * h];
    int[] row = new int[w * 4];
    for (int y = 0, o = 0; y < h; y++) {
      if ((y & 63) == 0 && token.isCancelled()) {
        throw new ImagingException.Cancelled();
      }
      r.getPixels(0, y, w, 1, row);
      for (int x = 0, j = 0; x < w; x++, j += 4, o++) {
        int inv = 255 - row[j + 3];
        out[o] =
            0xFF000000
                | ((255 - row[j]) * inv + 127) / 255 << 16
                | ((255 - row[j + 1]) * inv + 127) / 255 << 8
                | ((255 - row[j + 2]) * inv + 127) / 255;
      }
    }
    return out;
  }

  /** Bandas CMYK a ARGB con el perfil del archivo. */
  static int[] cmykProfile(Raster r, ICC_Profile cmyk, CancellationToken token)
      throws ImagingException.Cancelled {
    int w = r.getWidth();
    int h = r.getHeight();
    int[] out = new int[w * h];
    ICC_Profile srgb = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
    ColorConvertOp op = new ColorConvertOp(new ICC_Profile[] {cmyk, srgb}, null);
    for (int y0 = 0; y0 < h; y0 += BAND_ROWS) {
      if (token.isCancelled()) {
        throw new ImagingException.Cancelled();
      }
      int rows = Math.min(BAND_ROWS, h - y0);
      Raster band = r.createChild(0, y0, w, rows, 0, 0, null);
      WritableRaster dst = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, w, rows, 3, null);
      op.filter(band, dst);
      byte[] d = ((DataBufferByte) dst.getDataBuffer()).getData();
      for (int i = 0, s = y0 * w, j = 0; i < w * rows; i++, s++, j += 3) {
        out[s] = 0xFF000000 | (d[j] & 255) << 16 | (d[j + 1] & 255) << 8 | (d[j + 2] & 255);
      }
    }
    return out;
  }
}
