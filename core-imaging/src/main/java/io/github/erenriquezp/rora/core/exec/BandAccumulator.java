package io.github.erenriquezp.rora.core.exec;

/** Acumula en {@code acc} (propio de la banda) la información de las filas {@code [y0, y1)}. */
@FunctionalInterface
public interface BandAccumulator<A> {
  void accept(A acc, int y0, int y1);
}
