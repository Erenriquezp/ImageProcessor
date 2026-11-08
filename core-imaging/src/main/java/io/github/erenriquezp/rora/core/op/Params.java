package io.github.erenriquezp.rora.core.op;

import io.github.erenriquezp.rora.core.op.ParamDef.BoolParam;
import io.github.erenriquezp.rora.core.op.ParamDef.ColorParam;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamDef.EnumParam;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import io.github.erenriquezp.rora.core.op.ParamDef.SelectionParam;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Valores de los parámetros de una operación, validados contra su {@link ParamSchema}. Inmutable.
 *
 * <p>Se valida el <b>tipo</b> (y que un enum o una selección usen valores declarados), pero no el
 * rango: el comportamiento heredado que se porta hace su propio recorte, y rechazar valores
 * cambiaría ese comportamiento.
 *
 * <p>Almacenamiento: enteros y colores como {@code Integer}, decimales como {@code Double}
 * (convertir un {@code float} a {@code double} y volver es exacto), booleanos, ids de enum como
 * {@code String} y selecciones como {@code Set<String>} inmutable.
 */
public final class Params {

  private final ParamSchema schema;
  private final Map<String, Object> values;

  private Params(ParamSchema schema, Map<String, Object> values) {
    this.schema = schema;
    this.values = values;
  }

  public static Builder builder(ParamSchema schema) {
    return new Builder(Objects.requireNonNull(schema, "schema"));
  }

  public ParamSchema schema() {
    return schema;
  }

  /**
   * Los mismos valores asociados a {@code target}, que debe ser igual (por valor) a {@link
   * #schema()}; devuelve {@code this} si ya es la misma instancia. Sirve para aplicar con una
   * operación unos parámetros construidos con otra instancia de su esquema.
   */
  public Params withSchema(ParamSchema target) {
    if (target == schema) {
      return this;
    }
    if (!schema.equals(target)) {
      throw new IllegalArgumentException("Esquema distinto: " + target + " vs " + schema);
    }
    return new Params(target, values);
  }

  /** Copia editable de estos valores. */
  public Builder toBuilder() {
    Builder b = new Builder(schema);
    b.values.putAll(values);
    return b;
  }

  public int getInt(String key) {
    expect(key, IntParam.class);
    return (Integer) values.get(key);
  }

  public double getDouble(String key) {
    expect(key, DecimalParam.class);
    return (Double) values.get(key);
  }

  /** Decimal como {@code float} (las operaciones heredadas trabajan en {@code float}). */
  public float getFloat(String key) {
    return (float) getDouble(key);
  }

  public boolean getBool(String key) {
    expect(key, BoolParam.class);
    return (Boolean) values.get(key);
  }

  public String getEnum(String key) {
    expect(key, EnumParam.class);
    return (String) values.get(key);
  }

  public int getColor(String key) {
    expect(key, ColorParam.class);
    return (Integer) values.get(key);
  }

  @SuppressWarnings("unchecked")
  public Set<String> getSelection(String key) {
    expect(key, SelectionParam.class);
    return (Set<String>) values.get(key);
  }

  /** Valor almacenado, sin conversión. */
  public Object get(String key) {
    schema.get(key);
    return values.get(key);
  }

  private void expect(String key, Class<? extends ParamDef> type) {
    ParamDef d = schema.get(key);
    if (!type.isInstance(d)) {
      throw new IllegalArgumentException(
          "El parámetro "
              + key
              + " es "
              + d.getClass().getSimpleName()
              + ", no "
              + type.getSimpleName());
    }
  }

  /**
   * Igualdad <b>por valor</b>: esquemas estructuralmente iguales (ver {@link ParamSchema#equals}) y
   * los mismos valores. Los decimales se comparan como {@code Double.equals}: un {@code float}
   * ensanchado a {@code double} da siempre el mismo valor, así que dos entradas que acaban en el
   * mismo {@code float} son iguales; {@code -0.0} y {@code 0.0} son distintos.
   */
  @Override
  public boolean equals(Object o) {
    return o instanceof Params p
        && (p.schema == schema || p.schema.equals(schema))
        && p.values.equals(values);
  }

  @Override
  public int hashCode() {
    return 31 * schema.hashCode() + values.hashCode();
  }

  /**
   * Forma canónica estable, en el orden del esquema y sin depender del locale: enteros en decimal,
   * decimales con {@link Double#toString(double)} (la representación más corta que identifica el
   * valor; ida y vuelta exacta), booleanos, ids de enum, colores {@code #AARRGGBB} y selecciones en
   * el orden de las opciones. Ejemplo: {@code {levels=8,factor=1.5,tone=#FFC87850}}.
   */
  public String canonical() {
    StringJoiner j = new StringJoiner(",", "{", "}");
    for (ParamDef d : schema.params()) {
      Object v = values.get(d.key());
      String text =
          switch (d) {
            case ColorParam c -> String.format(Locale.ROOT, "#%08X", (Integer) v);
            case DecimalParam c -> Double.toString((Double) v);
            case SelectionParam c -> {
              Set<?> sel = (Set<?>) v;
              StringJoiner sj = new StringJoiner("|", "[", "]");
              for (String opt : c.options()) {
                if (sel.contains(opt)) {
                  sj.add(opt);
                }
              }
              yield sj.toString();
            }
            default -> String.valueOf(v);
          };
      j.add(d.key() + "=" + text);
    }
    return j.toString();
  }

  /** Representación determinista, en el orden del esquema. */
  @Override
  public String toString() {
    StringJoiner j = new StringJoiner(", ", "{", "}");
    for (ParamDef d : schema.params()) {
      Object v = values.get(d.key());
      if (v instanceof Set<?> s) {
        v = s.stream().map(String::valueOf).sorted().toList();
      }
      j.add(d.key() + "=" + v);
    }
    return j.toString();
  }

  /** Constructor de {@link Params}; empieza con los valores por defecto del esquema. */
  public static final class Builder {
    private final ParamSchema schema;
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder(ParamSchema schema) {
      this.schema = schema;
      for (ParamDef d : schema.params()) {
        values.put(d.key(), d.defaultValue());
      }
    }

    /**
     * Fija un valor. Acepta {@code Integer/Short/Byte/Long} (dentro de {@code int}) para enteros y
     * colores, cualquier {@code Number} para decimales, {@code Boolean}, {@code String} para enums
     * y {@code Collection<String>} para selecciones.
     */
    public Builder set(String key, Object value) {
      ParamDef d = schema.get(key);
      Objects.requireNonNull(value, key);
      values.put(key, convert(d, value));
      return this;
    }

    public Params build() {
      return new Params(schema, Map.copyOf(values));
    }

    private static Object convert(ParamDef d, Object v) {
      return switch (d) {
        case IntParam p -> toInt(p.key(), v);
        case ColorParam p -> toInt(p.key(), v);
        case DecimalParam p -> {
          if (v instanceof Number n) {
            yield n.doubleValue();
          }
          throw wrongType(p.key(), v, "decimal");
        }
        case BoolParam p -> {
          if (v instanceof Boolean b) {
            yield b;
          }
          throw wrongType(p.key(), v, "booleano");
        }
        case EnumParam p -> {
          if (v instanceof String s && p.values().contains(s)) {
            yield s;
          }
          throw new IllegalArgumentException(
              p.key() + ": valor " + v + " no está en " + p.values());
        }
        case SelectionParam p -> {
          if (v instanceof Collection<?> c) {
            for (Object o : c) {
              if (!(o instanceof String s) || !p.options().contains(s)) {
                throw new IllegalArgumentException(
                    p.key() + ": opción " + o + " no está en " + p.options());
              }
            }
            @SuppressWarnings("unchecked")
            Collection<String> cs = (Collection<String>) c;
            yield Set.copyOf(cs);
          }
          throw wrongType(p.key(), v, "selección");
        }
      };
    }

    private static int toInt(String key, Object v) {
      if (v instanceof Integer || v instanceof Short || v instanceof Byte || v instanceof Long) {
        long l = ((Number) v).longValue();
        if (l == (int) l) {
          return (int) l;
        }
      }
      throw wrongType(key, v, "entero");
    }

    private static IllegalArgumentException wrongType(String key, Object v, String expected) {
      return new IllegalArgumentException(
          key + ": se esperaba " + expected + " y se recibió " + v.getClass().getSimpleName());
    }
  }
}
