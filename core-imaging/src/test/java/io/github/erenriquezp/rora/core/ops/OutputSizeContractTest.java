package io.github.erenriquezp.rora.core.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy.Size;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.Params;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

/** {@code outputSize} coincide con lo que produce {@code apply} en todas las operaciones. */
@Tag("property")
class OutputSizeContractTest {

  private static final int[][] SIZES = {{1, 1}, {2, 3}, {17, 9}, {40, 64}};

  @TestFactory
  Stream<DynamicTest> outputSizeMatchesTheResult() {
    return OpRegistry.load().all().stream()
        .flatMap(
            op ->
                RegistryPropertiesTest.paramSets(op).stream()
                    .map(p -> dynamicTest(op.id() + " " + p, () -> check(op, p))));
  }

  private static void check(Operation op, Params p) {
    for (int[] s : SIZES) {
      Img out = op.apply(Img.create(s[0], s[1]), p, OpContext.sequential()).image();
      Size size = op.outputSize(new Size(s[0], s[1]), p);
      assertThat(out.width()).as("ancho con " + s[0] + "x" + s[1]).isEqualTo(size.width());
      assertThat(out.height()).as("alto con " + s[0] + "x" + s[1]).isEqualTo(size.height());
    }
  }
}
