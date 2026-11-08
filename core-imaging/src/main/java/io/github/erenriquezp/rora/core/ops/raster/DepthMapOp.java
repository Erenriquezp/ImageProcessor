package io.github.erenriquezp.rora.core.ops.raster;

import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.OpKind;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.EnumParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.PixelOps;
import io.github.erenriquezp.rora.core.ops.SimpleBandOp;
import java.util.List;
import java.util.Optional;

/**
 * Pseudo-profundidad en gris (0 = cerca, 255 = lejos) según la fuente: promedio de luminancia,
 * distancia al centro, posición horizontal o diagonal. Alpha intacto. Port de {@code
 * RasterPipelineFilters.depthMap} y {@code depthAt}.
 */
public final class DepthMapOp extends SimpleBandOp {

  public DepthMapOp() {
    super(
        "raster.depth-map",
        ParamSchema.of(
            new EnumParam(
                "source",
                "op.raster.depth-map.source",
                List.of("luminance", "radial", "horizontal", "diagonal"),
                "luminance",
                Optional.empty())),
        OpMeta.point().withKind(OpKind.POSITIONAL));
  }

  @Override
  protected BandKernel prepare(Img src, Params params) {
    return switch (params.getEnum("source")) {
      case "radial" ->
          (in, out, w, h, y0, y1) -> {
            double cx = (w - 1) / 2.0;
            double cy = (h - 1) / 2.0;
            double maxD = Math.hypot(cx, cy);
            for (int y = y0; y < y1; y++) {
              for (int x = 0, i = y * w; x < w; x++, i++) {
                double d = Math.hypot(x - cx, y - cy);
                write(out, i, in[i], PixelOps.clamp((int) Math.round(d / maxD * 255)));
              }
            }
          };
      case "horizontal" ->
          (in, out, w, h, y0, y1) -> {
            for (int y = y0; y < y1; y++) {
              for (int x = 0, i = y * w; x < w; x++, i++) {
                int d = PixelOps.clamp((int) Math.round(x / (double) Math.max(1, w - 1) * 255));
                write(out, i, in[i], d);
              }
            }
          };
      case "diagonal" ->
          (in, out, w, h, y0, y1) -> {
            for (int y = y0; y < y1; y++) {
              for (int x = 0, i = y * w; x < w; x++, i++) {
                double t = (x + y) / (double) Math.max(1, w + h - 2);
                write(out, i, in[i], PixelOps.clamp((int) Math.round(t * 255)));
              }
            }
          };
      default -> // "luminance"
          (in, out, w, h, y0, y1) -> {
            for (int i = y0 * w, end = y1 * w; i < end; i++) {
              int p = in[i];
              write(out, i, p, (((p >> 16) & 0xFF) + ((p >> 8) & 0xFF) + (p & 0xFF)) / 3);
            }
          };
    };
  }

  private static void write(int[] out, int i, int p, int d) {
    out[i] = (p & 0xFF000000) | (d << 16) | (d << 8) | d;
  }
}
