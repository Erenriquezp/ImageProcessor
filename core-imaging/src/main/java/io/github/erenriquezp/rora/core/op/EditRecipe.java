package io.github.erenriquezp.rora.core.op;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Receta de edición: lista ordenada e inmutable de {@link OpSpec}. Es el documento del usuario; el
 * historial guarda recetas, nunca imágenes. Cada método de edición devuelve una receta nueva.
 */
public final class EditRecipe {

  /** Receta vacía (la imagen original). */
  public static final EditRecipe EMPTY = new EditRecipe(List.of());

  private final List<OpSpec> steps;

  private EditRecipe(List<OpSpec> steps) {
    this.steps = List.copyOf(steps);
  }

  public static EditRecipe of(List<OpSpec> steps) {
    return steps.isEmpty() ? EMPTY : new EditRecipe(steps);
  }

  public List<OpSpec> steps() {
    return steps;
  }

  public int size() {
    return steps.size();
  }

  public boolean isEmpty() {
    return steps.isEmpty();
  }

  public OpSpec get(int i) {
    return steps.get(i);
  }

  /** Pasos activos, en orden: lo que se renderiza. */
  public List<OpSpec> activeSteps() {
    return steps.stream().filter(OpSpec::enabled).toList();
  }

  public EditRecipe add(OpSpec s) {
    return insert(steps.size(), s);
  }

  public EditRecipe insert(int i, OpSpec s) {
    Objects.requireNonNull(s, "step");
    Objects.checkIndex(i, steps.size() + 1);
    List<OpSpec> l = new ArrayList<>(steps);
    l.add(i, s);
    return new EditRecipe(l);
  }

  public EditRecipe replace(int i, OpSpec s) {
    Objects.requireNonNull(s, "step");
    Objects.checkIndex(i, steps.size());
    List<OpSpec> l = new ArrayList<>(steps);
    l.set(i, s);
    return new EditRecipe(l);
  }

  public EditRecipe remove(int i) {
    Objects.checkIndex(i, steps.size());
    List<OpSpec> l = new ArrayList<>(steps);
    l.remove(i);
    return of(l);
  }

  public EditRecipe setEnabled(int i, boolean enabled) {
    return replace(i, get(i).withEnabled(enabled));
  }

  /** Mueve el paso {@code from} a la posición {@code to} (índices de la receta resultante). */
  public EditRecipe move(int from, int to) {
    Objects.checkIndex(from, steps.size());
    Objects.checkIndex(to, steps.size());
    List<OpSpec> l = new ArrayList<>(steps);
    l.add(to, l.remove(from));
    return new EditRecipe(l);
  }

  /** Los {@code n} primeros pasos. */
  public EditRecipe prefix(int n) {
    Objects.checkFromToIndex(0, n, steps.size());
    return of(steps.subList(0, n));
  }

  /** Forma canónica estable: {@code [paso1;paso2]}. */
  public String canonical() {
    StringJoiner j = new StringJoiner(";", "[", "]");
    steps.forEach(s -> j.add(s.canonical()));
    return j.toString();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof EditRecipe r && r.steps.equals(steps);
  }

  @Override
  public int hashCode() {
    return steps.hashCode();
  }

  @Override
  public String toString() {
    return "EditRecipe" + canonical();
  }
}
