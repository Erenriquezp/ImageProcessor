package io.github.erenriquezp.rora.core.op;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.op.ParamDef.BoolParam;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamDef.EnumParam;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** {@link ParamUnit} y parámetros ocultos (cambio aditivo de la fase 2). */
class ParamUnitTest {

  @Test
  void legacyConstructorsDefaultToNoUnitAndVisible() {
    IntParam i = new IntParam("n", "l", 0, 9, 1, OptionalInt.empty());
    DecimalParam d = new DecimalParam("f", "l", 0, 1, 0.1, 0.5, OptionalDouble.empty());
    EnumParam e = new EnumParam("m", "l", List.of("a", "b"), "a", Optional.empty());
    BoolParam b = new BoolParam("x", "l", false, Optional.empty());
    assertThat(List.of(i.unit(), d.unit(), e.unit(), b.unit())).containsOnly(ParamUnit.NONE);
    assertThat(List.of(i.hidden(), d.hidden(), e.hidden(), b.hidden())).containsOnly(false);
  }

  @Test
  void pixelsFactoryAndHiddenEnum() {
    IntParam px = IntParam.pixels("n", "l", 2, 64, 8, OptionalInt.empty());
    assertThat(px.unit()).isEqualTo(ParamUnit.PIXELS);
    assertThat(px).isNotEqualTo(new IntParam("n", "l", 2, 64, 8, OptionalInt.empty()));
    EnumParam hidden = new EnumParam("m", "l", List.of("a"), "a", Optional.empty(), true);
    assertThat(hidden.hidden()).isTrue();
    assertThat(
            new DecimalParam("f", "l", 0, 1, 0.1, 0.5, OptionalDouble.empty(), ParamUnit.PIXELS)
                .unit())
        .isEqualTo(ParamUnit.PIXELS);
  }

  /** La lista de §5.3 del diseño: si cambia, el preview cambia, así que se fija aquí. */
  @Test
  void pixelParametersOfTheCoreRegistry() {
    Set<String> pixels = new TreeSet<>();
    for (Operation op : OpRegistry.load().all()) {
      for (ParamDef d : op.schema().params()) {
        if (d.unit() == ParamUnit.PIXELS) {
          pixels.add(op.id() + "." + d.key());
        }
        assertThat(d.hidden()).as(op.id() + "." + d.key()).isFalse();
      }
    }
    assertThat(pixels).containsExactly("accum.motion.displacement", "raster.pixelate.block");
  }
}
