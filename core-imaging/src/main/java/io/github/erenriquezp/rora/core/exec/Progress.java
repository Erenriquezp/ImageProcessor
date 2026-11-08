package io.github.erenriquezp.rora.core.exec;

/**
 * Receptor de progreso. Se invoca desde hilos de trabajo, nunca de forma concurrente consigo mismo,
 * con {@code done} creciente de 1 a {@code total} (unidades = bandas).
 */
@FunctionalInterface
public interface Progress {

  /** No hace nada. */
  Progress NONE = (done, total) -> {};

  void update(long done, long total);
}
