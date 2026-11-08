package io.github.erenriquezp.rora.core.op;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.image.Img;
import java.util.List;
import java.util.SplittableRandom;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Contrato de {@link PointOperation} y de {@link Operation#returnsInput} sobre el registro. */
class PointOperationContractTest {

  private static final OpRegistry REGISTRY = OpRegistry.load();

  @Test
  void pointKindOperationsAreExactlyThePointOperations() {
    TreeSet<String> pointKind = new TreeSet<>();
    TreeSet<String> pointOps = new TreeSet<>();
    for (Operation op : REGISTRY.all()) {
      if (op.meta().kind() instanceof OpKind.Point) {
        pointKind.add(op.id());
      }
      if (op instanceof PointOperation) {
        pointOps.add(op.id());
      }
    }
    assertThat(pointOps).isEqualTo(pointKind).hasSize(19);
  }

  @Test
  void pointKernelReproducesApply() {
    SplittableRandom rnd = new SplittableRandom(0x5EED_0004L);
    Img src = RandomParams.noise(37, 23, 0x5EED_0005L);
    for (Operation op : REGISTRY.all()) {
      if (!(op instanceof PointOperation po)) {
        continue;
      }
      for (int i = 0; i < 20; i++) {
        Params p = RandomParams.random(op.schema(), rnd);
        Img viaApply = op.apply(src, p, OpContext.sequential()).image();
        Img viaFusion =
            PointFusion.apply(src, List.of(new PointFusion.Stage(po, p)), OpContext.sequential());
        assertThat(viaFusion.sameContent(viaApply)).as(op.id() + " " + p).isTrue();
      }
    }
  }

  @Test
  void returnsInputMeansSameInstance() {
    SplittableRandom rnd = new SplittableRandom(0x5EED_0006L);
    Img src = RandomParams.noise(9, 7, 0x5EED_0007L);
    TreeSet<String> withIdentity = new TreeSet<>();
    for (Operation op : REGISTRY.all()) {
      for (int i = 0; i < 40; i++) {
        Params p = i == 0 ? op.schema().defaults() : RandomParams.random(op.schema(), rnd);
        if (op.returnsInput(p)) {
          withIdentity.add(op.id());
          assertThat(op.apply(src, p, OpContext.sequential()).image())
              .as(op.id() + " " + p)
              .isSameAs(src);
        }
      }
    }
    Operation eq = REGISTRY.require("tone.equalize");
    Params zero = eq.schema().defaults().toBuilder().set("factor", 0.0).build();
    assertThat(eq.returnsInput(zero)).isTrue();
    assertThat(eq.returnsInput(zero.toBuilder().set("markDark", true).build())).isFalse();
    assertThat(eq.returnsInput(zero.toBuilder().set("factor", 0.5).build())).isFalse();
    assertThat(withIdentity).isSubsetOf("tone.equalize");
  }
}
