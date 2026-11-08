package io.github.erenriquezp.rora.core.op;

/** Coste aproximado por píxel; orienta caché y paralelismo en fases posteriores. */
public enum Cost {
  /** Unas pocas operaciones aritméticas por píxel. */
  CHEAP,
  /** Decenas de operaciones o varias pasadas. */
  MODERATE,
  /** Vecindades grandes o cálculo intensivo. */
  EXPENSIVE
}
