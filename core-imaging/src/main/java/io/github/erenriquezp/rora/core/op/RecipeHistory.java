package io.github.erenriquezp.rora.core.op;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Historial de deshacer/rehacer por recetas (sin imágenes). No es seguro para hilos: lo usa un solo
 * hilo (el de la sesión de edición).
 *
 * <p>Coalescing: varias llamadas a {@link #record} con la misma {@code coalesceKey} no nula (p. ej.
 * el id de un arrastre de slider) forman <b>una</b> entrada; {@link #endCoalescing()} cierra el
 * gesto.
 */
public final class RecipeHistory {

  /** Tope por defecto de entradas de deshacer. */
  public static final int DEFAULT_LIMIT = 200;

  private final int limit;
  private final Deque<EditRecipe> undo = new ArrayDeque<>();
  private final Deque<EditRecipe> redo = new ArrayDeque<>();
  private EditRecipe current;
  private Object openKey;

  public RecipeHistory(EditRecipe initial, int limit) {
    if (limit < 1) {
      throw new IllegalArgumentException("limit=" + limit);
    }
    this.current = Objects.requireNonNull(initial, "initial");
    this.limit = limit;
  }

  public RecipeHistory(EditRecipe initial) {
    this(initial, DEFAULT_LIMIT);
  }

  public EditRecipe current() {
    return current;
  }

  public int limit() {
    return limit;
  }

  /**
   * Pasa a {@code next}. No hace nada si es igual a la actual. Si {@code coalesceKey} no es nula e
   * igual a la del gesto abierto, sustituye la actual sin crear entrada nueva. Vacía el rehacer.
   */
  public void record(EditRecipe next, Object coalesceKey) {
    Objects.requireNonNull(next, "next");
    if (next.equals(current)) {
      return;
    }
    boolean coalesce = coalesceKey != null && coalesceKey.equals(openKey);
    if (coalesce && next.equals(undo.peek())) {
      // El gesto vuelve al estado de partida: no deja rastro en el historial.
      undo.pop();
      current = next;
      openKey = null;
      redo.clear();
      return;
    }
    if (!coalesce) {
      undo.push(current);
      while (undo.size() > limit) {
        undo.removeLast();
      }
    }
    current = next;
    openKey = coalesceKey;
    redo.clear();
  }

  /** Cierra el gesto en curso: la siguiente {@link #record} crea una entrada nueva. */
  public void endCoalescing() {
    openKey = null;
  }

  public boolean canUndo() {
    return !undo.isEmpty();
  }

  public boolean canRedo() {
    return !redo.isEmpty();
  }

  /** Número de entradas que se pueden deshacer. */
  public int undoDepth() {
    return undo.size();
  }

  public int redoDepth() {
    return redo.size();
  }

  /** Deshace y devuelve la receta actual; sin efecto si no hay nada que deshacer. */
  public EditRecipe undo() {
    openKey = null;
    if (!undo.isEmpty()) {
      redo.push(current);
      current = undo.pop();
    }
    return current;
  }

  /** Rehace y devuelve la receta actual; sin efecto si no hay nada que rehacer. */
  public EditRecipe redo() {
    openKey = null;
    if (!redo.isEmpty()) {
      undo.push(current);
      current = redo.pop();
    }
    return current;
  }

  /** Vacía deshacer y rehacer y fija {@code base} como actual. */
  public void reset(EditRecipe base) {
    current = Objects.requireNonNull(base, "base");
    undo.clear();
    redo.clear();
    openKey = null;
  }
}
