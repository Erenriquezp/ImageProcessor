package io.github.erenriquezp.rora.core.ops.color;

import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.EnumParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.PixelOps;
import io.github.erenriquezp.rora.core.ops.SimpleBandOp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Matriz de color 3×3 <b>sin offsets</b> (defecto conocido n.º 7): {@code c' = clamp((int)(m0·r +
 * m1·g + m2·b))} en {@code float}. Port de {@code ColorFilters.sepia/coolTone/warmTone/polaroid/
 * kodachrome}; los coeficientes son los mismos literales {@code float}.
 */
public final class MatrixPresetOp extends SimpleBandOp {

  private static final Map<String, float[]> PRESETS =
      Map.of(
          "sepia",
          new float[] {0.393f, 0.769f, 0.189f, 0.349f, 0.686f, 0.168f, 0.272f, 0.534f, 0.131f},
          "cool",
          new float[] {0.80f, 0.10f, 0.10f, 0.05f, 0.92f, 0.03f, 0.00f, 0.15f, 1.10f},
          "warm",
          new float[] {1.10f, 0.10f, 0.00f, 0.00f, 1.00f, 0.05f, 0.00f, 0.00f, 0.80f},
          "polaroid",
          new float[] {1.438f, -0.062f, -0.062f, -0.122f, 1.378f, -0.122f, -0.016f, 0.016f, 0.984f},
          "kodachrome",
          new float[] {
            1.128f, -0.397f, -0.040f, -0.164f, 1.084f, -0.055f, -0.168f, -0.560f, 1.601f
          });

  public MatrixPresetOp() {
    super(
        "color.matrix-preset",
        ParamSchema.of(
            new EnumParam(
                "preset",
                "op.color.matrix-preset.preset",
                List.of("sepia", "cool", "warm", "polaroid", "kodachrome"),
                "sepia",
                Optional.empty())),
        OpMeta.point());
  }

  @Override
  protected BandKernel prepare(Img src, Params params) {
    float[] m = PRESETS.get(params.getEnum("preset"));
    float m00 = m[0];
    float m01 = m[1];
    float m02 = m[2];
    float m10 = m[3];
    float m11 = m[4];
    float m12 = m[5];
    float m20 = m[6];
    float m21 = m[7];
    float m22 = m[8];
    return (in, out, w, h, y0, y1) -> {
      for (int i = y0 * w, end = y1 * w; i < end; i++) {
        int p = in[i];
        int r = (p >> 16) & 0xFF;
        int g = (p >> 8) & 0xFF;
        int b = p & 0xFF;
        int nr = PixelOps.clamp((int) (m00 * r + m01 * g + m02 * b));
        int ng = PixelOps.clamp((int) (m10 * r + m11 * g + m12 * b));
        int nb = PixelOps.clamp((int) (m20 * r + m21 * g + m22 * b));
        out[i] = (p & 0xFF000000) | (nr << 16) | (ng << 8) | nb;
      }
    };
  }
}
