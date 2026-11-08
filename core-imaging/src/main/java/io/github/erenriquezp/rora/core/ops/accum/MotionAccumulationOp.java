package io.github.erenriquezp.rora.core.ops.accum;

import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.Cost;
import io.github.erenriquezp.rora.core.op.OpKind;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.EnumParam;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.PixelOps;
import io.github.erenriquezp.rora.core.ops.SimpleBandOp;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Buffer de acumulación horizontal (motion blur / estela) sobre A, R, G y B: suma {@code samples}
 * copias desplazadas {@code i·displacement} píxeles a la derecha (con el borde izquierdo repetido).
 *
 * <ul>
 *   <li>{@code add}: media uniforme, {@code (int)(Σ · (1/N))}.
 *   <li>{@code accum}: pesos {@code (float) 0.85^i}, normalizado por su suma.
 *   <li>{@code mult}: producto de valores normalizados, {@code (int)(Π · 255)}.
 * </ul>
 *
 * Port exacto de {@code BufferAcumulacion_LOAD}. El legacy guarda cuatro {@code float[w·h]} y suma
 * muestra a muestra; aquí cada píxel se acumula en variables locales con <b>la misma secuencia de
 * operaciones {@code float}</b> (las muestras en el mismo orden), así que el resultado es idéntico
 * y no hacen falta los 16 B/px de buffers. Solo lee su propia fila: radio vertical 0.
 */
public final class MotionAccumulationOp extends SimpleBandOp {

  public MotionAccumulationOp() {
    super(
        "accum.motion",
        ParamSchema.of(
            new EnumParam(
                "mode",
                "op.accum.motion.mode",
                List.of("add", "accum", "mult"),
                "add",
                Optional.empty()),
            new IntParam("samples", "op.accum.motion.samples", 2, 64, 25, OptionalInt.empty()),
            new IntParam(
                "displacement", "op.accum.motion.displacement", 1, 32, 8, OptionalInt.empty())),
        new OpMeta(new OpKind.Neighborhood(0, 1), true, false, Cost.EXPENSIVE, 0));
  }

  @Override
  protected BandKernel prepare(Img src, Params params) {
    int n = Math.max(2, params.getInt("samples"));
    int desp = Math.max(1, params.getInt("displacement"));
    return switch (params.getEnum("mode")) {
      case "accum" -> accum(n, desp);
      case "mult" -> mult(n, desp);
      default -> add(n, desp);
    };
  }

  private static BandKernel add(int n, int desp) {
    float invN = 1.0f / n;
    return (in, out, w, h, y0, y1) -> {
      for (int y = y0; y < y1; y++) {
        int row = y * w;
        for (int x = 0; x < w; x++) {
          float sa = 0f;
          float sr = 0f;
          float sg = 0f;
          float sb = 0f;
          for (int i = 0; i < n; i++) {
            int p = in[row + Math.min(w - 1, Math.max(0, x - i * desp))];
            sa += (p >> 24) & 0xFF;
            sr += (p >> 16) & 0xFF;
            sg += (p >> 8) & 0xFF;
            sb += p & 0xFF;
          }
          out[row + x] = pack(sa * invN, sr * invN, sg * invN, sb * invN);
        }
      }
    };
  }

  private static BandKernel accum(int n, int desp) {
    float[] weights = new float[n];
    float sum = 0f;
    for (int i = 0; i < n; i++) {
      weights[i] = (float) Math.pow(0.85, i);
      sum += weights[i];
    }
    float inv = 1.0f / sum;
    return (in, out, w, h, y0, y1) -> {
      for (int y = y0; y < y1; y++) {
        int row = y * w;
        for (int x = 0; x < w; x++) {
          float sa = 0f;
          float sr = 0f;
          float sg = 0f;
          float sb = 0f;
          for (int i = 0; i < n; i++) {
            int p = in[row + Math.min(w - 1, Math.max(0, x - i * desp))];
            float wt = weights[i];
            sa += ((p >> 24) & 0xFF) * wt;
            sr += ((p >> 16) & 0xFF) * wt;
            sg += ((p >> 8) & 0xFF) * wt;
            sb += (p & 0xFF) * wt;
          }
          out[row + x] = pack(sa * inv, sr * inv, sg * inv, sb * inv);
        }
      }
    };
  }

  private static BandKernel mult(int n, int desp) {
    return (in, out, w, h, y0, y1) -> {
      for (int y = y0; y < y1; y++) {
        int row = y * w;
        for (int x = 0; x < w; x++) {
          float pa = 1.0f;
          float pr = 1.0f;
          float pg = 1.0f;
          float pb = 1.0f;
          for (int i = 0; i < n; i++) {
            int p = in[row + Math.min(w - 1, Math.max(0, x - i * desp))];
            pa *= ((p >> 24) & 0xFF) / 255.0f;
            pr *= ((p >> 16) & 0xFF) / 255.0f;
            pg *= ((p >> 8) & 0xFF) / 255.0f;
            pb *= (p & 0xFF) / 255.0f;
          }
          out[row + x] = pack(pa * 255, pr * 255, pg * 255, pb * 255);
        }
      }
    };
  }

  private static int pack(float a, float r, float g, float b) {
    return (PixelOps.clamp((int) a) << 24)
        | (PixelOps.clamp((int) r) << 16)
        | (PixelOps.clamp((int) g) << 8)
        | PixelOps.clamp((int) b);
  }
}
