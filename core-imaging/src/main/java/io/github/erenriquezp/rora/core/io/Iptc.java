package io.github.erenriquezp.rora.core.io;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/** IPTC-IIM dentro de un bloque Photoshop (APP13 de JPEG): solo el recurso 0x0404. */
public final class Iptc {

  /** Cabecera del segmento APP13, con su NUL final. */
  public static final byte[] APP13_ID = "Photoshop 3.0\0".getBytes(StandardCharsets.ISO_8859_1);

  private static final int RESOURCE_IPTC = 0x0404;

  private Iptc() {}

  /** Bloque IPTC válido del contenido de APP13 que sigue a {@link #APP13_ID}. */
  public static Optional<byte[]> fromResources(byte[] data) {
    int i = 0;
    while (i + 12 <= data.length) {
      if (data[i] != '8' || data[i + 1] != 'B' || data[i + 2] != 'I' || data[i + 3] != 'M') {
        return Optional.empty();
      }
      int id = (data[i + 4] & 0xFF) << 8 | (data[i + 5] & 0xFF);
      int name = (data[i + 6] & 0xFF) + 1;
      name += name & 1;
      int sizeAt = i + 6 + name;
      if (sizeAt + 4 > data.length) {
        return Optional.empty();
      }
      long size = ByteBuffer.wrap(data, sizeAt, 4).getInt() & 0xFFFFFFFFL;
      int dataAt = sizeAt + 4;
      if (dataAt + size > data.length) {
        return Optional.empty();
      }
      if (id == RESOURCE_IPTC) {
        byte[] iim = Arrays.copyOfRange(data, dataAt, dataAt + (int) size);
        return valid(iim) ? Optional.of(iim) : Optional.empty();
      }
      i = dataAt + (int) size + (int) (size & 1);
    }
    return Optional.empty();
  }

  /** ¿Es una secuencia IIM bien formada, sin conjuntos extendidos? */
  public static boolean valid(byte[] iim) {
    int i = 0;
    while (i < iim.length) {
      if (iim[i] == 0) {
        for (; i < iim.length; i++) {
          if (iim[i] != 0) {
            return false;
          }
        }
        return true;
      }
      if (iim[i] != 0x1C || i + 5 > iim.length || (iim[i + 3] & 0x80) != 0) {
        return false;
      }
      i += 5 + ((iim[i + 3] & 0xFF) << 8 | (iim[i + 4] & 0xFF));
    }
    return i == iim.length;
  }

  /** Contenido de APP13 con {@code iim} como único recurso. */
  public static byte[] toApp13(byte[] iim) {
    int pad = iim.length & 1;
    ByteBuffer b = ByteBuffer.allocate(APP13_ID.length + 12 + iim.length + pad);
    b.put(APP13_ID).put((byte) '8').put((byte) 'B').put((byte) 'I').put((byte) 'M');
    b.putShort((short) RESOURCE_IPTC).putShort((short) 0).putInt(iim.length).put(iim);
    return b.array();
  }
}
