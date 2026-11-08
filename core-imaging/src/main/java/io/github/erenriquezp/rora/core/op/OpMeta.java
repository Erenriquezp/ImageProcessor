package io.github.erenriquezp.rora.core.op;

import java.util.Objects;

/**
 * Metadatos de una operación.
 *
 * @param kind patrón de acceso por defecto (ver {@link Operation#kind(Params)})
 * @param preservesDimensions la salida tiene el tamaño de la entrada
 * @param preservesAlpha el alpha de cada píxel de salida es el de la entrada
 * @param cost coste aproximado
 * @param identityTolerance diferencia máxima por canal admitida con {@link ParamSchema#neutral()}
 */
public record OpMeta(
    OpKind kind,
    boolean preservesDimensions,
    boolean preservesAlpha,
    Cost cost,
    int identityTolerance) {

  public OpMeta {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(cost, "cost");
    if (identityTolerance < 0 || identityTolerance > 255) {
      throw new IllegalArgumentException("identityTolerance=" + identityTolerance);
    }
  }

  /** Operación puntual barata que conserva tamaño y alpha. */
  public static OpMeta point() {
    return new OpMeta(OpKind.POINT, true, true, Cost.CHEAP, 0);
  }

  public OpMeta withKind(OpKind k) {
    return new OpMeta(k, preservesDimensions, preservesAlpha, cost, identityTolerance);
  }

  public OpMeta withAlphaChanged() {
    return new OpMeta(kind, preservesDimensions, false, cost, identityTolerance);
  }

  public OpMeta withCost(Cost c) {
    return new OpMeta(kind, preservesDimensions, preservesAlpha, c, identityTolerance);
  }

  public OpMeta withIdentityTolerance(int t) {
    return new OpMeta(kind, preservesDimensions, preservesAlpha, cost, t);
  }
}
