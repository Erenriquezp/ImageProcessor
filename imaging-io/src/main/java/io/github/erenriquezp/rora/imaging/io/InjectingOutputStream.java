package io.github.erenriquezp.rora.imaging.io;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.ToIntFunction;

/**
 * Inserta bytes en un punto cercano al principio del flujo, sin retener el archivo en memoria.
 *
 * <p>{@code insertionPoint} recibe lo escrito hasta el momento y devuelve la posición donde
 * insertar, o -1 si todavía necesita más bytes.
 */
final class InjectingOutputStream extends FilterOutputStream {

  private final ToIntFunction<byte[]> insertionPoint;
  private final byte[] extra;
  private final ByteArrayOutputStream head = new ByteArrayOutputStream();
  private boolean injected;

  InjectingOutputStream(OutputStream out, byte[] extra, ToIntFunction<byte[]> insertionPoint) {
    super(out);
    this.extra = extra;
    this.insertionPoint = insertionPoint;
  }

  /** Tras SOI y, si lo hay, el segmento APP0 (JFIF). */
  static int afterJpegApp0(byte[] h) {
    if (h.length < 4) {
      return -1;
    }
    if ((h[2] & 0xFF) == 0xFF && (h[3] & 0xFF) == 0xE0) {
      if (h.length < 6) {
        return -1;
      }
      int end = 4 + ((h[4] & 0xFF) << 8 | (h[5] & 0xFF));
      return h.length >= end ? end : -1;
    }
    return 2;
  }

  /** Tras la firma y el chunk IHDR de un PNG. */
  static int afterPngIhdr(byte[] h) {
    return h.length >= 33 ? 33 : -1;
  }

  @Override
  public void write(int b) throws IOException {
    write(new byte[] {(byte) b}, 0, 1);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    if (injected) {
      out.write(b, off, len);
      return;
    }
    head.write(b, off, len);
    byte[] bytes = head.toByteArray();
    int at = insertionPoint.applyAsInt(bytes);
    if (at >= 0) {
      out.write(bytes, 0, at);
      out.write(extra);
      out.write(bytes, at, bytes.length - at);
      injected = true;
      head.reset();
    }
  }

  @Override
  public void close() throws IOException {
    if (!injected && head.size() > 0) {
      out.write(head.toByteArray());
      head.reset();
    }
    super.close();
  }
}
