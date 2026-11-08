package io.github.erenriquezp.rora.core.ops.raster;

import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.BandOperation;
import io.github.erenriquezp.rora.core.op.Cost;
import io.github.erenriquezp.rora.core.op.OpKind;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import java.util.OptionalInt;

/**
 * Pixelado por bloques {@code n×n} alineados a la esquina superior izquierda: cada bloque toma la
 * media entera (truncada) de sus canales A, R, G y B, <b>incluido el alpha</b>. Port exacto de
 * {@code RasterPipelineFilters.bitmapPixelate}.
 *
 * <p>Las bandas se alinean a la rejilla de bloques ({@code rowAlign = n}), así que cada bloque cae
 * entero dentro de una banda.
 */
public final class PixelateOp extends BandOperation {

  public static final String ID = "raster.pixelate";

  private static final ParamSchema SCHEMA =
      ParamSchema.of(
          new IntParam("block", "op.raster.pixelate.block", 2, 64, 8, OptionalInt.empty()));

  private static final OpMeta META =
      new OpMeta(new OpKind.Neighborhood(7, 8), true, false, Cost.CHEAP, 0);

  @Override
  public String id() {
    return ID;
  }

  @Override
  public ParamSchema schema() {
    return SCHEMA;
  }

  @Override
  public OpMeta meta() {
    return META;
  }

  @Override
  public OpKind kind(Params params) {
    int n = blockSize(params);
    return new OpKind.Neighborhood(n - 1, n);
  }

  private static int blockSize(Params params) {
    return Math.max(1, params.getInt("block"));
  }

  @Override
  protected BandKernel prepare(Img src, Params params) {
    int n = blockSize(params);
    return (in, out, w, h, y0, y1) -> {
      for (int by = y0; by < y1; by += n) {
        int yMax = Math.min(by + n, h);
        for (int bx = 0; bx < w; bx += n) {
          long sumR = 0;
          long sumG = 0;
          long sumB = 0;
          long sumA = 0;
          int count = 0;
          int xMax = Math.min(bx + n, w);
          for (int y = by; y < yMax; y++) {
            for (int x = bx, i = y * w + bx; x < xMax; x++, i++) {
              int p = in[i];
              sumA += (p >> 24) & 0xFF;
              sumR += (p >> 16) & 0xFF;
              sumG += (p >> 8) & 0xFF;
              sumB += p & 0xFF;
              count++;
            }
          }
          int a = (int) (sumA / count);
          int r = (int) (sumR / count);
          int g = (int) (sumG / count);
          int b = (int) (sumB / count);
          int rgb = (a << 24) | (r << 16) | (g << 8) | b;
          for (int y = by; y < yMax; y++) {
            for (int x = bx, i = y * w + bx; x < xMax; x++, i++) {
              out[i] = rgb;
            }
          }
        }
      }
    };
  }
}
