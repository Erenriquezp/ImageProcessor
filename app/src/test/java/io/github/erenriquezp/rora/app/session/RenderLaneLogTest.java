package io.github.erenriquezp.rora.app.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.RenderKey;
import io.github.erenriquezp.rora.core.op.RenderLevel;
import org.junit.jupiter.api.Test;

/** El registro de un fallo de render lleva los ids de los pasos, nunca sus parámetros. */
class RenderLaneLogTest {

  @Test
  void describeShowsOnlyLevelAndOperationIds() {
    OpRegistry registry = OpRegistry.load();
    Operation blur = registry.require("blur.gaussian");
    OpSpec step =
        OpSpec.of(blur, blur.schema().defaults().toBuilder().set("radius", 37.25).build());
    RenderKey key = RenderKey.root(7, RenderLevel.FULL).then(step);
    assertThat(key.toString()).contains("37.25"); // la clave completa sí lleva el parámetro
    String described = RenderLane.describe(key);
    assertThat(described).isEqualTo("Full [blur.gaussian]");
    assertThat(described).doesNotContain("37.25").doesNotContain("radius");
    assertThat(RenderLane.describe(null)).isEqualTo("[]");
  }
}
