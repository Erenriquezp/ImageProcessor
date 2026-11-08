package io.github.erenriquezp.rora.core.io;

import java.util.Optional;
import java.util.Set;

/**
 * Lo que se sabe de un archivo sin decodificar sus píxeles.
 *
 * @param width ancho guardado, antes de aplicar la orientación
 * @param height alto guardado, antes de aplicar la orientación
 * @param orientation valor EXIF 1..8
 * @param profileName nombre del perfil ICC incrustado, ya saneado
 * @param frames páginas o fotogramas
 */
public record ImageInfo(
    FormatId format,
    int width,
    int height,
    int orientation,
    int bitsPerSample,
    Kind kind,
    boolean hasAlpha,
    Optional<String> profileName,
    int frames,
    Set<MetadataKind> present) {

  /** Modelo de color del archivo. */
  public enum Kind {
    RGB,
    GRAY,
    INDEXED,
    CMYK,
    OTHER
  }

  /** Metadatos encontrados en el archivo. */
  public enum MetadataKind {
    EXIF,
    GPS,
    XMP,
    IPTC,
    ICC,
    TEXT
  }

  public ImageInfo {
    present = Set.copyOf(present);
  }

  /** Dimensiones tras aplicar la orientación. */
  public int displayWidth() {
    return Orientation.swapsAxes(orientation) ? height : width;
  }

  /** Dimensiones tras aplicar la orientación. */
  public int displayHeight() {
    return Orientation.swapsAxes(orientation) ? width : height;
  }
}
