package io.github.erenriquezp.rora.ai.api;

import java.util.Objects;

/**
 * Datos de un modelo de superresolución.
 *
 * @param scale ampliación nativa del modelo
 * @param tile lado del tile con el que se ejecuta
 * @param overlap solape entre tiles, en píxeles de entrada
 * @param sha256 hash hexadecimal de los bytes del modelo
 */
public record ModelInfo(
    String id,
    int scale,
    int tile,
    int overlap,
    String sha256,
    long bytes,
    String license,
    String attribution,
    String version) {

  public ModelInfo {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(sha256, "sha256");
    if (scale < 1 || tile < 1 || overlap < 0 || overlap >= tile) {
      throw new IllegalArgumentException(
          "Geometría no válida: " + scale + "/" + tile + "/" + overlap);
    }
  }
}
