package io.github.erenriquezp.rora.app.viewmodel;

import io.github.erenriquezp.rora.app.catalog.Catalog;
import io.github.erenriquezp.rora.app.model.ControlSpec;
import io.github.erenriquezp.rora.core.op.EditRecipe;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.Params;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** Controles del paso seleccionado en la pila, generados desde su esquema. */
public final class ParamPanelViewModel<F> implements ControlState.Sink {

  private final Env<F> env;
  private final GestureTracker gesture;
  private final ObservableList<ControlState> controls = FXCollections.observableArrayList();
  private final StringProperty title = new SimpleStringProperty("");
  private int index = -1;
  private String opId;

  ParamPanelViewModel(Env<F> env, GestureTracker gesture) {
    this.env = env;
    this.gesture = gesture;
  }

  public ObservableList<ControlState> controls() {
    return controls;
  }

  public ReadOnlyStringProperty titleProperty() {
    return title;
  }

  /** Muestra el paso {@code stepIndex}, o nada con {@code -1}. */
  void show(int stepIndex) {
    EditRecipe recipe = env.session().recipe();
    if (stepIndex < 0 || stepIndex >= recipe.size()) {
      index = -1;
      opId = null;
      controls.clear();
      title.set("");
      return;
    }
    OpSpec step = recipe.steps().get(stepIndex);
    boolean sameStep = stepIndex == index && step.opId().equals(opId);
    index = stepIndex;
    opId = step.opId();
    title.set(StackViewModel.titleOf(step, env.messages()));
    Operation op = env.session().registry().require(step.opId());
    Params params = step.params().withSchema(op.schema());
    if (sameStep && !controls.isEmpty()) {
      controls.forEach(c -> c.sync(params.get(c.spec().key())));
      return;
    }
    Set<String> hidden =
        Catalog.ENTRIES.stream()
            .filter(e -> StackViewModel.matches(e, step))
            .findFirst()
            .map(Catalog.Entry::hiddenParams)
            .orElse(Set.of());
    List<ControlState> states = new ArrayList<>();
    for (ControlSpec spec : ControlSpec.from(op.schema(), env.messages(), hidden)) {
      states.add(new ControlState(spec, params.get(spec.key()), this));
    }
    controls.setAll(states);
  }

  @Override
  public void changed(ControlState control, boolean keyboard) {
    EditRecipe recipe = env.session().recipe();
    if (index < 0 || index >= recipe.size()) {
      return;
    }
    OpSpec step = recipe.steps().get(index);
    Params next =
        step.params().toBuilder().set(control.spec().key(), control.value()).build();
    env.session().setEditingIndex(index);
    env.session().applyRecipe(recipe.replace(index, step.withParams(next)), gesture.key(keyboard));
  }

  @Override
  public void gestureEnded() {
    gesture.end();
  }
}
