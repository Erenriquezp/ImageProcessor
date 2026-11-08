package io.github.erenriquezp.rora.core.io;

import io.github.erenriquezp.rora.core.io.ImageMetadata.ExifData;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Extrae los metadatos de los contenedores sin decodificar píxeles. Si la estructura está dañada,
 * devuelve {@link ImageMetadata#EMPTY}: un metadato dudoso se descarta.
 */
public final class ContainerScanner {

  private static final int MAX_ICC = 4 << 20;
  private static final int MAX_XMP = 8 << 20;
  private static final int MAX_CHUNK = 16 << 20;
  private static final int TAG_XMP = 700;
  private static final int TAG_IPTC = 33723;
  private static final int TAG_ICC = 34675;
  private static final byte[] EXIF_ID = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] XMP_ID =
      "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] ICC_ID = "ICC_PROFILE\0".getBytes(StandardCharsets.ISO_8859_1);
  private static final String PNG_XMP_KEY = "XML:com.adobe.xmp";

  private ContainerScanner() {}

  /** Metadatos de {@code file}; {@code EMPTY} ante cualquier problema. */
  public static ImageMetadata scan(FormatId format, Path file) {
    try (SeekableByteChannel ch = Files.newByteChannel(file)) {
      FileSource src = new FileSource(ch);
      return switch (format) {
        case JPEG -> jpeg(src);
        case PNG -> png(src);
        case TIFF -> tiff(src);
        case WEBP -> webp(src);
        case BMP, GIF -> ImageMetadata.EMPTY;
      };
    } catch (IOException | RuntimeException e) {
      return ImageMetadata.EMPTY;
    }
  }

  private static final class FileSource implements ExifCodec.Source {
    private final SeekableByteChannel ch;

    FileSource(SeekableByteChannel ch) {
      this.ch = ch;
    }

    @Override
    public long size() throws IOException {
      return ch.size();
    }

    @Override
    public byte[] read(long position, int length) throws IOException {
      ByteBuffer b = ByteBuffer.allocate(length);
      ch.position(position);
      while (b.hasRemaining()) {
        if (ch.read(b) < 0) {
          throw new EOFException();
        }
      }
      return b.array();
    }
  }

  private static boolean starts(byte[] data, byte[] prefix) {
    return data.length >= prefix.length
        && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
  }

  private static byte[] after(byte[] data, int n) {
    return Arrays.copyOfRange(data, n, data.length);
  }

  private static ImageMetadata jpeg(FileSource s) throws IOException {
    Optional<ExifData> exif = Optional.empty();
    Optional<byte[]> xmp = Optional.empty();
    Optional<byte[]> iptc = Optional.empty();
    Map<Integer, byte[]> icc = new HashMap<>();
    int iccCount = 0;
    boolean damaged = false;
    long pos = 2;
    long size = s.size();
    while (pos + 4 <= size) {
      byte[] h = s.read(pos, 4);
      if ((h[0] & 0xFF) != 0xFF) {
        break;
      }
      int marker = h[1] & 0xFF;
      if (marker == 0xFF) {
        pos++;
        continue;
      }
      if (marker == 0xDA || marker == 0xD9) {
        break;
      }
      int len = (h[2] & 0xFF) << 8 | (h[3] & 0xFF);
      if (len < 2 || pos + 2 + len > size) {
        break;
      }
      if (marker == 0xE1 || marker == 0xE2 || marker == 0xED) {
        byte[] d = s.read(pos + 4, len - 2);
        if (marker == 0xE1 && exif.isEmpty() && starts(d, EXIF_ID)) {
          exif = ExifCodec.parse(after(d, EXIF_ID.length));
          damaged = exif.isEmpty();
        } else if (marker == 0xE1 && xmp.isEmpty() && starts(d, XMP_ID)) {
          xmp = Optional.of(after(d, XMP_ID.length));
        } else if (marker == 0xED && iptc.isEmpty() && starts(d, Iptc.APP13_ID)) {
          iptc = Iptc.fromResources(after(d, Iptc.APP13_ID.length));
        } else if (marker == 0xE2 && starts(d, ICC_ID) && d.length > ICC_ID.length + 2) {
          iccCount = d[ICC_ID.length + 1] & 0xFF;
          icc.put(d[ICC_ID.length] & 0xFF, after(d, ICC_ID.length + 2));
        }
      }
      pos += 2 + len;
    }
    return new ImageMetadata(exif, xmp, iptc, assemble(icc, iccCount), Map.of(), damaged);
  }

  private static Optional<byte[]> assemble(Map<Integer, byte[]> parts, int count)
      throws IOException {
    if (parts.isEmpty() || parts.size() != count) {
      return Optional.empty();
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (int i = 1; i <= count; i++) {
      byte[] p = parts.get(i);
      if (p == null || out.size() + p.length > MAX_ICC) {
        return Optional.empty();
      }
      out.write(p);
    }
    return Optional.of(out.toByteArray());
  }

  private static ImageMetadata png(FileSource s) throws IOException {
    Optional<ExifData> exif = Optional.empty();
    Optional<byte[]> xmp = Optional.empty();
    Optional<byte[]> icc = Optional.empty();
    boolean srgb = false;
    boolean damaged = false;
    Map<String, String> text = new HashMap<>();
    long pos = 8;
    long size = s.size();
    while (pos + 8 <= size) {
      byte[] h = s.read(pos, 8);
      long len = ByteBuffer.wrap(h).getInt() & 0xFFFFFFFFL;
      String type = new String(h, 4, 4, StandardCharsets.ISO_8859_1);
      if (type.equals("IEND") || pos + 12 + len > size) {
        break;
      }
      if (len <= MAX_CHUNK && isMetadataChunk(type)) {
        byte[] d = s.read(pos + 8, (int) len);
        switch (type) {
          case "eXIf" -> {
            exif = ExifCodec.parse(d);
            damaged = exif.isEmpty();
          }
          case "iCCP" -> icc = inflateProfile(d);
          case "sRGB" -> srgb = true;
          case "tEXt" -> {
            int nul = indexOf(d, 0);
            if (nul > 0) {
              text.put(latin(d, 0, nul), latin(d, nul + 1, d.length - nul - 1));
            }
          }
          case "iTXt" -> {
            String[] kv = itxt(d);
            if (kv != null && kv[0].equals(PNG_XMP_KEY)) {
              xmp = Optional.of(kv[1].getBytes(StandardCharsets.UTF_8));
            } else if (kv != null) {
              text.put(kv[0], kv[1]);
            }
          }
          default -> {
            // No se usa.
          }
        }
      }
      pos += 12 + len;
    }
    return new ImageMetadata(
        exif, xmp, Optional.empty(), srgb ? Optional.empty() : icc, text, damaged);
  }

  private static boolean isMetadataChunk(String type) {
    return switch (type) {
      case "eXIf", "iCCP", "sRGB", "tEXt", "iTXt" -> true;
      default -> false;
    };
  }

  private static int indexOf(byte[] d, int from) {
    for (int i = from; i < d.length; i++) {
      if (d[i] == 0) {
        return i;
      }
    }
    return -1;
  }

  private static String latin(byte[] d, int off, int n) {
    return new String(d, off, n, StandardCharsets.ISO_8859_1);
  }

  /** {clave, valor} de un iTXt sin comprimir; {@code null} si no lo es. */
  private static String[] itxt(byte[] d) {
    int key = indexOf(d, 0);
    if (key <= 0 || key + 2 >= d.length || d[key + 1] != 0) {
      return null;
    }
    int lang = indexOf(d, key + 3);
    int trans = lang < 0 ? -1 : indexOf(d, lang + 1);
    if (trans < 0) {
      return null;
    }
    return new String[] {
      latin(d, 0, key), new String(d, trans + 1, d.length - trans - 1, StandardCharsets.UTF_8)
    };
  }

  private static Optional<byte[]> inflateProfile(byte[] d) {
    int nul = indexOf(d, 0);
    if (nul <= 0 || nul + 2 > d.length || d[nul + 1] != 0) {
      return Optional.empty();
    }
    Inflater inf = new Inflater();
    try {
      inf.setInput(d, nul + 2, d.length - nul - 2);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[8192];
      while (!inf.finished()) {
        int n = inf.inflate(buf);
        if (n == 0 && (inf.needsInput() || inf.needsDictionary())) {
          return Optional.empty();
        }
        out.write(buf, 0, n);
        if (out.size() > MAX_ICC) {
          return Optional.empty();
        }
      }
      return Optional.of(out.toByteArray());
    } catch (DataFormatException e) {
      return Optional.empty();
    } finally {
      inf.end();
    }
  }

  private static ImageMetadata tiff(FileSource s) {
    Optional<ExifData> exif = ExifCodec.parse(s);
    return new ImageMetadata(
        exif,
        ExifCodec.ifd0Tag(s, TAG_XMP, MAX_XMP),
        ExifCodec.ifd0Tag(s, TAG_IPTC, MAX_CHUNK).filter(Iptc::valid),
        ExifCodec.ifd0Tag(s, TAG_ICC, MAX_ICC),
        Map.of(),
        exif.isEmpty());
  }

  private static ImageMetadata webp(FileSource s) throws IOException {
    Optional<ExifData> exif = Optional.empty();
    Optional<byte[]> xmp = Optional.empty();
    Optional<byte[]> icc = Optional.empty();
    boolean damaged = false;
    long pos = 12;
    long size = s.size();
    while (pos + 8 <= size) {
      byte[] h = s.read(pos, 8);
      String id = new String(h, 0, 4, StandardCharsets.ISO_8859_1);
      long len = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN).getInt(4) & 0xFFFFFFFFL;
      if (pos + 8 + len > size) {
        break;
      }
      if (len <= MAX_CHUNK && (id.equals("EXIF") || id.equals("XMP ") || id.equals("ICCP"))) {
        byte[] d = s.read(pos + 8, (int) len);
        switch (id) {
          case "EXIF" -> {
            exif = ExifCodec.parse(starts(d, EXIF_ID) ? after(d, EXIF_ID.length) : d);
            damaged = exif.isEmpty();
          }
          case "XMP " -> xmp = Optional.of(d);
          default -> icc = Optional.of(d);
        }
      }
      pos += 8 + len + (len & 1);
    }
    return new ImageMetadata(exif, xmp, Optional.empty(), icc, Map.of(), damaged);
  }
}
