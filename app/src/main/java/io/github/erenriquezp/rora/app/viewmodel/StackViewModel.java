package io.github.erenriquezp.rora.app.viewmodel;

import io.github.erenriquezp.rora.app.catalog.Catalog;
import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.model.ControlSpec;
import io.github.erenriquezp.rora.app.model.RecipeEdits;
import io.github.erenriquezp.rora.app.session.ai.AiJobService;
import io.github.erenriquezp.rora.app.session.ai.AiResolver;
import io.github.erenriquezp.rora.core.op.EditRecipe;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.Params;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** La pila de ajustes: lo que se ve de la receta y lo que se puede hacer con cada paso. */
public final class StackViewModel<F> {

  /**
   * Fila de la pila; el índice es el del paso en la receta.
   *
   * @param badge estado de la mejora con IA; vacío en los demás pasos
   * @param badgeHint explicación de la insignia; vacía si no hace falta
   */
  public record Row(
      int index,
      String title,
      String summary,
      boolean enabled,
      boolean movable,
      String badge,
      String badgeHint) {}

  private static final int MAX_SUMMARY_PARAMS = 2;
  private static final String[] NO_BADGE = {"", ""};

  private final Env<F> env;
  private final ObservableList<Row> rows = FXCollections.observableArrayList();
  private final IntegerProperty selected = new SimpleIntegerProperty(-1);
  private final StringProperty header = new SimpleStringProperty("");
  private Runnable openEnhance = () -> {};

  StackViewModel(Env<F> env) {
    this.env = env;
    updateHeader();
  }

  public ObservableList<Row> rows() {
    return rows;
  }

  public IntegerProperty selectedProperty() {
    return selected;
  }

  public StringProperty headerProperty() {
    return header;
  }

  /** Qué hacer al pulsar el paso de mejora: tiene panel propio. */
  void onEnhanceSelected(Runnable action) {
    openEnhance = action;
  }

  /** Selecciona un paso; {@code adjust.basic} no se abre aparte: es el panel de Ajustes. */
  public void select(int index) {
    EditRecipe recipe = env.session().recipe();
    String opId =
        index >= 0 && index < recipe.size() ? recipe.steps().get(index).opId() : "";
    if (opId.equals(RecipeEdits.ENHANCE)) {
      selected.set(-1);
      openEnhance.run();
      return;
    }
    selected.set(opId.equals(RecipeEdits.ADJUST) ? -1 : index);
  }

  public void clearSelection() {
    selected.set(-1);
  }

  public void toggle(int index) {
    EditRecipe recipe = env.session().recipe();
    env.session().applyRecipe(recipe.setEnabled(index, !recipe.steps().get(index).enabled()), null);
  }

  public void remove(int index) {
    if (selected.get() == index) {
      selected.set(-1);
    } else if (selected.get() > index) {
      selected.set(selected.get() - 1);
    }
    env.session().applyRecipe(env.session().recipe().remove(index), null);
  }

  /** Reordena dentro de la zona reordenable; la selección sigue al paso. */
  public void move(int from, int to) {
    EditRecipe before = env.session().recipe();
    EditRecipe after = RecipeEdits.move(before, from, to);
    if (after == before) {
      return;
    }
    int sel = selected.get();
    int target = Math.max(RecipeEdits.geometryEnd(before), Math.min(before.size() - 1, to));
    if (sel == from) {
      selected.set(target);
    } else if (from < sel && sel <= target) {
      selected.set(sel - 1);
    } else if (target <= sel && sel < from) {
      selected.set(sel + 1);
    }
    env.session().applyRecipe(after, null);
  }

  void sync(EditRecipe recipe) {
    List<Row> next = new ArrayList<>();
    List<OpSpec> steps = recipe.steps();
    for (int i = 0; i < steps.size(); i++) {
      OpSpec s = steps.get(i);
      boolean enhance = s.opId().equals(RecipeEdits.ENHANCE);
      String[] badge = enhance && s.enabled() ? enhanceBadge() : NO_BADGE;
      next.add(
          new Row(
              i,
              titleOf(s, env.messages()),
              summaryOf(s),
              s.enabled(),
              !RecipeEdits.isGeometry(s.opId()) && !enhance,
              badge[0],
              badge[1]));
    }
    // Solo cambian las filas distintas: la lista no parpadea mientras se arrastra un control.
    for (int i = 0; i < next.size(); i++) {
      if (i >= rows.size()) {
        rows.add(next.get(i));
      } else if (!rows.get(i).equals(next.get(i))) {
        rows.set(i, next.get(i));
      }
    }
    if (rows.size() > next.size()) {
      rows.remove(next.size(), rows.size());
    }
    if (selected.get() >= steps.size()) {
      selected.set(-1);
    }
    updateHeader();
  }

  private String[] enhanceBadge() {
    Messages m = env.messages();
    Optional<AiJobService> service = env.session().ai();
    Optional<AiResolver.Located> step = env.session().enhanceStep();
    if (service.isEmpty() || step.isEmpty()) {
      return NO_BADGE;
    }
    String classic = m.get("ui.stack.enhance.classic");
    return switch (service.get().state(step.get().key())) {
      case NOT_RUN -> new String[] {m.get("ui.stack.enhance.notRun"), classic};
      case RUNNING -> new String[] {m.get("ui.stack.enhance.running"), classic};
      case READY -> new String[] {m.get("ui.stack.enhance.ready"), ""};
      case STALE -> new String[] {m.get("ui.stack.enhance.stale"), classic};
    };
  }

  private void updateHeader() {
    header.set(env.messages().format("ui.stack.title", rows.size()));
  }

  static String titleOf(OpSpec step, Messages m) {
    return Catalog.ENTRIES.stream()
        .filter(e -> matches(e, step))
        .findFirst()
        .map(e -> m.get(e.labelKey()))
        .orElseGet(() -> m.get("op." + step.opId() + ".title"));
  }

  /** La entrada del catálogo representa este paso: misma operación y mismos valores fijados. */
  static boolean matches(Catalog.Entry entry, OpSpec step) {
    if (!entry.opId().equals(step.opId())) {
      return false;
    }
    for (Map.Entry<String, Object> fixed : entry.fixedParams().entrySet()) {
      if (!fixed.getValue().equals(step.params().get(fixed.getKey()))) {
        return false;
      }
    }
    return true;
  }

  private String summaryOf(OpSpec step) {
    Messages m = env.messages();
    Operation op = env.session().registry().require(step.opId());
    Params params = step.params().withSchema(op.schema());
    Params defaults = op.schema().defaults();
    if (step.opId().equals(RecipeEdits.ADJUST)) {
      Params neutral = op.schema().neutral().orElseThrow();
      long changed =
          op.schema().params().stream()
              .filter(d -> !params.get(d.key()).equals(neutral.get(d.key())))
              .count();
      return m.format("ui.stack.changes", changed);
    }
    if (step.opId().equals(RecipeEdits.CROP)) {
      return "";
    }
    if (step.opId().equals(RecipeEdits.ENHANCE)) {
      return m.get("op.ai.enhance.scale." + params.getEnum("scale"));
    }
    Set<String> hidden =
        Catalog.ENTRIES.stream()
            .filter(e -> matches(e, step))
            .findFirst()
            .map(Catalog.Entry::hiddenParams)
            .orElse(Set.of());
    List<String> parts = new ArrayList<>();
    for (ControlSpec spec : ControlSpec.from(op.schema(), m, hidden)) {
      if (parts.size() < MAX_SUMMARY_PARAMS
          && spec instanceof ControlSpec.Slider s
          && (!params.get(s.key()).equals(defaults.get(s.key()))
              || op.schema().params().size() == 1)) {
        parts.add(s.format(((Number) params.get(s.key())).doubleValue(), m.locale()));
      }
    }
    return String.join("  ", parts);
  }
}
