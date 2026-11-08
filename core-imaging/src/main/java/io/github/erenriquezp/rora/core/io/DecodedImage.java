package io.github.erenriquezp.rora.core.io;

import io.github.erenriquezp.rora.core.image.Img;
import java.util.List;

/**
 * Resultado de decodificar: píxeles sRGB de 8 bits ya orientados.
 *
 * @param notes avisos para el usuario sobre la conversión
 */
public record DecodedImage(Img image, ImageInfo info, ImageMetadata metadata, List<Note> notes) {

  /** Aviso con su clave en {@code labels_es} y sus argumentos. */
  public record Note(String key, List<String> args) {
    public Note {
      args = List.copyOf(args);
    }

    public static Note of(String key, String... args) {
      return new Note(key, List.of(args));
    }
  }

  public DecodedImage {
    notes = List.copyOf(notes);
  }
}
