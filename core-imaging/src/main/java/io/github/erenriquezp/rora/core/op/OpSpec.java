package io.github.erenriquezp.rora.core.op;

import java.util.Objects;

/**
 * Un paso de una receta: id de operación, parámetros y si está activo. Inmutable, con igualdad
 * estructural (la de {@link Params}, por valor); la usan el historial y la caché de resultados.
 *
 * @param opId id estable de la operación en el {@link OpRegistry}
 * @param params valores, construidos con el esquema de esa operación
 * @param enabled un paso inactivo se conserva en la receta pero no se aplica
 */
public record OpSpec(String opId, Params params, boolean enabled) {

  public OpSpec {
    Objects.requireNonNull(opId, "opId");
    Objects.requireNonNull(params, "params");
  }

  /** Paso activo de {@code op}; {@code params} debe tener el esquema de {@code op} (por valor). */
  public static OpSpec of(Operation op, Params params) {
    if (!op.schema().equals(params.schema())) {
      throw new IllegalArgumentException("Los parámetros no pertenecen al esquema de " + op.id());
    }
    return new OpSpec(op.id(), params, true);
  }

  /** Paso activo de {@code op} con sus valores por defecto. */
  public static OpSpec defaults(Operation op) {
    return of(op, op.schema().defaults());
  }

  public OpSpec withParams(Params p) {
    return new OpSpec(opId, p, enabled);
  }

  public OpSpec withEnabled(boolean e) {
    return e == enabled ? this : new OpSpec(opId, params, e);
  }

  /** Forma canónica estable: {@code point.saturation{factor=1.5}}, con {@code !} si inactivo. */
  public String canonical() {
    return (enabled ? "" : "!") + opId + params.canonical();
  }
}
