package io.github.erenriquezp.rora.core.ops.fragment;

import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.ColorParam;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamDef.EnumParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.PixelOps;
import io.github.erenriquezp.rora.core.ops.SimplePointOp;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Mezcla cada píxel (src) con un color plano (dst) según el modo; alpha de src intacto. Port de
 * {@code FragmentOpsFilters.fragmentBlend}. {@code src-over} con {@code alpha = 1} es la identidad.
 */
public final class BlendColorOp extends SimplePointOp {

  public BlendColorOp() {
    super(
        "fragment.blend-color",
        ParamSchema.of(
            new EnumParam(
                "mode",
                "op.fragment.blend-color.mode",
                List.of("src-over", "multiply", "add", "screen", "subtract"),
                "src-over",
                Optional.of("src-over")),
            new ColorParam(
                "color",
                "op.fragment.blend-color.color",
                0xFF808080,
                false,
                OptionalInt.of(0xFF000000)),
            new DecimalParam(
                "alpha", "op.fragment.blend-color.alpha", 0, 1, 0.01, 0.5, OptionalDouble.of(1))),
        OpMeta.point());
  }

  @Override
  public BandKernel pointKernel(Params params, int width, int height) {
    String mode = params.getEnum("mode");
    int color = params.getColor("color");
    int dstR = (color >> 16) & 0xFF;
    int dstG = (color >> 8) & 0xFF;
    int dstB = color & 0xFF;
    float a = PixelOps.clamp01(params.getFloat("alpha"));
    return switch (mode) {
      case "multiply" ->
          (in, out, w, h, y0, y1) -> {
            for (int i = y0 * w, end = y1 * w; i < end; i++) {
              int p = in[i];
              int r = (((p >> 16) & 0xFF) * dstR) / 255;
              int g = (((p >> 8) & 0xFF) * dstG) / 255;
              int b = ((p & 0xFF) * dstB) / 255;
              out[i] = (p & 0xFF000000) | (r << 16) | (g << 8) | b;
            }
          };
      case "add" ->
          (in, out, w, h, y0, y1) -> {
            for (int i = y0 * w, end = y1 * w; i < end; i++) {
              int p = in[i];
              int r = PixelOps.clamp(((p >> 16) & 0xFF) + dstR);
              int g = PixelOps.clamp(((p >> 8) & 0xFF) + dstG);
              int b = PixelOps.clamp((p & 0xFF) + dstB);
              out[i] = (p & 0xFF000000) | (r << 16) | (g << 8) | b;
            }
          };
      case "screen" ->
          (in, out, w, h, y0, y1) -> {
            for (int i = y0 * w, end = y1 * w; i < end; i++) {
              int p = in[i];
              int r = 255 - ((255 - ((p >> 16) & 0xFF)) * (255 - dstR)) / 255;
              int g = 255 - ((255 - ((p >> 8) & 0xFF)) * (255 - dstG)) / 255;
              int b = 255 - ((255 - (p & 0xFF)) * (255 - dstB)) / 255;
              out[i] = (p & 0xFF000000) | (r << 16) | (g << 8) | b;
            }
          };
      case "subtract" ->
          (in, out, w, h, y0, y1) -> {
            for (int i = y0 * w, end = y1 * w; i < end; i++) {
              int p = in[i];
              int r = PixelOps.clamp(((p >> 16) & 0xFF) - dstR);
              int g = PixelOps.clamp(((p >> 8) & 0xFF) - dstG);
              int b = PixelOps.clamp((p & 0xFF) - dstB);
              out[i] = (p & 0xFF000000) | (r << 16) | (g << 8) | b;
            }
          };
      default -> // "src-over"
          (in, out, w, h, y0, y1) -> {
            for (int i = y0 * w, end = y1 * w; i < end; i++) {
              int p = in[i];
              int r = PixelOps.lerp(dstR, (p >> 16) & 0xFF, a);
              int g = PixelOps.lerp(dstG, (p >> 8) & 0xFF, a);
              int b = PixelOps.lerp(dstB, p & 0xFF, a);
              out[i] = (p & 0xFF000000) | (r << 16) | (g << 8) | b;
            }
          };
    };
  }
}
