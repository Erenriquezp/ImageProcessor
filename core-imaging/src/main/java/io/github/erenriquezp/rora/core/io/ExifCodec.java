package io.github.erenriquezp.rora.core.io;

import io.github.erenriquezp.rora.core.io.ImageMetadata.ExifData;
import io.github.erenriquezp.rora.core.io.ImageMetadata.ExifTag;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parser y escritor EXIF estrictos. Solo conserva etiquetas de una lista blanca; ante cualquier
 * anomalía estructural devuelve vacío: los metadatos dudosos no llegan a la salida. La miniatura
 * (IFD1) no se lee nunca.
 */
public final class ExifCodec {

  /** Acceso aleatorio a los bytes de una estructura TIFF. */
  public interface Source {
    long size() throws IOException;

    byte[] read(long position, int length) throws IOException;
  }

  private static final int BYTE = 1;
  private static final int ASCII = 2;
  private static final int SHORT = 3;
  private static final int LONG = 4;
  private static final int RATIONAL = 5;
  private static final int UNDEFINED = 7;
  private static final int SRATIONAL = 10;
  private static final int[] UNIT = {0, 1, 1, 2, 4, 8, 1, 1, 2, 4, 8, 4, 8};

  private static final int TAG_ORIENTATION = 0x0112;
  private static final int TAG_EXIF_IFD = 0x8769;
  private static final int TAG_GPS_IFD = 0x8825;
  private static final int TAG_EXIF_VERSION = 0x9000;
  private static final int TAG_COLOR_SPACE = 0xA001;
  private static final int TAG_PIXEL_X = 0xA002;
  private static final int TAG_PIXEL_Y = 0xA003;

  private static final int MAX_ENTRIES = 512;
  private static final int MAX_TOTAL_ENTRIES = 2048;
  private static final int MAX_VALUE_BYTES = 4096;
  private static final int MAX_GPS_ID = 31;

  private record Spec(Set<Integer> types, boolean sensitive) {}

  private static final Map<Integer, Spec> IFD0 = new HashMap<>();
  private static final Map<Integer, Spec> EXIF = new HashMap<>();
  private static final Set<Integer> GPS_TYPES =
      Set.of(BYTE, ASCII, SHORT, LONG, RATIONAL, UNDEFINED);

  static {
    open(IFD0, ASCII, 0x010F, 0x0110, 0x0132, 0x013B, 0x8298); // Make, Model, DateTime, Artist, ©
    sensitive(IFD0, ASCII, 0x010E); // ImageDescription

    open(EXIF, RATIONAL, 0x829A, 0x829D, 0x9202, 0x9205, 0x920A); // tiempo, f, apertura, focal
    open(EXIF, SRATIONAL, 0x9201, 0x9204); // obturación, compensación
    open(EXIF, SHORT, 0x8822, 0x8827, 0x8830, 0x9207, 0x9208, 0x9209); // programa, ISO, medición…
    open(EXIF, SHORT, 0xA402, 0xA403, 0xA405, 0xA406); // modo, balance, focal 35, escena
    open(EXIF, RATIONAL, 0xA404, 0xA432); // zoom digital, datos del objetivo
    open(EXIF, UNDEFINED, TAG_EXIF_VERSION);
    open(EXIF, ASCII, 0x9003, 0x9004, 0x9290, 0x9291, 0x9292, 0xA433, 0xA434); // fechas, objetivo
    sensitive(EXIF, ASCII, 0x9010, 0x9011, 0x9012); // OffsetTime*: desvela la zona
    sensitive(EXIF, SHORT, 0xA214); // SubjectLocation
    sensitive(EXIF, UNDEFINED, 0x9286); // UserComment
    sensitive(EXIF, ASCII, 0xA430, 0xA431, 0xA435); // propietario y números de serie
  }

  private ExifCodec() {}

  private static void open(Map<Integer, Spec> table, int type, int... ids) {
    add(table, type, false, ids);
  }

  private static void sensitive(Map<Integer, Spec> table, int type, int... ids) {
    add(table, type, true, ids);
  }

  private static void add(Map<Integer, Spec> table, int type, boolean sensitive, int... ids) {
    for (int id : ids) {
      table.put(id, new Spec(Set.of(type), sensitive));
    }
  }

  /** Fuente sobre un array. */
  public static Source source(byte[] data) {
    return new Source() {
      @Override
      public long size() {
        return data.length;
      }

      @Override
      public byte[] read(long position, int length) throws IOException {
        if (position < 0 || position + length > data.length) {
          throw new EOFException();
        }
        return Arrays.copyOfRange(data, (int) position, (int) position + length);
      }
    };
  }

  /** Interpreta un bloque TIFF/EXIF; vacío si tiene cualquier anomalía. */
  public static Optional<ExifData> parse(byte[] tiff) {
    return parse(source(tiff));
  }

  /** Interpreta una estructura TIFF/EXIF; vacío si tiene cualquier anomalía. */
  public static Optional<ExifData> parse(Source source) {
    try {
      return Optional.of(new Reader(source).read());
    } catch (IOException | RuntimeException e) {
      return Optional.empty();
    }
  }

  /** Bytes crudos de una etiqueta de IFD0, si existe y mide como mucho {@code maxBytes}. */
  public static Optional<byte[]> ifd0Tag(Source source, int tag, int maxBytes) {
    try {
      return new Reader(source).rawIfd0Tag(tag, maxBytes);
    } catch (IOException | RuntimeException e) {
      return Optional.empty();
    }
  }

  private static final class Anomaly extends RuntimeException {
    private static final long serialVersionUID = 1L;

    Anomaly(String message) {
      super(message, null, false, false);
    }
  }

  private static final class Reader {
    private final Source s;
    private final long total;
    private ByteOrder order;
    private final Set<Long> visited = new HashSet<>();
    private int entries;
    private int orientation = 1;
    private long exifPointer = -1;
    private long gpsPointer = -1;

    Reader(Source s) throws IOException {
      this.s = s;
      this.total = s.size();
    }

    private long header() throws IOException {
      if (total < 8) {
        throw new Anomaly("corto");
      }
      byte[] h = s.read(0, 8);
      if (h[0] == 'I' && h[1] == 'I') {
        order = ByteOrder.LITTLE_ENDIAN;
      } else if (h[0] == 'M' && h[1] == 'M') {
        order = ByteOrder.BIG_ENDIAN;
      } else {
        throw new Anomaly("orden");
      }
      ByteBuffer b = ByteBuffer.wrap(h).order(order);
      if ((b.getShort(2) & 0xFFFF) != 42) {
        throw new Anomaly("magia");
      }
      return b.getInt(4) & 0xFFFFFFFFL;
    }

    ExifData read() throws IOException {
      long first = header();
      List<ExifTag> ifd0 = readIfd(first, IFD0, true, false);
      List<ExifTag> exif = exifPointer >= 0 ? readIfd(exifPointer, EXIF, false, false) : List.of();
      List<ExifTag> gps = gpsPointer >= 0 ? readIfd(gpsPointer, Map.of(), false, true) : List.of();
      return new ExifData(orientation, ifd0, exif, gps);
    }

    Optional<byte[]> rawIfd0Tag(int wanted, int maxBytes) throws IOException {
      long off = header();
      ByteBuffer b = ifdEntries(off);
      int n = b.getShort(0) & 0xFFFF;
      for (int i = 0; i < n; i++) {
        int at = 2 + 12 * i;
        if ((b.getShort(at) & 0xFFFF) == wanted) {
          long size = valueSize(b, at);
          if (size > maxBytes) {
            return Optional.empty();
          }
          return Optional.of(value(b, at, size));
        }
      }
      return Optional.empty();
    }

    private ByteBuffer ifdEntries(long off) throws IOException {
      if (off < 8 || off + 2 > total) {
        throw new Anomaly("IFD fuera");
      }
      int n = ByteBuffer.wrap(s.read(off, 2)).order(order).getShort() & 0xFFFF;
      if (n > MAX_ENTRIES) {
        throw new Anomaly("demasiadas entradas");
      }
      if (off + 2 + 12L * n > total) {
        throw new Anomaly("IFD truncado");
      }
      return ByteBuffer.wrap(s.read(off, 2 + 12 * n)).order(order);
    }

    private long valueSize(ByteBuffer b, int at) {
      int type = b.getShort(at + 2) & 0xFFFF;
      if (type < 1 || type >= UNIT.length) {
        throw new Anomaly("tipo");
      }
      long size = (b.getInt(at + 4) & 0xFFFFFFFFL) * UNIT[type];
      if (size > 4) {
        long pos = b.getInt(at + 8) & 0xFFFFFFFFL;
        if (pos + size > total) {
          throw new Anomaly("valor fuera");
        }
      }
      return size;
    }

    private byte[] value(ByteBuffer b, int at, long size) throws IOException {
      if (size <= 4) {
        byte[] v = new byte[(int) size];
        for (int i = 0; i < v.length; i++) {
          v[i] = b.get(at + 8 + i);
        }
        return v;
      }
      return s.read(b.getInt(at + 8) & 0xFFFFFFFFL, (int) size);
    }

    private List<ExifTag> readIfd(long off, Map<Integer, Spec> specs, boolean root, boolean gps)
        throws IOException {
      if (!visited.add(off)) {
        throw new Anomaly("ciclo");
      }
      ByteBuffer b = ifdEntries(off);
      int n = b.getShort(0) & 0xFFFF;
      entries += n;
      if (entries > MAX_TOTAL_ENTRIES) {
        throw new Anomaly("demasiadas entradas");
      }
      Set<Integer> seen = new HashSet<>();
      List<ExifTag> out = new ArrayList<>();
      for (int i = 0; i < n; i++) {
        int at = 2 + 12 * i;
        int id = b.getShort(at) & 0xFFFF;
        if (!seen.add(id)) {
          throw new Anomaly("etiqueta repetida");
        }
        int type = b.getShort(at + 2) & 0xFFFF;
        long size = valueSize(b, at);
        if (root && id == TAG_ORIENTATION) {
          if (type == SHORT && size == 2) {
            orientation = Orientation.normalize(b.getShort(at + 8) & 0xFFFF);
          }
          continue;
        }
        if (root && (id == TAG_EXIF_IFD || id == TAG_GPS_IFD)) {
          if (type != LONG || size != 4) {
            throw new Anomaly("puntero");
          }
          long pointer = b.getInt(at + 8) & 0xFFFFFFFFL;
          if (id == TAG_EXIF_IFD) {
            exifPointer = pointer;
          } else {
            gpsPointer = pointer;
          }
          continue;
        }
        Spec spec =
            gps
                ? (id <= MAX_GPS_ID && GPS_TYPES.contains(type) ? new Spec(GPS_TYPES, true) : null)
                : specs.get(id);
        if (spec == null || !spec.types().contains(type) || size > MAX_VALUE_BYTES || size == 0) {
          continue;
        }
        byte[] raw = value(b, at, size);
        ExifTag tag = normalize(id, type, b.getInt(at + 4), raw, spec.sensitive());
        if (tag != null) {
          out.add(tag);
        }
      }
      out.sort(Comparator.comparingInt(ExifTag::id));
      return out;
    }

    private ExifTag normalize(int id, int type, int count, byte[] raw, boolean sensitive) {
      if (type == ASCII) {
        return ascii(id, raw, sensitive);
      }
      int width =
          switch (type) {
            case SHORT, 8 -> 2;
            case LONG, RATIONAL, 9, SRATIONAL, 11 -> 4;
            case 12 -> 8;
            default -> 1;
          };
      if (order == ByteOrder.BIG_ENDIAN && width > 1) {
        for (int i = 0; i + width <= raw.length; i += width) {
          for (int a = i, b = i + width - 1; a < b; a++, b--) {
            byte t = raw[a];
            raw[a] = raw[b];
            raw[b] = t;
          }
        }
      }
      return new ExifTag(id, type, count, raw, sensitive);
    }

    private static ExifTag ascii(int id, byte[] raw, boolean sensitive) {
      int end = raw.length;
      while (end > 0 && raw[end - 1] == 0) {
        end--;
      }
      if (end == 0) {
        return null;
      }
      for (int i = 0; i < end; i++) {
        int c = raw[i] & 0xFF;
        if (c < 0x20 || c == 0x7F) {
          return null;
        }
      }
      byte[] clean = Arrays.copyOf(raw, end + 1);
      return new ExifTag(id, ASCII, clean.length, clean, sensitive);
    }
  }

  /** Bloque TIFF little-endian: siempre Orientation=1 y las dimensiones dadas. */
  public static byte[] write(ExifData d, int width, int height) {
    List<ExifTag> exif = new ArrayList<>(d.exif());
    if (exif.stream().noneMatch(t -> t.id() == TAG_EXIF_VERSION)) {
      exif.add(
          new ExifTag(
              TAG_EXIF_VERSION, UNDEFINED, 4, "0232".getBytes(StandardCharsets.US_ASCII), false));
    }
    exif.add(tagShort(TAG_COLOR_SPACE, 1));
    exif.add(tagLong(TAG_PIXEL_X, width));
    exif.add(tagLong(TAG_PIXEL_Y, height));
    exif.sort(Comparator.comparingInt(ExifTag::id));

    boolean hasGps = !d.gps().isEmpty();
    int n0 = d.ifd0().size() + 2 + (hasGps ? 1 : 0);
    int offExif = 8 + ifdSize(n0);
    int offGps = offExif + ifdSize(exif.size());

    List<ExifTag> ifd0 = new ArrayList<>(d.ifd0());
    ifd0.add(tagShort(TAG_ORIENTATION, 1));
    ifd0.add(tagLong(TAG_EXIF_IFD, offExif));
    if (hasGps) {
      ifd0.add(tagLong(TAG_GPS_IFD, offGps));
    }
    ifd0.sort(Comparator.comparingInt(ExifTag::id));

    int data = offGps + (hasGps ? ifdSize(d.gps().size()) : 0);
    int size = data + dataSize(ifd0) + dataSize(exif) + dataSize(d.gps());
    ByteBuffer out = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    out.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
    int next = writeIfd(out, 8, ifd0, data);
    next = writeIfd(out, offExif, exif, next);
    if (hasGps) {
      writeIfd(out, offGps, d.gps(), next);
    }
    return out.array();
  }

  private static ExifTag tagShort(int id, int v) {
    byte[] b = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) v).array();
    return new ExifTag(id, SHORT, 1, b, false);
  }

  private static ExifTag tagLong(int id, int v) {
    byte[] b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array();
    return new ExifTag(id, LONG, 1, b, false);
  }

  private static int ifdSize(int n) {
    return 2 + 12 * n + 4;
  }

  private static int dataSize(List<ExifTag> tags) {
    int s = 0;
    for (ExifTag t : tags) {
      if (t.value().length > 4) {
        s += (t.value().length + 1) & ~1;
      }
    }
    return s;
  }

  /** Escribe el IFD en {@code at} y sus valores en {@code dataAt}; devuelve el nuevo final. */
  private static int writeIfd(ByteBuffer out, int at, List<ExifTag> tags, int dataAt) {
    out.position(at);
    out.putShort((short) tags.size());
    int next = dataAt;
    for (ExifTag t : tags) {
      out.putShort((short) t.id()).putShort((short) t.type()).putInt(t.count());
      if (t.value().length <= 4) {
        byte[] inline = Arrays.copyOf(t.value(), 4);
        out.put(inline);
      } else {
        out.putInt(next);
        int resume = out.position();
        out.position(next);
        out.put(t.value());
        next += (t.value().length + 1) & ~1;
        out.position(resume);
      }
    }
    out.putInt(0);
    return next;
  }
}
