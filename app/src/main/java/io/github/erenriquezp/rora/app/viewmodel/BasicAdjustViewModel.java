package io.github.erenriquezp.rora.app.viewmodel;

import io.github.erenriquezp.rora.app.model.ControlSpec;
import io.github.erenriquezp.rora.app.model.RecipeEdits;
import io.github.erenriquezp.rora.core.op.EditRecipe;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Panel de Ajustes básicos. {@code adjust.basic} es un único paso: aparece al mover el primer
 * control y desaparece cuando todo vuelve al neutro. El grupo Detalle controla la cantidad de un
 * paso {@code detail.unsharp-mask}.
 */
public final class BasicAdjustViewModel<F> implements ControlState.Sink {

  /** Grupo de controles con su título. */
  public record Group(String titleKey, List<ControlState> controls) {}

  private static final List<String> LIGHT =
      List.of("exposure", "contrast", "highlights", "shadows", "whites", "blacks");
  private static final List<String> COLOR =
      List.of("temperature", "tint", "vibrance", "saturation");
  private static final String AMOUNT = "amount";

  private final Env<F> env;
  private final GestureTracker gesture;
  private final Operation adjust;
  private final Operation sharpen;
  private final List<ControlState> adjustControls = new ArrayList<>();
  private final ControlState sharpenAmount;
  private final List<Group> groups;

  BasicAdjustViewModel(Env<F> env, GestureTracker gesture) {
    this.env = env;
    this.gesture = gesture;
    this.adjust = env.session().registry().require(RecipeEdits.ADJUST);
    this.sharpen = env.session().registry().require(RecipeEdits.SHARPEN);
    ParamSchema schema = adjust.schema();
    List<ControlSpec> specs = ControlSpec.from(schema, env.messages(), Set.of());
    Params neutral = schema.neutral().orElseThrow();
    List<ControlState> light = states(specs, LIGHT, neutral);
    List<ControlState> color = states(specs, COLOR, neutral);
    adjustControls.addAll(light);
    adjustControls.addAll(color);

    ControlSpec.Slider amount =
        (ControlSpec.Slider)
            ControlSpec.from(sharpen.schema(), env.messages(), Set.of("radius", "threshold"))
                .get(0);
    ControlSpec.Slider labelled =
        new ControlSpec.Slider(
            amount.key(),
            env.messages().get("ui.detail.sharpen"),
            amount.min(),
            amount.max(),
            amount.step(),
            amount.decimals(),
            amount.integer(),
            0.0);
    this.sharpenAmount = new ControlState(labelled, 0.0, this);
    this.groups =
        List.of(
            new Group("ui.group.light", light),
            new Group("ui.group.color", color),
            new Group("ui.group.detail", List.of(sharpenAmount)));
  }

  private List<ControlState> states(List<ControlSpec> specs, List<String> keys, Params neutral) {
    List<ControlState> out = new ArrayList<>();
    for (String key : keys) {
      ControlSpec spec =
          specs.stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow();
      out.add(new ControlState(spec, neutral.get(key), this));
    }
    return out;
  }

  public List<Group> groups() {
    return groups;
  }

  @Override
  public void changed(ControlState control, boolean keyboard) {
    Object key = gesture.key(keyboard);
    EditRecipe recipe = env.session().recipe();
    if (control == sharpenAmount) {
      applySharpen(recipe, key);
    } else {
      applyAdjust(recipe, key);
    }
  }

  private void applyAdjust(EditRecipe recipe, Object key) {
    Params.Builder b = adjust.schema().defaults().toBuilder();
    for (ControlState c : adjustControls) {
      b.set(c.spec().key(), c.value());
    }
    Params params = b.build();
    EditRecipe next =
        params.equals(adjust.schema().neutral().orElseThrow())
            ? RecipeEdits.removeOp(recipe, RecipeEdits.ADJUST)
            : RecipeEdits.putAdjust(recipe, OpSpec.of(adjust, params));
    env.session().setEditingIndex(RecipeEdits.indexOf(next, RecipeEdits.ADJUST));
    env.session().applyRecipe(next, key);
  }

  private void applySharpen(EditRecipe recipe, Object key) {
    double amount = ((Number) sharpenAmount.value()).doubleValue();
    int existing = RecipeEdits.indexOf(recipe, RecipeEdits.SHARPEN);
    Params base =
        existing >= 0
            ? recipe.steps().get(existing).params().withSchema(sharpen.schema())
            : sharpen.schema().neutral().orElseThrow();
    EditRecipe next =
        amount == 0
            ? RecipeEdits.removeOp(recipe, RecipeEdits.SHARPEN)
            : RecipeEdits.putLast(
                recipe, OpSpec.of(sharpen, base.toBuilder().set(AMOUNT, amount).build()));
    env.session().setEditingIndex(RecipeEdits.indexOf(next, RecipeEdits.SHARPEN));
    env.session().applyRecipe(next, key);
  }

  @Override
  public void gestureEnded() {
    gesture.end();
  }

  /** Refleja la receta en los controles (deshacer, abrir otra imagen…). */
  void sync(EditRecipe recipe) {
    int adjustIndex = RecipeEdits.indexOf(recipe, RecipeEdits.ADJUST);
    Params params =
        adjustIndex >= 0
            ? recipe.steps().get(adjustIndex).params().withSchema(adjust.schema())
            : adjust.schema().neutral().orElseThrow();
    for (ControlState c : adjustControls) {
      c.sync(params.get(c.spec().key()));
    }
    int sharpenIndex = RecipeEdits.indexOf(recipe, RecipeEdits.SHARPEN);
    sharpenAmount.sync(
        sharpenIndex >= 0
            ? recipe.steps().get(sharpenIndex).params().withSchema(sharpen.schema()).getDouble(AMOUNT)
            : 0.0);
  }
}
