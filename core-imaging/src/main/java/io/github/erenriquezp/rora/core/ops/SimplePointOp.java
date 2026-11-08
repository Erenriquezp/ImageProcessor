package io.github.erenriquezp.rora.core.ops;

import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.PointBandOperation;
import java.util.Objects;

/** {@link PointBandOperation} con id, esquema y metadatos fijos (como {@link SimpleBandOp}). */
public abstract class SimplePointOp extends PointBandOperation {

  private final String id;
  private final ParamSchema schema;
  private final OpMeta meta;

  protected SimplePointOp(String id, ParamSchema schema, OpMeta meta) {
    this.id = Objects.requireNonNull(id, "id");
    this.schema = Objects.requireNonNull(schema, "schema");
    this.meta = Objects.requireNonNull(meta, "meta");
  }

  @Override
  public final String id() {
    return id;
  }

  @Override
  public final ParamSchema schema() {
    return schema;
  }

  @Override
  public final OpMeta meta() {
    return meta;
  }
}
