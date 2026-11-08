package io.github.erenriquezp.rora.core.io;

import java.util.Objects;

/**
 * Ajustes de exportación, inmutables y validados.
 *
 * @param quality calidad JPEG, 1..100
 * @param deflateLevel compresión PNG, 0..9
 * @param background color 0xRRGGBB sobre el que se aplana la transparencia
 * @param maxLongSide límite del lado mayor en píxeles; 0 = sin límite
 * @param maxBytes límite de peso en bytes (solo JPEG); 0 = sin límite
 */
public record ExportSettings(
    FormatId format,
    int quality,
    boolean progressive,
    Subsampling subsampling,
    boolean optimizeHuffman,
    int deflateLevel,
    TiffCompression tiff,
    ProfilePolicy profile,
    MetadataPolicy metadata,
    int background,
    int maxLongSide,
    long maxBytes) {

  /** Muestreo de croma en JPEG. */
  public enum Subsampling {
    S420,
    S444
  }

  /** Compresión sin pérdida de TIFF. */
  public enum TiffCompression {
    NONE,
    LZW,
    DEFLATE,
    PACKBITS
  }

  /** Etiquetado de color de la salida, que siempre es sRGB. */
  public enum ProfilePolicy {
    EMBED_SRGB,
    UNTAGGED
  }

  /** Qué metadatos del original llegan a la salida. */
  public enum MetadataPolicy {
    KEEP,
    REMOVE_LOCATION,
    REMOVE_ALL
  }

  public ExportSettings {
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(subsampling, "subsampling");
    Objects.requireNonNull(tiff, "tiff");
    Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(metadata, "metadata");
    if (!format.writable()) {
      throw new IllegalArgumentException("Formato sin escritura: " + format);
    }
    check(quality >= 1 && quality <= 100, "quality=" + quality);
    check(deflateLevel >= 0 && deflateLevel <= 9, "deflateLevel=" + deflateLevel);
    check((background & ~0xFFFFFF) == 0, "background=" + background);
    check(maxLongSide >= 0 && maxLongSide <= 16384, "maxLongSide=" + maxLongSide);
    check(maxBytes >= 0, "maxBytes=" + maxBytes);
    check(maxBytes == 0 || format == FormatId.JPEG, "maxBytes solo se aplica a JPEG");
  }

  private static void check(boolean ok, String detail) {
    if (!ok) {
      throw new IllegalArgumentException(detail);
    }
  }

  /** Valores por defecto: sin límites, fondo blanco y sin ubicación. */
  public static ExportSettings defaults(FormatId format) {
    return new ExportSettings(
        format,
        90,
        false,
        Subsampling.S420,
        true,
        6,
        TiffCompression.DEFLATE,
        ProfilePolicy.EMBED_SRGB,
        MetadataPolicy.REMOVE_LOCATION,
        0xFFFFFF,
        0,
        0);
  }

  public ExportSettings withQuality(int value) {
    return new ExportSettings(
        format,
        value,
        progressive,
        subsampling,
        optimizeHuffman,
        deflateLevel,
        tiff,
        profile,
        metadata,
        background,
        maxLongSide,
        maxBytes);
  }

  public ExportSettings withJpeg(boolean prog, Subsampling sub, boolean huffman) {
    return new ExportSettings(
        format,
        quality,
        prog,
        sub,
        huffman,
        deflateLevel,
        tiff,
        profile,
        metadata,
        background,
        maxLongSide,
        maxBytes);
  }

  public ExportSettings withDeflateLevel(int value) {
    return new ExportSettings(
        format,
        quality,
        progressive,
        subsampling,
        optimizeHuffman,
        value,
        tiff,
        profile,
        metadata,
        background,
        maxLongSide,
        maxBytes);
  }

  public ExportSettings withTiff(TiffCompression value) {
    return new ExportSettings(
        format,
        quality,
        progressive,
        subsampling,
        optimizeHuffman,
        deflateLevel,
        value,
        profile,
        metadata,
        background,
        maxLongSide,
        maxBytes);
  }

  public ExportSettings withProfile(ProfilePolicy value) {
    return new ExportSettings(
        format,
        quality,
        progressive,
        subsampling,
        optimizeHuffman,
        deflateLevel,
        tiff,
        value,
        metadata,
        background,
        maxLongSide,
        maxBytes);
  }

  public ExportSettings withMetadata(MetadataPolicy value) {
    return new ExportSettings(
        format,
        quality,
        progressive,
        subsampling,
        optimizeHuffman,
        deflateLevel,
        tiff,
        profile,
        value,
        background,
        maxLongSide,
        maxBytes);
  }

  public ExportSettings withBackground(int value) {
    return new ExportSettings(
        format,
        quality,
        progressive,
        subsampling,
        optimizeHuffman,
        deflateLevel,
        tiff,
        profile,
        metadata,
        value,
        maxLongSide,
        maxBytes);
  }

  public ExportSettings withMaxLongSide(int value) {
    return new ExportSettings(
        format,
        quality,
        progressive,
        subsampling,
        optimizeHuffman,
        deflateLevel,
        tiff,
        profile,
        metadata,
        background,
        value,
        maxBytes);
  }

  public ExportSettings withMaxBytes(long value) {
    return new ExportSettings(
        format,
        quality,
        progressive,
        subsampling,
        optimizeHuffman,
        deflateLevel,
        tiff,
        profile,
        metadata,
        background,
        maxLongSide,
        value);
  }
}
