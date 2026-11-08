package io.github.erenriquezp.rora.core.ops;

import io.github.erenriquezp.rora.core.op.BandOperation;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import java.util.Objects;

/**
 * {@link BandOperation} con id, esquema y metadatos fijos. Cada subclase escribe su propio bucle en
 * {@code prepare} (no un método por píxel compartido), para que el JIT pueda optimizar cada bucle
 * por separado.
 */
public abstract class SimpleBandOp extends BandOperation {

  private final String id;
  private final ParamSchema schema;
  private final OpMeta meta;

  protected SimpleBandOp(String id, ParamSchema schema, OpMeta meta) {
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
