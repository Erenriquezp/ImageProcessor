package io.github.erenriquezp.rora.core.op;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Descriptor de un parámetro de operación. No contiene texto de UI: {@link #labelKey()} es una
 * clave de traducción. Los rangos son <b>recomendaciones</b> para la interfaz y los tests; el
 * núcleo no recorta ni rechaza valores fuera de rango (ver {@link Params}).
 */
public sealed interface ParamDef {

  /** Clave estable del parámetro dentro de su operación. */
  String key();

  /** Clave de traducción de la etiqueta. */
  String labelKey();

  /** Valor por defecto, en el tipo de almacenamiento de {@link Params}. */
  Object defaultValue();

  /** Valor con el que el parámetro no altera la imagen, si existe. */
  Optional<Object> neutralValue();

  /** Entero en {@code [min, max]}. */
  record IntParam(
      String key, String labelKey, int min, int max, int defaultInt, OptionalInt neutral)
      implements ParamDef {
    public IntParam {
      checkKeys(key, labelKey);
      if (min > max) {
        throw new IllegalArgumentException(key + ": min > max");
      }
      Objects.requireNonNull(neutral, "neutral");
    }

    @Override
    public Object defaultValue() {
      return defaultInt;
    }

    @Override
    public Optional<Object> neutralValue() {
      return neutral.isPresent() ? Optional.of(neutral.getAsInt()) : Optional.empty();
    }
  }

  /** Decimal en {@code [min, max]} con un paso sugerido para la UI. */
  record DecimalParam(
      String key,
      String labelKey,
      double min,
      double max,
      double step,
      double defaultDouble,
      OptionalDouble neutral)
      implements ParamDef {
    public DecimalParam {
      checkKeys(key, labelKey);
      if (!(min <= max) || !(step > 0)) {
        throw new IllegalArgumentException(key + ": rango o paso no válidos");
      }
      Objects.requireNonNull(neutral, "neutral");
    }

    @Override
    public Object defaultValue() {
      return defaultDouble;
    }

    @Override
    public Optional<Object> neutralValue() {
      return neutral.isPresent() ? Optional.of(neutral.getAsDouble()) : Optional.empty();
    }
  }

  /** Booleano. */
  record BoolParam(String key, String labelKey, boolean defaultBool, Optional<Boolean> neutral)
      implements ParamDef {
    public BoolParam {
      checkKeys(key, labelKey);
      Objects.requireNonNull(neutral, "neutral");
    }

    @Override
    public Object defaultValue() {
      return defaultBool;
    }

    @Override
    public Optional<Object> neutralValue() {
      return neutral.map(b -> b);
    }
  }

  /** Uno de varios valores con id estable (p. ej. {@code "rgb"}, {@code "luminance"}). */
  record EnumParam(
      String key,
      String labelKey,
      List<String> values,
      String defaultEnum,
      Optional<String> neutral)
      implements ParamDef {
    public EnumParam {
      checkKeys(key, labelKey);
      values = List.copyOf(values);
      if (values.isEmpty() || Set.copyOf(values).size() != values.size()) {
        throw new IllegalArgumentException(key + ": valores vacíos o repetidos");
      }
      if (!values.contains(defaultEnum)) {
        throw new IllegalArgumentException(key + ": el valor por defecto no está en values");
      }
      Objects.requireNonNull(neutral, "neutral");
      if (neutral.isPresent() && !values.contains(neutral.get())) {
        throw new IllegalArgumentException(key + ": el valor neutro no está en values");
      }
    }

    @Override
    public Object defaultValue() {
      return defaultEnum;
    }

    @Override
    public Optional<Object> neutralValue() {
      return neutral.map(n -> n);
    }
  }

  /** Color ARGB empaquetado; si {@code alpha} es falso, la UI solo ofrece RGB. */
  record ColorParam(
      String key, String labelKey, int defaultArgb, boolean alpha, OptionalInt neutral)
      implements ParamDef {
    public ColorParam {
      checkKeys(key, labelKey);
      Objects.requireNonNull(neutral, "neutral");
    }

    @Override
    public Object defaultValue() {
      return defaultArgb;
    }

    @Override
    public Optional<Object> neutralValue() {
      return neutral.isPresent() ? Optional.of(neutral.getAsInt()) : Optional.empty();
    }
  }

  /** Subconjunto de varias opciones (p. ej. canales {@code r}, {@code g}, {@code b}). */
  record SelectionParam(
      String key,
      String labelKey,
      List<String> options,
      Set<String> defaultSelection,
      Optional<Set<String>> neutral)
      implements ParamDef {
    public SelectionParam {
      checkKeys(key, labelKey);
      options = List.copyOf(options);
      defaultSelection = Set.copyOf(defaultSelection);
      if (options.isEmpty() || Set.copyOf(options).size() != options.size()) {
        throw new IllegalArgumentException(key + ": opciones vacías o repetidas");
      }
      if (!options.containsAll(defaultSelection)) {
        throw new IllegalArgumentException(key + ": la selección por defecto no está en options");
      }
      Objects.requireNonNull(neutral, "neutral");
      neutral = neutral.map(Set::copyOf);
    }

    @Override
    public Object defaultValue() {
      return defaultSelection;
    }

    @Override
    public Optional<Object> neutralValue() {
      return neutral.map(n -> n);
    }
  }

  private static void checkKeys(String key, String labelKey) {
    if (key == null || !key.matches("[a-z][a-zA-Z0-9]*")) {
      throw new IllegalArgumentException("Clave de parámetro no válida: " + key);
    }
    Objects.requireNonNull(labelKey, "labelKey");
  }
}
