package io.github.erenriquezp.rora.core.op;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** {@link OpSpec}, {@link EditRecipe} y {@link RecipeHistory}. */
class RecipeHistoryTest {

  private static final OpRegistry REGISTRY = OpRegistry.load();

  private static OpSpec sat(double f) {
    Operation op = REGISTRY.require("point.saturation");
    return OpSpec.of(op, Params.builder(op.schema()).set("factor", f).build());
  }

  private static OpSpec neg() {
    return OpSpec.defaults(REGISTRY.require("color.negative"));
  }

  // ── OpSpec ────────────────────────────────────────────────────────────────

  @Test
  void opSpecRejectsParamsOfAnotherSchema() {
    Operation sat = REGISTRY.require("point.saturation");
    Params other = REGISTRY.require("color.brightness").schema().defaults();
    assertThatThrownBy(() -> OpSpec.of(sat, other)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void opSpecEqualityAndCanonical() {
    assertThat(sat(1.5)).isEqualTo(sat(1.5)).hasSameHashCodeAs(sat(1.5)).isNotEqualTo(sat(1.4));
    assertThat(sat(1.5).canonical()).isEqualTo("point.saturation{factor=1.5}");
    assertThat(neg().withEnabled(false).canonical()).isEqualTo("!color.negative{}");
    OpSpec n = neg();
    assertThat(n.withEnabled(true)).isSameAs(n);
    assertThat(sat(1).withParams(sat(2).params())).isEqualTo(sat(2));
  }

  // ── EditRecipe ────────────────────────────────────────────────────────────

  @Test
  void recipeEditsAreImmutable() {
    EditRecipe r0 = EditRecipe.EMPTY;
    EditRecipe r1 = r0.add(sat(1.2));
    EditRecipe r2 = r1.add(neg());
    assertThat(r0.size()).isZero();
    assertThat(r1.steps()).containsExactly(sat(1.2));
    assertThat(r2.steps()).containsExactly(sat(1.2), neg());
    assertThat(r2.insert(0, sat(0.5)).steps()).containsExactly(sat(0.5), sat(1.2), neg());
    assertThat(r2.replace(1, sat(2)).steps()).containsExactly(sat(1.2), sat(2));
    assertThat(r2.remove(0).steps()).containsExactly(neg());
    assertThat(r2.remove(0).remove(0)).isSameAs(EditRecipe.EMPTY);
    assertThat(r2.move(0, 1).steps()).containsExactly(neg(), sat(1.2));
    assertThat(r2.setEnabled(0, false).activeSteps()).containsExactly(neg());
    assertThat(r2.prefix(1)).isEqualTo(r1);
    assertThat(r2.steps()).containsExactly(sat(1.2), neg());
    assertThatThrownBy(() -> r2.steps().add(neg()))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> r2.remove(2)).isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> r2.insert(3, neg())).isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  void recipeEqualityAndCanonical() {
    EditRecipe a = EditRecipe.EMPTY.add(sat(1.5)).add(neg());
    EditRecipe b = EditRecipe.of(List.of(sat(1.5), neg()));
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a.canonical()).isEqualTo("[point.saturation{factor=1.5};color.negative{}]");
    assertThat(a.toString()).contains(a.canonical());
    assertThat(a.get(1)).isEqualTo(neg());
    assertThat(EditRecipe.EMPTY.isEmpty()).isTrue();
  }

  // ── RecipeHistory ─────────────────────────────────────────────────────────

  @Test
  void undoRedo() {
    RecipeHistory h = new RecipeHistory(EditRecipe.EMPTY);
    EditRecipe r1 = EditRecipe.EMPTY.add(sat(1.2));
    EditRecipe r2 = r1.add(neg());
    h.record(r1, null);
    h.record(r2, null);
    assertThat(h.undoDepth()).isEqualTo(2);
    assertThat(h.undo()).isEqualTo(r1);
    assertThat(h.canRedo()).isTrue();
    assertThat(h.undo()).isEqualTo(EditRecipe.EMPTY);
    assertThat(h.canUndo()).isFalse();
    assertThat(h.undo()).isEqualTo(EditRecipe.EMPTY);
    assertThat(h.redo()).isEqualTo(r1);
    assertThat(h.redo()).isEqualTo(r2);
    assertThat(h.redo()).isEqualTo(r2);
    h.undo();
    h.record(r1.add(sat(0.1)), null);
    assertThat(h.canRedo()).isFalse();
    assertThat(h.redoDepth()).isZero();
  }

  @Test
  void recordingTheCurrentRecipeIsANoOp() {
    RecipeHistory h = new RecipeHistory(EditRecipe.EMPTY);
    h.record(EditRecipe.of(List.of()), null);
    assertThat(h.undoDepth()).isZero();
  }

  @Test
  void limitDropsOldestEntries() {
    RecipeHistory h = new RecipeHistory(EditRecipe.EMPTY, 3);
    EditRecipe r = EditRecipe.EMPTY;
    for (int i = 1; i <= 5; i++) {
      r = r.add(sat(i / 10.0));
      h.record(r, null);
    }
    assertThat(h.undoDepth()).isEqualTo(3);
    h.undo();
    h.undo();
    assertThat(h.undo().size()).isEqualTo(2);
    assertThat(h.canUndo()).isFalse();
    assertThat(new RecipeHistory(EditRecipe.EMPTY).limit()).isEqualTo(200);
    assertThatThrownBy(() -> new RecipeHistory(EditRecipe.EMPTY, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void oneDragIsOneEntry() {
    RecipeHistory h = new RecipeHistory(EditRecipe.EMPTY.add(sat(1)));
    Object drag = new Object();
    for (int i = 0; i < 50; i++) {
      h.record(EditRecipe.EMPTY.add(sat(1 + i / 100.0)), drag);
    }
    h.endCoalescing();
    assertThat(h.undoDepth()).isEqualTo(1);
    assertThat(h.current()).isEqualTo(EditRecipe.EMPTY.add(sat(1 + 49 / 100.0)));
    // Mismo key tras cerrar el gesto: entrada nueva.
    h.record(EditRecipe.EMPTY.add(sat(0.2)), drag);
    assertThat(h.undoDepth()).isEqualTo(2);
    assertThat(h.undo()).isEqualTo(EditRecipe.EMPTY.add(sat(1 + 49 / 100.0)));
    assertThat(h.undo()).isEqualTo(EditRecipe.EMPTY.add(sat(1)));
  }

  @Test
  void undoClosesTheGesture() {
    RecipeHistory h = new RecipeHistory(EditRecipe.EMPTY);
    Object drag = "drag-1";
    h.record(EditRecipe.EMPTY.add(sat(1.1)), drag);
    h.undo();
    h.record(EditRecipe.EMPTY.add(sat(1.2)), drag);
    assertThat(h.undoDepth()).isEqualTo(1);
  }

  @Test
  void resetClearsBothStacks() {
    RecipeHistory h = new RecipeHistory(EditRecipe.EMPTY);
    h.record(EditRecipe.EMPTY.add(neg()), null);
    h.undo();
    h.reset(EditRecipe.EMPTY);
    assertThat(h.canUndo()).isFalse();
    assertThat(h.canRedo()).isFalse();
    assertThat(h.current()).isEqualTo(EditRecipe.EMPTY);
  }

  /** Modelo de referencia con listas: secuencia aleatoria con semilla, mismos estados. */
  @Test
  void matchesAReferenceModelOnARandomSequence() {
    SplittableRandom rnd = new SplittableRandom(0x5EED_0003L);
    RecipeHistory h = new RecipeHistory(EditRecipe.EMPTY, 10);
    List<EditRecipe> undo = new ArrayList<>();
    List<EditRecipe> redo = new ArrayList<>();
    EditRecipe cur = EditRecipe.EMPTY;
    for (int i = 0; i < 5000; i++) {
      int action = rnd.nextInt(3);
      if (action == 0) {
        EditRecipe next = cur.add(sat(rnd.nextInt(5) / 4.0));
        h.record(next, null);
        undo.add(0, cur);
        if (undo.size() > 10) {
          undo.remove(10);
        }
        redo.clear();
        cur = next;
      } else if (action == 1) {
        h.undo();
        if (!undo.isEmpty()) {
          redo.add(0, cur);
          cur = undo.remove(0);
        }
      } else {
        h.redo();
        if (!redo.isEmpty()) {
          undo.add(0, cur);
          cur = redo.remove(0);
        }
      }
      assertThat(h.current()).isEqualTo(cur);
      assertThat(h.undoDepth()).isEqualTo(undo.size());
      assertThat(h.redoDepth()).isEqualTo(redo.size());
    }
  }
}
