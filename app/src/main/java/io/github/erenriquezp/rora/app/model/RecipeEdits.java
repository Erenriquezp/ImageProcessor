package io.github.erenriquezp.rora.app.model;

import io.github.erenriquezp.rora.core.op.EditRecipe;
import io.github.erenriquezp.rora.core.op.OpSpec;
import java.util.List;

/**
 * Reglas de orden de la receta: primero la geometría, en un orden fijo; después el resto,
 * reordenable. La geometría no se arrastra porque de ella dependen los parámetros en píxeles de los
 * pasos posteriores.
 */
public final class RecipeEdits {

  public static final String ADJUST = "adjust.basic";
  public static final String SHARPEN = "detail.unsharp-mask";
  public static final String ROTATE = "transform.rotate90";
  public static final String FLIP = "transform.flip";
  public static final String STRAIGHTEN = "transform.straighten";
  public static final String CROP = "transform.crop";
  public static final String RESIZE = "transform.resize";

  private static final List<String> GEOMETRY_ORDER =
      List.of(ROTATE, FLIP, STRAIGHTEN, CROP, RESIZE);

  private RecipeEdits() {}

  public static boolean isGeometry(String opId) {
    return GEOMETRY_ORDER.contains(opId);
  }

  /** Índice del primer paso que no es geometría. */
  public static int geometryEnd(EditRecipe recipe) {
    List<OpSpec> steps = recipe.steps();
    int i = 0;
    while (i < steps.size() && isGeometry(steps.get(i).opId())) {
      i++;
    }
    return i;
  }

  /** Índice del primer paso con {@code opId}, o {@code -1}. */
  public static int indexOf(EditRecipe recipe, String opId) {
    List<OpSpec> steps = recipe.steps();
    for (int i = 0; i < steps.size(); i++) {
      if (steps.get(i).opId().equals(opId)) {
        return i;
      }
    }
    return -1;
  }

  /** Añade {@code step} al principio de la zona reordenable, o lo actualiza si ya existe. */
  public static EditRecipe putAdjust(EditRecipe recipe, OpSpec step) {
    int i = indexOf(recipe, step.opId());
    return i >= 0 ? recipe.replace(i, step) : recipe.insert(geometryEnd(recipe), step);
  }

  /** Añade {@code step} al final, o lo actualiza si ya existe (un único paso por operación). */
  public static EditRecipe putLast(EditRecipe recipe, OpSpec step) {
    int i = indexOf(recipe, step.opId());
    return i >= 0 ? recipe.replace(i, step) : recipe.add(step);
  }

  /** Coloca un paso de geometría en su sitio; sustituye al que hubiera de la misma operación. */
  public static EditRecipe putGeometry(EditRecipe recipe, OpSpec step) {
    int existing = indexOf(recipe, step.opId());
    if (existing >= 0) {
      return recipe.replace(existing, step);
    }
    int rank = GEOMETRY_ORDER.indexOf(step.opId());
    List<OpSpec> steps = recipe.steps();
    int at = 0;
    while (at < steps.size()
        && isGeometry(steps.get(at).opId())
        && GEOMETRY_ORDER.indexOf(steps.get(at).opId()) <= rank) {
      at++;
    }
    return recipe.insert(at, step);
  }

  /** Quita el paso con {@code opId}, si lo hay. */
  public static EditRecipe removeOp(EditRecipe recipe, String opId) {
    int i = indexOf(recipe, opId);
    return i >= 0 ? recipe.remove(i) : recipe;
  }

  /** Mueve un paso dentro de la zona reordenable; pedir otra cosa no tiene efecto. */
  public static EditRecipe move(EditRecipe recipe, int from, int to) {
    int end = geometryEnd(recipe);
    if (from < end || from >= recipe.size() || from == to) {
      return recipe;
    }
    return recipe.move(from, Math.max(end, Math.min(recipe.size() - 1, to)));
  }
}
