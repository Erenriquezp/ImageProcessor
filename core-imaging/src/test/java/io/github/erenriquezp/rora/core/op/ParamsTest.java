package io.github.erenriquezp.rora.core.op;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.core.op.ParamDef.BoolParam;
import io.github.erenriquezp.rora.core.op.ParamDef.ColorParam;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamDef.EnumParam;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import io.github.erenriquezp.rora.core.op.ParamDef.SelectionParam;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ParamsTest {

  static final IntParam LEVELS =
      new IntParam("levels", "op.t.levels", 2, 64, 8, OptionalInt.of(64));
  static final DecimalParam FACTOR =
      new DecimalParam("factor", "op.t.factor", 0, 2, 0.01, 1, OptionalDouble.of(1));
  static final BoolParam MARK = new BoolParam("mark", "op.t.mark", false, Optional.of(false));
  static final EnumParam MODE =
      new EnumParam("mode", "op.t.mode", List.of("rgb", "luminance"), "rgb", Optional.of("rgb"));
  static final ColorParam TONE =
      new ColorParam("tone", "op.t.tone", 0xFFC87850, false, OptionalInt.of(0xFFFFFFFF));
  static final SelectionParam CHANNELS =
      new SelectionParam(
          "channels",
          "op.t.channels",
          List.of("r", "g", "b"),
          Set.of("r", "g", "b"),
          Optional.of(Set.of()));

  static final ParamSchema SCHEMA = ParamSchema.of(LEVELS, FACTOR, MARK, MODE, TONE, CHANNELS);

  @Test
  void defaultsComeFromTheSchema() {
    Params p = SCHEMA.defaults();
    assertThat(p.getInt("levels")).isEqualTo(8);
    assertThat(p.getDouble("factor")).isEqualTo(1.0);
    assertThat(p.getBool("mark")).isFalse();
    assertThat(p.getEnum("mode")).isEqualTo("rgb");
    assertThat(p.getColor("tone")).isEqualTo(0xFFC87850);
    assertThat(p.getSelection("channels")).containsExactlyInAnyOrder("r", "g", "b");
    assertThat(p.schema()).isSameAs(SCHEMA);
    assertThat(p.get("levels")).isEqualTo(8);
  }

  @Test
  void floatsRoundTripExactly() {
    for (float f : new float[] {1.2f, 0.9f, -20f, 0.3f, 1e-7f, Float.MAX_VALUE}) {
      Params p = Params.builder(SCHEMA).set("factor", f).build();
      assertThat(p.getFloat("factor")).isEqualTo(f);
    }
  }

  @Test
  void setConvertsAcceptedNumericTypes() {
    Params p =
        Params.builder(SCHEMA)
            .set("levels", (short) 4)
            .set("factor", 3)
            .set("tone", 0x112233L)
            .set("channels", List.of("r"))
            .build();
    assertThat(p.getInt("levels")).isEqualTo(4);
    assertThat(p.getDouble("factor")).isEqualTo(3.0);
    assertThat(p.getColor("tone")).isEqualTo(0x112233);
    assertThat(p.getSelection("channels")).containsExactly("r");
    assertThat(Params.builder(SCHEMA).set("levels", (byte) 2).build().getInt("levels"))
        .isEqualTo(2);
  }

  @Test
  void rangesAreNotEnforced() {
    // El legacy recorta por su cuenta: el núcleo no rechaza valores fuera del rango recomendado.
    Params p = Params.builder(SCHEMA).set("levels", 1000).set("factor", -5.0).build();
    assertThat(p.getInt("levels")).isEqualTo(1000);
    assertThat(p.getDouble("factor")).isEqualTo(-5.0);
  }

  @Test
  void typesAndValuesAreValidated() {
    Params.Builder b = Params.builder(SCHEMA);
    assertThatThrownBy(() -> b.set("levels", 1.5)).hasMessageContaining("entero");
    assertThatThrownBy(() -> b.set("levels", 1L << 40)).hasMessageContaining("entero");
    assertThatThrownBy(() -> b.set("factor", "1")).hasMessageContaining("decimal");
    assertThatThrownBy(() -> b.set("mark", 1)).hasMessageContaining("booleano");
    assertThatThrownBy(() -> b.set("mode", "hsv")).hasMessageContaining("no está");
    assertThatThrownBy(() -> b.set("channels", List.of("a"))).hasMessageContaining("no está");
    assertThatThrownBy(() -> b.set("channels", "r")).hasMessageContaining("selección");
    assertThatThrownBy(() -> b.set("unknown", 1)).hasMessageContaining("desconocido");
    assertThatThrownBy(() -> b.set("levels", null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void gettersCheckTheParameterType() {
    Params p = SCHEMA.defaults();
    assertThatThrownBy(() -> p.getDouble("levels")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> p.getInt("tone")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> p.getBool("mode")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> p.getEnum("mark")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> p.getColor("levels")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> p.getSelection("mode")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> p.get("nope")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void neutralRequiresEveryParameter() {
    Params n = SCHEMA.neutral().orElseThrow();
    assertThat(n.getInt("levels")).isEqualTo(64);
    assertThat(n.getColor("tone")).isEqualTo(0xFFFFFFFF);
    assertThat(n.getSelection("channels")).isEmpty();

    IntParam noNeutral = new IntParam("block", "op.t.block", 2, 64, 8, OptionalInt.empty());
    assertThat(ParamSchema.of(noNeutral, FACTOR).neutral()).isEmpty();
    assertThat(ParamSchema.EMPTY.neutral()).isEmpty();
    assertThat(ParamSchema.of()).isSameAs(ParamSchema.EMPTY);
  }

  @Test
  void equalityToStringAndToBuilder() {
    Params a = Params.builder(SCHEMA).set("levels", 4).build();
    Params b = SCHEMA.defaults().toBuilder().set("levels", 4).build();
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(SCHEMA.defaults());
    assertThat(a).isNotEqualTo("x");
    assertThat(a.toString())
        .isEqualTo(
            "{levels=4, factor=1.0, mark=false, mode=rgb, tone=-3639216, channels=[b, g, r]}");
  }

  @Test
  void schemaLookupAndValidation() {
    assertThat(SCHEMA.params()).hasSize(6);
    assertThat(SCHEMA.isEmpty()).isFalse();
    assertThat(SCHEMA.find("mode")).contains(MODE);
    assertThat(SCHEMA.find("x")).isEmpty();
    assertThat(SCHEMA.get("tone")).isSameAs(TONE);
    assertThat(SCHEMA.toString()).contains("levels", "channels");
    assertThatThrownBy(() -> ParamSchema.of(LEVELS, LEVELS)).hasMessageContaining("repetido");
  }

  @Test
  void paramDefsValidateThemselves() {
    assertThatThrownBy(() -> new IntParam("Bad", "l", 0, 1, 0, OptionalInt.empty()))
        .hasMessageContaining("Clave");
    assertThatThrownBy(() -> new IntParam("k", "l", 2, 1, 0, OptionalInt.empty()))
        .hasMessageContaining("min");
    assertThatThrownBy(() -> new DecimalParam("k", "l", 0, 1, 0, 0, OptionalDouble.empty()))
        .hasMessageContaining("paso");
    assertThatThrownBy(() -> new DecimalParam("k", "l", 1, 0, 1, 0, OptionalDouble.empty()))
        .hasMessageContaining("rango");
    assertThatThrownBy(() -> new EnumParam("k", "l", List.of(), "a", Optional.empty()))
        .hasMessageContaining("vacíos");
    assertThatThrownBy(() -> new EnumParam("k", "l", List.of("a", "a"), "a", Optional.empty()))
        .hasMessageContaining("repetidos");
    assertThatThrownBy(() -> new EnumParam("k", "l", List.of("a"), "b", Optional.empty()))
        .hasMessageContaining("defecto");
    assertThatThrownBy(() -> new EnumParam("k", "l", List.of("a"), "a", Optional.of("b")))
        .hasMessageContaining("neutro");
    assertThatThrownBy(() -> new SelectionParam("k", "l", List.of(), Set.of(), Optional.empty()))
        .hasMessageContaining("vacías");
    assertThatThrownBy(
            () -> new SelectionParam("k", "l", List.of("a"), Set.of("b"), Optional.empty()))
        .hasMessageContaining("defecto");
    assertThatThrownBy(() -> new BoolParam("k", null, false, Optional.empty()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void neutralValuesAreExposedPerType() {
    assertThat(LEVELS.neutralValue()).contains(64);
    assertThat(FACTOR.neutralValue()).contains(1.0);
    assertThat(MARK.neutralValue()).contains(false);
    assertThat(MODE.neutralValue()).contains("rgb");
    assertThat(TONE.neutralValue()).contains(0xFFFFFFFF);
    assertThat(CHANNELS.neutralValue()).contains(Set.of());
    assertThat(new IntParam("k", "l", 0, 1, 0, OptionalInt.empty()).neutralValue()).isEmpty();
    assertThat(new DecimalParam("k", "l", 0, 1, 1, 0, OptionalDouble.empty()).neutralValue())
        .isEmpty();
    assertThat(new ColorParam("k", "l", 0, true, OptionalInt.empty()).neutralValue()).isEmpty();
    assertThat(new BoolParam("k", "l", true, Optional.empty()).neutralValue()).isEmpty();
  }
}
