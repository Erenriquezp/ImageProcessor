package io.github.erenriquezp.rora.core.op;

import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.image.Img;

/**
 * {@link BandOperation} puntual: {@code apply} usa el mismo kernel que la fusión ({@link
 * #pointKernel}), así que el camino fusionado y el no fusionado ejecutan el mismo código.
 */
public abstract class PointBandOperation extends BandOperation implements PointOperation {

  @Override
  protected final BandKernel prepare(Img src, Params params) {
    return pointKernel(params, src.width(), src.height());
  }
}
