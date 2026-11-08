package io.github.erenriquezp.rora.core.io;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Metadatos ya interpretados y validados. Lo que no se reconoce no llega a este modelo, así que
 * nunca se puede copiar a la salida.
 *
 * @param icc perfil del archivo; se usa para convertir y nunca se escribe en la salida
 * @param text texto de PNG por clave
 * @param damaged había un bloque EXIF, pero se descartó por anómalo
 */
public record ImageMetadata(
    Optional<ExifData> exif,
    Optional<byte[]> xmp,
    Optional<byte[]> iptc,
    Optional<byte[]> icc,
    Map<String, String> text,
    boolean damaged) {

  public static final ImageMetadata EMPTY =
      new ImageMetadata(
          Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Map.of(), false);

  public ImageMetadata {
    text = Map.copyOf(text);
  }

  /** Orientación EXIF, o 1 si no hay. */
  public int orientation() {
    return exif.map(ExifData::orientation).orElse(1);
  }

  /** Etiqueta EXIF ya normalizada a little-endian. */
  public record ExifTag(int id, int type, int count, byte[] value, boolean sensitive) {}

  /**
   * Etiquetas EXIF reconocidas, ordenadas por id. Nunca incluye la miniatura.
   *
   * @param orientation valor 1..8
   */
  public record ExifData(
      int orientation, List<ExifTag> ifd0, List<ExifTag> exif, List<ExifTag> gps) {
    public ExifData {
      ifd0 = List.copyOf(ifd0);
      exif = List.copyOf(exif);
      gps = List.copyOf(gps);
    }

    public boolean isEmpty() {
      return ifd0.isEmpty() && exif.isEmpty() && gps.isEmpty();
    }
  }
}
