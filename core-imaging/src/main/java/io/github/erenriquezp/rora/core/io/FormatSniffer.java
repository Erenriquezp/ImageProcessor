package io.github.erenriquezp.rora.core.io;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** Detecta el formato por el contenido; la extensión solo se usa para reconocer RAW. */
public final class FormatSniffer {

  /** Bytes de cabecera que necesita {@link #detect}. */
  public static final int HEADER_BYTES = 32;

  private static final Set<String> RAW_EXTENSIONS =
      Set.of(
          "dng", "cr2", "cr3", "crw", "nef", "nrw", "arw", "srf", "sr2", "orf", "rw2", "raf", "pef",
          "srw", "erf", "kdc", "dcr", "mrw", "3fr", "iiq", "x3f");

  private FormatSniffer() {}

  /**
   * @param header primeros bytes del archivo (hasta {@link #HEADER_BYTES})
   * @param length bytes válidos de {@code header}
   * @param fileName nombre del archivo
   */
  public static FormatId detect(byte[] header, int length, String fileName)
      throws ImagingException.UnsupportedFormat {
    if (RAW_EXTENSIONS.contains(extension(fileName))) {
      throw new ImagingException.UnsupportedFormat("io.error.raw", "extension RAW: " + fileName);
    }
    if (startsWith(header, length, 0xFF, 0xD8, 0xFF)) {
      return FormatId.JPEG;
    }
    if (startsWith(header, length, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
      return FormatId.PNG;
    }
    if (ascii(header, length, 0, 6, "GIF87a") || ascii(header, length, 0, 6, "GIF89a")) {
      return FormatId.GIF;
    }
    if (ascii(header, length, 0, 2, "BM")) {
      return FormatId.BMP;
    }
    if (ascii(header, length, 0, 4, "RIFF") && ascii(header, length, 8, 4, "WEBP")) {
      return FormatId.WEBP;
    }
    if (ascii(header, length, 0, 15, "FUJIFILMCCD-RAW")) {
      throw new ImagingException.UnsupportedFormat("io.error.raw", "firma RAF");
    }
    if (ascii(header, length, 4, 4, "ftyp")) {
      boolean cr3 = ascii(header, length, 8, 3, "crx");
      throw new ImagingException.UnsupportedFormat(
          cr3 ? "io.error.raw" : "io.error.heif", "contenedor ISO BMFF");
    }
    if (startsWith(header, length, 'I', 'I', 0x2A, 0)
        || startsWith(header, length, 'M', 'M', 0, 0x2A)) {
      if (ascii(header, length, 8, 2, "CR")) {
        throw new ImagingException.UnsupportedFormat("io.error.raw", "firma CR2");
      }
      return FormatId.TIFF;
    }
    if (startsWith(header, length, 'I', 'I', 0x2B, 0)
        || startsWith(header, length, 'M', 'M', 0, 0x2B)) {
      throw new ImagingException.UnsupportedFormat("io.error.bigtiff", "BigTIFF");
    }
    throw new ImagingException.UnsupportedFormat("io.error.unsupported", "firma desconocida");
  }

  private static String extension(String name) {
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private static boolean startsWith(byte[] h, int length, int... sig) {
    if (length < sig.length) {
      return false;
    }
    for (int i = 0; i < sig.length; i++) {
      if ((h[i] & 0xFF) != sig[i]) {
        return false;
      }
    }
    return true;
  }

  private static boolean ascii(byte[] h, int length, int off, int n, String expected) {
    return length >= off + n && new String(h, off, n, StandardCharsets.ISO_8859_1).equals(expected);
  }
}
