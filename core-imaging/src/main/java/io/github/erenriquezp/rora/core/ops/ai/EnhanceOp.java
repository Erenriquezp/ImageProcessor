package io.github.erenriquezp.rora.core.ops.ai;

import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy.Size;
import io.github.erenriquezp.rora.core.internal.ImgAccess;
import io.github.erenriquezp.rora.core.op.BandOperation;
import io.github.erenriquezp.rora.core.op.Cost;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpKind;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.OpResult;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamDef.EnumParam;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.ops.detail.UnsharpMaskOp;
import io.github.erenriquezp.rora.core.ops.geometry.ResizeOp;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Súper-resolución. Esta operación nunca ejecuta el motor: usa el resultado que ya exista (el
 * parámetro {@code artifact}, que rellena la sesión) o, si no hay, una ampliación clásica con las
 * mismas dimensiones, de modo que los pasos posteriores no dependen de si se ejecutó la IA.
 *
 * <p>En preview la salida tiene el tamaño de la entrada: ampliar en cada tick de un slider no
 * tendría sentido.
 */
public final class EnhanceOp implements Operation {

  public static final String ID = "ai.enhance";

  public static final String SCALE_X2 = "X2";
  public static final String SCALE_X4 = "X4";
  private static final String OUTPUT = "OUTPUT";
  private static final String PREVIEW = "PREVIEW";

  private static final long PHASE = 1_000_000L;

  // artifact tiene rango 0..0 a propósito: los rangos son orientativos y este valor no es del
  // usuario. Lo rellena la sesión, y nunca se guarda en su receta.
  private static final ParamSchema SCHEMA =
      ParamSchema.of(
          new EnumParam(
              "scale",
              "op.ai.enhance.scale",
              List.of(SCALE_X2, SCALE_X4),
              SCALE_X2,
              Optional.empty()),
          new DecimalParam(
              "intensity", "op.ai.enhance.intensity", 0, 100, 1, 100, OptionalDouble.empty()),
          new EnumParam(
              "target", "op.ai.enhance.target", List.of(OUTPUT, PREVIEW), OUTPUT, Optional.empty()),
          new IntParam("artifact", "op.ai.enhance.artifact", 0, 0, 0, OptionalInt.empty()));

  private static final OpMeta META = new OpMeta(OpKind.GLOBAL, false, false, Cost.EXPENSIVE, 0);

  private final ResizeOp resize = new ResizeOp();
  private final UnsharpMaskOp sharpen = new UnsharpMaskOp();

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

  private static int factor(Params p) {
    return SCALE_X4.equals(p.getEnum("scale")) ? 4 : 2;
  }

  private static boolean isPreview(Params p) {
    return PREVIEW.equals(p.getEnum("target"));
  }

  @Override
  public Size outputSize(Size in, Params p) {
    if (isPreview(p)) {
      return in;
    }
    int s = factor(p);
    return new Size(in.width() * s, in.height() * s);
  }

  @Override
  public Params forPreview(Params p, double scale, Size fullIn, Size previewIn) {
    return p.toBuilder().set("target", PREVIEW).build();
  }

  @Override
  public OpResult apply(Img src, Params p, OpContext ctx) {
    BandOperation.checkSchema(this, p);
    ctx.token().throwIfCancelled();
    int artifact = p.getInt("artifact");
    double intensity = p.getDouble("intensity");
    int s = factor(p);
    if (isPreview(p)) {
      if (artifact <= 0) {
        return OpResult.of(src);
      }
      Img enhanced =
          ctx.artifacts()
              .proxy(artifact, src.width(), src.height())
              .orElseThrow(() -> missing(artifact));
      return OpResult.of(intensity >= 100 ? enhanced : mix(src, enhanced, intensity, ctx));
    }
    if (artifact <= 0) {
      return OpResult.of(classic(src, s, ctx, ctx.progress()));
    }
    Img enhanced = ctx.artifacts().full(artifact).orElseThrow(() -> missing(artifact));
    if (enhanced.width() != src.width() * s || enhanced.height() != src.height() * s) {
      throw new IllegalStateException(
          "Artefacto de " + enhanced + " para una salida x" + s + " de " + src);
    }
    if (intensity >= 100) {
      return OpResult.of(enhanced);
    }
    return OpResult.of(mix(classic(src, s, ctx, Progress.NONE), enhanced, intensity, ctx));
  }

  private static IllegalStateException missing(int artifact) {
    return new IllegalStateException("Artefacto no disponible: " + artifact);
  }

  /** Lanczos-3 y una nitidez suave: el sustituto cuando la IA no se ha ejecutado. */
  private Img classic(Img src, int s, OpContext ctx, Progress progress) {
    int longSide = Math.max(src.width(), src.height()) * s;
    Params rp = Params.builder(resize.schema()).set("longSide", longSide).build();
    Img big = resize.apply(src, rp, phase(ctx, progress, 0)).image();
    Params sp =
        Params.builder(sharpen.schema())
            .set("amount", 50.0)
            .set("radius", s == 4 ? 2.0 : 1.0)
            .set("threshold", 2)
            .build();
    return sharpen.apply(big, sp, phase(ctx, progress, 1)).image();
  }

  private static OpContext phase(OpContext ctx, Progress outer, int phase) {
    Progress p =
        outer == Progress.NONE
            ? Progress.NONE
            : (done, total) ->
                outer.update(phase * PHASE + (total > 0 ? done * PHASE / total : 0), 2 * PHASE);
    return new OpContext(ctx.executor(), ctx.token(), p, ctx.artifacts());
  }

  /** Mezcla en sRGB de 8 bits; el alfa es el del resultado de la IA. */
  private static Img mix(Img base, Img enhanced, double intensity, OpContext ctx) {
    int w = base.width();
    int h = base.height();
    int[] a = ImgAccess.pixels(base);
    int[] b = ImgAccess.pixels(enhanced);
    int[] out = new int[a.length];
    int t = (int) Math.round(Math.max(0, intensity) * 256 / 100);
    ctx.executor()
        .reduceBands(
            h,
            ctx.token(),
            ctx.progress(),
            () -> null,
            (unused, y0, y1) -> {
              for (int i = y0 * w; i < y1 * w; i++) {
                int pa = a[i];
                int pb = b[i];
                out[i] =
                    (pb & 0xFF000000)
                        | blend(pa >> 16, pb >> 16, t) << 16
                        | blend(pa >> 8, pb >> 8, t) << 8
                        | blend(pa, pb, t);
              }
            },
            (x, y) -> null);
    return Img.adopt(w, h, out);
  }

  private static int blend(int a, int b, int t) {
    int ca = a & 0xFF;
    int cb = b & 0xFF;
    return ca + (((cb - ca) * t + 128) >> 8);
  }
}
