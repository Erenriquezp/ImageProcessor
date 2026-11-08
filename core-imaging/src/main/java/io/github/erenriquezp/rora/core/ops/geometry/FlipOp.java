package io.github.erenriquezp.rora.core.ops.geometry;

import io.github.erenriquezp.rora.core.image.ProxyPolicy.Size;
import io.github.erenriquezp.rora.core.op.Cost;
import io.github.erenriquezp.rora.core.op.OpKind;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.EnumParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import java.util.List;
import java.util.Optional;

/** Volteo exacto, horizontal o vertical. */
public final class FlipOp extends GeometryOp {

  public static final String ID = "transform.flip";

  private static final ParamSchema SCHEMA =
      ParamSchema.of(
          new EnumParam(
              "axis",
              "op.transform.flip.axis",
              List.of("horizontal", "vertical"),
              "horizontal",
              Optional.empty()));

  private static final OpMeta META = new OpMeta(OpKind.GLOBAL, true, false, Cost.CHEAP, 0);

  public FlipOp() {
    super(ID, SCHEMA, META);
  }

  @Override
  RowFiller prepare(Params p, Size in, Size out) {
    boolean horizontal = p.getEnum("axis").equals("horizontal");
    return (src, inW, inH, dst, outW, outH, y0, y1) -> {
      for (int y = y0; y < y1; y++) {
        if (horizontal) {
          for (int x = 0; x < outW; x++) {
            dst[y * outW + x] = src[y * inW + inW - 1 - x];
          }
        } else {
          System.arraycopy(src, (inH - 1 - y) * inW, dst, y * outW, outW);
        }
      }
    };
  }
}
