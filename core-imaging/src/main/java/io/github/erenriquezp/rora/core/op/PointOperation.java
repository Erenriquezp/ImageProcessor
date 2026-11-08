package io.github.erenriquezp.rora.core.op;

import io.github.erenriquezp.rora.core.exec.BandKernel;

/**
 * Operación puntual que se puede <b>fusionar</b> con otras: su kernel depende solo de los
 * parámetros y del tamaño de la imagen, nunca de su contenido, y cada píxel de salida depende solo
 * del mismo píxel de entrada (y de su posición si {@link OpKind.Point#positional()}).
 *
 * <p>Contrato adicional al de {@link Operation}: {@link #kind(Params)} es un {@link OpKind.Point};
 * el kernel escribe exactamente las filas {@code [y0, y1)} de {@code dst} y solo lee esas mismas
 * filas de {@code src}; y {@code apply(src, p, ctx)} es idéntico a ejecutar ese kernel sobre todas
 * las bandas.
 */
public interface PointOperation extends Operation {

  /** Kernel para estos parámetros e imágenes de {@code width × height}. */
  BandKernel pointKernel(Params params, int width, int height);
}
