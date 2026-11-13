package io.github.erenriquezp.rora.wallpapers.preset;

import java.util.Objects;

/**
 * Algo que se ignoró al leer presets: un archivo entero o una entrada.
 *
 * @param source nombre del archivo (sin ruta) o {@code integrados}
 * @param id id de la entrada, o vacío si afecta al archivo
 */
public record PresetProblem(String source, String id, String detail) {

  public PresetProblem {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(detail, "detail");
  }

  @Override
  public String toString() {
    return source + (id.isEmpty() ? "" : " [" + id + "]") + ": " + detail;
  }
}
