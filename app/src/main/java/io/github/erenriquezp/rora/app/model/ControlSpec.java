package io.github.erenriquezp.rora.app.model;

import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.core.op.ParamDef;
import io.github.erenriquezp.rora.core.op.ParamDef.BoolParam;
import io.github.erenriquezp.rora.core.op.ParamDef.ColorParam;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamDef.EnumParam;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import io.github.erenriquezp.rora.core.op.ParamDef.SelectionParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Descripción de un control de parámetro, sin nodos: se genera desde un {@link ParamSchema} y se
 * prueba sin toolkit.
 */
public sealed interface ControlSpec {

  String key();

  String label();

  /** Valor al que vuelve con "restablecer": el neutro del parámetro o, si no tiene, el defecto. */
  Object resetValue();

  /** Hasta 3 opciones, botones segmentados; con más, lista desplegable. */
  int SEGMENTED_MAX = 3;

  record Slider(
      String key,
      String label,
      double min,
      double max,
      double step,
      int decimals,
      boolean integer,
      Object resetValue)
      implements ControlSpec {

    public double clamp(double value) {
      return Math.max(min, Math.min(max, value));
    }

    /** Ajusta a los decimales del control y al rango, para que el valor guardado sea limpio. */
    public double normalize(double value) {
      double p = Math.pow(10, decimals);
      return clamp(Math.round(value * p) / p);
    }

    /** Valor tras un paso de teclado; {@code fine} da una décima parte del paso. */
    public double stepped(double current, int direction, boolean fine) {
      if (integer) {
        return normalize(current + direction * (fine ? 1 : Math.max(1, Math.round(step))));
      }
      double p = Math.pow(10, decimals + (fine ? 1 : 0));
      double delta = direction * (fine ? step / 10 : step);
      return clamp(Math.round((current + delta) * p) / p);
    }

    /** Valor como se muestra, con signo si el rango es simétrico. */
    public String format(double value, Locale locale) {
      String pattern = decimals == 0 ? "0" : "0." + "0".repeat(decimals);
      DecimalFormat f = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale));
      String text = f.format(value);
      return min < 0 && value > 0 ? "+" + text : text;
    }

    public double resetDouble() {
      return ((Number) resetValue).doubleValue();
    }
  }

  record Toggle(String key, String label, Object resetValue) implements ControlSpec {}

  record Choice(
      String key,
      String label,
      List<String> values,
      List<String> valueLabels,
      boolean segmented,
      Object resetValue)
      implements ControlSpec {}

  record ColorPick(String key, String label, Object resetValue) implements ControlSpec {}

  record Checks(
      String key, String label, List<String> options, List<String> optionLabels, Object resetValue)
      implements ControlSpec {}

  /** Controles de {@code schema}, sin los parámetros ocultos ni los de {@code hiddenKeys}. */
  static List<ControlSpec> from(ParamSchema schema, Messages messages, Set<String> hiddenKeys) {
    List<ControlSpec> out = new ArrayList<>();
    for (ParamDef d : schema.params()) {
      if (!d.hidden() && !hiddenKeys.contains(d.key())) {
        out.add(of(d, messages));
      }
    }
    return List.copyOf(out);
  }

  private static ControlSpec of(ParamDef d, Messages m) {
    String label = m.get(d.labelKey());
    Object reset = d.neutralValue().orElse(d.defaultValue());
    return switch (d) {
      case IntParam p -> new Slider(p.key(), label, p.min(), p.max(), 1, 0, true, reset);
      case DecimalParam p ->
          new Slider(
              p.key(), label, p.min(), p.max(), p.step(), decimalsOf(p.step()), false, reset);
      case BoolParam p -> new Toggle(p.key(), label, reset);
      case EnumParam p ->
          new Choice(
              p.key(),
              label,
              p.values(),
              optionLabels(p.labelKey(), p.values(), m),
              p.values().size() <= SEGMENTED_MAX,
              reset);
      case ColorParam p -> new ColorPick(p.key(), label, reset);
      case SelectionParam p ->
          new Checks(
              p.key(), label, p.options(), optionLabels(p.labelKey(), p.options(), m), reset);
    };
  }

  private static List<String> optionLabels(String labelKey, List<String> values, Messages m) {
    return values.stream().map(v -> m.get(labelKey + "." + v)).toList();
  }

  private static int decimalsOf(double step) {
    int d = 0;
    double s = step;
    while (d < 6 && Math.abs(s - Math.rint(s)) > 1e-9) {
      s *= 10;
      d++;
    }
    return d;
  }
}
