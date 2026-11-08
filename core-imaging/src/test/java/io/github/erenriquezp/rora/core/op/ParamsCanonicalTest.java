package io.github.erenriquezp.rora.core.op;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.op.ParamDef.BoolParam;
import io.github.erenriquezp.rora.core.op.ParamDef.ColorParam;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamDef.EnumParam;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import io.github.erenriquezp.rora.core.op.ParamDef.SelectionParam;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** Igualdad por valor de {@link Params} y su forma canónica (fase 2). */
class ParamsCanonicalTest {

  private static ParamSchema schema() {
    // Instancia nueva en cada llamada: la igualdad no puede depender de la identidad.
    return ParamSchema.of(
        new IntParam("levels", "op.t.levels", 2, 64, 8, OptionalInt.of(64)),
        new DecimalParam("factor", "op.t.factor", 0, 2, 0.01, 1, OptionalDouble.of(1)),
        new BoolParam("mark", "op.t.mark", false, Optional.of(false)),
        new EnumParam("mode", "op.t.mode", List.of("rgb", "luminance"), "rgb", Optional.empty()),
        new ColorParam("tone", "op.t.tone", 0xFFC87850, false, OptionalInt.empty()),
        new SelectionParam(
            "channels",
            "op.t.channels",
            List.of("r", "g", "b"),
            Set.of("r", "g", "b"),
            Optional.empty()));
  }

  @Test
  void equalSchemasAreEqualEvenIfDistinctInstances() {
    ParamSchema a = schema();
    ParamSchema b = schema();
    assertThat(a).isNotSameAs(b).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a.defaults()).isEqualTo(b.defaults()).hasSameHashCodeAs(b.defaults());
  }

  @Test
  void differentSchemasAreNotEqual() {
    ParamSchema other =
        ParamSchema.of(new IntParam("levels", "op.t.levels", 2, 32, 8, OptionalInt.of(64)));
    ParamSchema mine =
        ParamSchema.of(new IntParam("levels", "op.t.levels", 2, 64, 8, OptionalInt.of(64)));
    assertThat(other).isNotEqualTo(mine);
    assertThat(other.defaults()).isNotEqualTo(mine.defaults());
    assertThat(schema()).isNotEqualTo(null).isNotEqualTo("x");
  }

  @Test
  void differentValuesAreNotEqual() {
    ParamSchema s = schema();
    assertThat(Params.builder(s).set("levels", 9).build()).isNotEqualTo(s.defaults());
  }

  @Test
  void twoDoublesRoundingToTheSameFloatGiveTheSameKeyWhenPassedAsFloat() {
    // Lo que hace el mapeo de la UI: pasa el float que recibiría la fachada.
    double slider1 = 1.3;
    double slider2 = Math.nextUp(1.3);
    assertThat((float) slider1).isEqualTo((float) slider2);
    ParamSchema s = schema();
    Params p1 = Params.builder(s).set("factor", (float) slider1).build();
    Params p2 = Params.builder(schema()).set("factor", (float) slider2).build();
    assertThat(p1).isEqualTo(p2).hasSameHashCodeAs(p2);
    assertThat(p1.canonical()).isEqualTo(p2.canonical());
    // Pasar el double crudo distinguiría los valores (fallo de caché, nunca un acierto falso).
    assertThat(Params.builder(s).set("factor", slider1).build())
        .isNotEqualTo(Params.builder(s).set("factor", slider2).build());
  }

  @Test
  void negativeZeroIsADistinctValue() {
    ParamSchema s = schema();
    assertThat(Params.builder(s).set("factor", -0.0).build())
        .isNotEqualTo(Params.builder(s).set("factor", 0.0).build());
  }

  @Test
  void canonicalFormIsInSchemaOrderAndLocaleIndependent() {
    Locale saved = Locale.getDefault();
    try {
      Locale.setDefault(Locale.GERMANY);
      Params p =
          Params.builder(schema())
              .set("levels", 4)
              .set("factor", 1.5f)
              .set("mark", true)
              .set("mode", "luminance")
              .set("tone", 0x0A0B0C0D)
              .set("channels", Set.of("b", "r"))
              .build();
      assertThat(p.canonical())
          .isEqualTo(
              "{levels=4,factor=1.5,mark=true,mode=luminance,tone=#0A0B0C0D,channels=[r|b]}");
      assertThat(schema().defaults().canonical())
          .isEqualTo("{levels=8,factor=1.0,mark=false,mode=rgb,tone=#FFC87850,channels=[r|g|b]}");
      assertThat(ParamSchema.EMPTY.defaults().canonical()).isEqualTo("{}");
    } finally {
      Locale.setDefault(saved);
    }
  }

  @Test
  void canonicalDoublesRoundTripExactly() {
    SplittableRandom rnd = new SplittableRandom(0x5EED_0002L);
    for (int i = 0; i < 1_000_000; i++) {
      double d = Double.longBitsToDouble(rnd.nextLong());
      if (Double.isNaN(d)) {
        continue;
      }
      assertThat(Double.parseDouble(Double.toString(d))).isEqualTo(d);
      float f = Float.intBitsToFloat(rnd.nextInt());
      if (!Float.isNaN(f)) {
        double wide = f;
        assertThat(Double.parseDouble(Double.toString(wide))).isEqualTo(wide);
      }
    }
  }

  @Test
  void canonicalDoublesOfEverySliderStepOfTheRegistryRoundTrip() {
    for (Operation op : OpRegistry.load().all()) {
      for (ParamDef d : op.schema().params()) {
        if (d instanceof DecimalParam dp) {
          long steps = Math.round((dp.max() - dp.min()) / dp.step());
          for (long k = 0; k <= steps; k++) {
            double wide = (float) (dp.min() + k * dp.step());
            Params p = Params.builder(op.schema()).set(dp.key(), wide).build();
            String c = p.canonical();
            String text = c.substring(c.indexOf(dp.key() + "=") + dp.key().length() + 1);
            text = text.split("[,}]")[0];
            assertThat(Double.parseDouble(text)).as(op.id() + "." + dp.key()).isEqualTo(wide);
          }
        }
      }
    }
  }
}
