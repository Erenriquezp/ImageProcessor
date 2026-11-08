package io.github.erenriquezp.rora.core.ops.geometry;

import io.github.erenriquezp.rora.core.image.ProxyPolicy.Size;
import io.github.erenriquezp.rora.core.op.Cost;
import io.github.erenriquezp.rora.core.op.OpKind;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import java.util.OptionalDouble;

/**
 * Recorte con un rectángulo normalizado a {@code [0, 1]}, independiente de la resolución (vale
 * igual sobre el proxy y sobre la imagen completa). Valores invertidos se ordenan; el resultado
 * tiene siempre al menos 1×1 píxel.
 */
public final class CropOp extends GeometryOp {

  public static final String ID = "transform.crop";

  private static final ParamSchema SCHEMA =
      ParamSchema.of(edge("left", 0), edge("top", 0), edge("right", 1), edge("bottom", 1));

  private static final OpMeta META = new OpMeta(OpKind.GLOBAL, false, false, Cost.CHEAP, 0);

  public CropOp() {
    super(ID, SCHEMA, META);
  }

  private static DecimalParam edge(String key, double value) {
    return new DecimalParam(
        key, "op.transform.crop." + key, 0, 1, 0.0001, value, OptionalDouble.of(value));
  }

  private static int start(double a, double b, int size) {
    return Math.max(0, Math.min(size - 1, (int) Math.floor(clamp(Math.min(a, b)) * size)));
  }

  private static int end(double a, double b, int size, int start) {
    int e = (int) Math.ceil(clamp(Math.max(a, b)) * size);
    return Math.max(start + 1, Math.min(size, e));
  }

  private static double clamp(double v) {
    return Math.max(0, Math.min(1, v));
  }

  @Override
  public Size outputSize(Size in, Params p) {
    int x0 = start(p.getDouble("left"), p.getDouble("right"), in.width());
    int x1 = end(p.getDouble("left"), p.getDouble("right"), in.width(), x0);
    int y0 = start(p.getDouble("top"), p.getDouble("bottom"), in.height());
    int y1 = end(p.getDouble("top"), p.getDouble("bottom"), in.height(), y0);
    return new Size(x1 - x0, y1 - y0);
  }

  @Override
  RowFiller prepare(Params p, Size in, Size out) {
    int x0 = start(p.getDouble("left"), p.getDouble("right"), in.width());
    int top = start(p.getDouble("top"), p.getDouble("bottom"), in.height());
    return (src, inW, inH, dst, outW, outH, y0, y1) -> {
      for (int y = y0; y < y1; y++) {
        System.arraycopy(src, (y + top) * inW + x0, dst, y * outW, outW);
      }
    };
  }
}
