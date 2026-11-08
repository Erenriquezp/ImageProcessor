package io.github.erenriquezp.rora.core.ops.geometry;

import io.github.erenriquezp.rora.core.image.ProxyPolicy.Size;
import io.github.erenriquezp.rora.core.op.Cost;
import io.github.erenriquezp.rora.core.op.OpKind;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import java.util.OptionalInt;

/** Rotación exacta en cuartos de vuelta, en sentido horario. */
public final class Rotate90Op extends GeometryOp {

  public static final String ID = "transform.rotate90";

  private static final ParamSchema SCHEMA =
      ParamSchema.of(
          new IntParam("turns", "op.transform.rotate90.turns", 0, 3, 1, OptionalInt.of(0)));

  private static final OpMeta META = new OpMeta(OpKind.GLOBAL, false, false, Cost.CHEAP, 0);

  public Rotate90Op() {
    super(ID, SCHEMA, META);
  }

  private static int turns(Params p) {
    return Math.floorMod(p.getInt("turns"), 4);
  }

  @Override
  public Size outputSize(Size in, Params p) {
    return (turns(p) & 1) == 1 ? new Size(in.height(), in.width()) : in;
  }

  @Override
  RowFiller prepare(Params p, Size in, Size out) {
    int turns = turns(p);
    return (src, inW, inH, dst, outW, outH, y0, y1) -> {
      for (int y = y0; y < y1; y++) {
        int o = y * outW;
        for (int x = 0; x < outW; x++, o++) {
          dst[o] =
              switch (turns) {
                case 1 -> src[(inH - 1 - x) * inW + y];
                case 2 -> src[(inH - 1 - y) * inW + inW - 1 - x];
                case 3 -> src[x * inW + inW - 1 - y];
                default -> src[y * inW + x];
              };
        }
      }
    };
  }
}
