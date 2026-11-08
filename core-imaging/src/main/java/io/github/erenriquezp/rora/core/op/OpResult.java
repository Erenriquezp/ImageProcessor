package io.github.erenriquezp.rora.core.op;

import io.github.erenriquezp.rora.core.image.Img;
import java.util.Objects;
import java.util.Optional;

/**
 * Resultado de una operación: la imagen y, opcionalmente, datos auxiliares estructurados (p. ej.
 * {@link ExposureStats}). {@code image} puede ser <b>la misma instancia</b> que la entrada cuando
 * la operación no la cambia (análisis, o parámetros que la dejan igual).
 */
public record OpResult(Img image, Object aux) {

  public OpResult {
    Objects.requireNonNull(image, "image");
  }

  public static OpResult of(Img image) {
    return new OpResult(image, null);
  }

  public static OpResult of(Img image, Object aux) {
    return new OpResult(image, Objects.requireNonNull(aux, "aux"));
  }

  /** Datos auxiliares del tipo pedido, si los hay. */
  public <T> Optional<T> aux(Class<T> type) {
    return type.isInstance(aux) ? Optional.of(type.cast(aux)) : Optional.empty();
  }
}
