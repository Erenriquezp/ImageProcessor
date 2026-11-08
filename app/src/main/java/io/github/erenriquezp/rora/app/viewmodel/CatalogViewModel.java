package io.github.erenriquezp.rora.app.viewmodel;

import io.github.erenriquezp.rora.app.catalog.Catalog;
import io.github.erenriquezp.rora.core.op.EditRecipe;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.Params;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** Buscador del catálogo; elegir un filtro añade un paso editable y lo selecciona. */
public final class CatalogViewModel<F> {

  private final Env<F> env;
  private final StackViewModel<F> stack;
  private final StringProperty query = new SimpleStringProperty("");
  private final List<Catalog.Entry> entries;
  private final ObservableList<Catalog.Entry> results = FXCollections.observableArrayList();

  CatalogViewModel(Env<F> env, StackViewModel<F> stack) {
    this.env = env;
    this.stack = stack;
    List<Catalog.Entry> all = new ArrayList<>(Catalog.ENTRIES);
    all.addAll(env.labEntries());
    this.entries = List.copyOf(all);
    query.subscribe(q -> results.setAll(Catalog.search(q, env.messages(), entries)));
  }

  public StringProperty queryProperty() {
    return query;
  }

  public ObservableList<Catalog.Entry> results() {
    return results;
  }

  /** Añade el filtro al final de la pila; una entrada de deshacer. */
  public void add(Catalog.Entry entry) {
    if (!env.session().hasImage()) {
      return;
    }
    Operation op = env.session().registry().require(entry.opId());
    Params.Builder b = op.schema().defaults().toBuilder();
    for (Map.Entry<String, Object> fixed : entry.fixedParams().entrySet()) {
      b.set(fixed.getKey(), fixed.getValue());
    }
    EditRecipe next = env.session().recipe().add(OpSpec.of(op, b.build()));
    env.session().applyRecipe(next, null);
    stack.select(next.size() - 1);
  }
}
