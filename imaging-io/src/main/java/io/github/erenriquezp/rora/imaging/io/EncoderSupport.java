package io.github.erenriquezp.rora.imaging.io;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.io.ImagingException;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageWriter;
import javax.imageio.event.IIOWriteProgressListener;

/** Utilidades comunes de los codificadores. */
final class EncoderSupport {

  private static final int[] RGB_MASKS = {0x00FF0000, 0x0000FF00, 0x000000FF};
  private static final int[] ARGB_MASKS = {0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000};

  private EncoderSupport() {}

  /** {@code BufferedImage} sobre {@code px}, sin copiarlo. */
  static BufferedImage wrap(int[] px, int w, int h, boolean alpha) {
    DataBufferInt db = new DataBufferInt(px, px.length);
    WritableRaster r = Raster.createPackedRaster(db, w, h, w, alpha ? ARGB_MASKS : RGB_MASKS, null);
    ColorModel cm =
        alpha ? ColorModel.getRGBdefault() : new DirectColorModel(24, 0xFF0000, 0xFF00, 0xFF);
    return new BufferedImage(cm, r, false, null);
  }

  /** ¿Algún píxel no es opaco? */
  static boolean hasAlpha(int[] px) {
    for (int p : px) {
      if (p >>> 24 != 255) {
        return true;
      }
    }
    return false;
  }

  static void attach(ImageWriter writer, CancellationToken token, Progress progress) {
    writer.addIIOWriteProgressListener(
        new IIOWriteProgressListener() {
          @Override
          public void imageProgress(ImageWriter source, float percentageDone) {
            if (token.isCancelled()) {
              source.abort();
            }
            progress.update(Math.min(100, (long) percentageDone), 100);
          }

          @Override
          public void imageStarted(ImageWriter source, int imageIndex) {}

          @Override
          public void imageComplete(ImageWriter source) {}

          @Override
          public void thumbnailStarted(ImageWriter source, int imageIndex, int thumbnailIndex) {}

          @Override
          public void thumbnailProgress(ImageWriter source, float percentageDone) {}

          @Override
          public void thumbnailComplete(ImageWriter source) {}

          @Override
          public void writeAborted(ImageWriter source) {}
        });
  }

  static void throwIfCancelled(CancellationToken token) throws ImagingException.Cancelled {
    if (token.isCancelled()) {
      throw new ImagingException.Cancelled();
    }
  }

  /** Error de escritura traducido: disco o permisos frente a fallo del códec. */
  static ImagingException translate(Exception e) {
    if (e instanceof java.nio.file.FileSystemException
        || e instanceof java.io.FileNotFoundException) {
      return new ImagingException.IoFailure("io.error.write", e.toString(), e);
    }
    return new ImagingException.EncodingFailure("io.error.encode", e.toString(), e);
  }

  static byte[] jpegSegment(int marker, byte[]... parts) throws IOException {
    int n = 0;
    for (byte[] p : parts) {
      n += p.length;
    }
    ByteBuffer b = ByteBuffer.allocate(4 + n);
    b.put((byte) 0xFF).put((byte) marker).putShort((short) (n + 2));
    for (byte[] p : parts) {
      b.put(p);
    }
    return b.array();
  }

  static byte[] pngChunk(String type, byte[] data) {
    ByteBuffer b = ByteBuffer.allocate(12 + data.length);
    b.putInt(data.length).put(type.getBytes(StandardCharsets.US_ASCII)).put(data);
    CRC32 crc = new CRC32();
    crc.update(b.array(), 4, 4 + data.length);
    b.putInt((int) crc.getValue());
    return b.array();
  }

  static byte[] concat(byte[]... parts) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] p : parts) {
      out.write(p);
    }
    return out.toByteArray();
  }
}
