package io.github.erenriquezp.rora.core.exec;

/**
 * Calcula las filas {@code [y0, y1)} de {@code dst} a partir de {@code src} (ARGB, {@code stride ==
 * width}). Solo lee {@code src} (todas sus filas están disponibles, pero debe respetar el halo
 * declarado) y solo escribe sus propias filas de {@code dst}.
 */
@FunctionalInterface
public interface BandKernel {
  void run(int[] src, int[] dst, int width, int height, int y0, int y1);
}
