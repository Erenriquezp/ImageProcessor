package io.github.erenriquezp.rora.app.viewmodel;

import io.github.erenriquezp.rora.app.model.ControlSpec;
import io.github.erenriquezp.rora.app.model.CropModel;
import io.github.erenriquezp.rora.app.model.FrameOverlayState;
import io.github.erenriquezp.rora.app.model.RecipeEdits;
import io.github.erenriquezp.rora.core.image.ProxyPolicy.Size;
import io.github.erenriquezp.rora.core.op.EditRecipe;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.Params;
import java.util.List;
import java.util.Set;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Herramienta de recorte y geometría. Mientras está abierta, el recorte existente no se aplica en
 * la vista, para poder editarlo sobre la imagen entera; Enter lo confirma y Esc lo descarta.
 */
public final class CropViewModel<F> implements ControlState.Sink, FrameEditor {

  /** Proporción ofrecida; {@code ratio 0} = libre y {@code -1} = la de la imagen. */
  public record RatioOption(String label, double ratio) {}

  private static final String TURNS = "turns";
  private static final String AXIS = "axis";
  private static final String DEGREES = "degrees";
  private static final String LONG_SIDE = "longSide";

  private final Env<F> env;
  private final GestureTracker gesture;
  private final ObjectProperty<CropModel> crop = new SimpleObjectProperty<>();
  private final ObjectProperty<FrameOverlayState> overlay = new SimpleObjectProperty<>();
  private final ControlState straighten;
  private final List<RatioOption> ratios;
  private final Runnable onClosed;
  private boolean active;

  CropViewModel(Env<F> env, GestureTracker gesture, Runnable onClosed) {
    this.env = env;
    this.gesture = gesture;
    this.onClosed = onClosed;
    Operation op = env.session().registry().require(RecipeEdits.STRAIGHTEN);
    ControlSpec.Slider base =
        (ControlSpec.Slider) ControlSpec.from(op.schema(), env.messages(), Set.of()).get(0);
    this.straighten =
        new ControlState(
            new ControlSpec.Slider(
                base.key(),
                env.messages().get("ui.crop.straighten"),
                base.min(),
                base.max(),
                base.step(),
                base.decimals(),
                base.integer(),
                base.resetValue()),
            0.0,
            this);
    this.ratios =
        List.of(
            new RatioOption(env.messages().get("ui.crop.ratio.free"), 0),
            new RatioOption(env.messages().get("ui.crop.ratio.original"), -1),
            new RatioOption(env.messages().format("ui.stack.ratio", 1, 1), 1),
            new RatioOption(env.messages().format("ui.stack.ratio", 4, 3), 4.0 / 3),
            new RatioOption(env.messages().format("ui.stack.ratio", 3, 2), 3.0 / 2),
            new RatioOption(env.messages().format("ui.stack.ratio", 16, 9), 16.0 / 9));
    crop.addListener(
        (o, was, now) -> overlay.set(now == null ? null : FrameOverlayState.editable(now)));
  }

  @Override
  public ReadOnlyObjectProperty<FrameOverlayState> overlayProperty() {
    return overlay;
  }

  @Override
  public void setFrame(CropModel frame) {
    setCrop(frame);
  }

  public ObjectProperty<CropModel> cropProperty() {
    return crop;
  }

  public ControlState straighten() {
    return straighten;
  }

  public List<RatioOption> ratios() {
    return ratios;
  }

  public boolean isActive() {
    return active;
  }

  private Operation op(String id) {
    return env.session().registry().require(id);
  }

  private double imageAspect() {
    Size s = env.session().outputSize();
    return (double) s.width() / s.height();
  }

  void activate() {
    if (active || !env.session().hasImage()) {
      return;
    }
    active = true;
    EditRecipe recipe = env.session().recipe();
    env.session().setExcludedOps(Set.of(RecipeEdits.CROP));
    int i = RecipeEdits.indexOf(recipe, RecipeEdits.CROP);
    CropModel model = CropModel.full(imageAspect());
    if (i >= 0) {
      Params p = recipe.steps().get(i).params();
      model =
          new CropModel(
              p.getDouble("left"),
              p.getDouble("top"),
              p.getDouble("right"),
              p.getDouble("bottom"),
              0,
              model.imageAspect());
    }
    crop.set(model);
    sync(recipe);
  }

  /** Enter: aplica el recorte como una entrada de deshacer. */
  public void commit() {
    if (!active) {
      return;
    }
    deactivate();
    CropModel c = crop.get();
    crop.set(null);
    onClosed.run();
    EditRecipe recipe = env.session().recipe();
    if (c.isFull()) {
      env.session().applyRecipe(RecipeEdits.removeOp(recipe, RecipeEdits.CROP), null);
      return;
    }
    Operation op = op(RecipeEdits.CROP);
    Params p =
        op.schema().defaults().toBuilder()
            .set("left", c.left())
            .set("top", c.top())
            .set("right", c.right())
            .set("bottom", c.bottom())
            .build();
    env.session().applyRecipe(RecipeEdits.putGeometry(recipe, OpSpec.of(op, p)), null);
  }

  /** Esc: se abandona la herramienta sin cambiar el recorte. */
  public void cancel() {
    if (active) {
      deactivate();
      crop.set(null);
      onClosed.run();
    }
  }

  private void deactivate() {
    active = false;
    env.session().setExcludedOps(Set.of());
  }

  public void setCrop(CropModel model) {
    crop.set(model);
  }

  /** Aplica la proporción elegida; la opción "original" usa la de la imagen. */
  public void chooseRatio(RatioOption option) {
    CropModel c = crop.get();
    if (c != null) {
      crop.set(c.withRatio(option.ratio() < 0 ? c.imageAspect() : option.ratio()));
    }
  }

  /** Cuarto de vuelta. Rotar invalida el recorte, porque cambia lo que significan sus bordes. */
  public void rotate(boolean clockwise) {
    EditRecipe recipe = env.session().recipe();
    int i = RecipeEdits.indexOf(recipe, RecipeEdits.ROTATE);
    int turns = i >= 0 ? recipe.steps().get(i).params().getInt(TURNS) : 0;
    applyGeometry(withTurns(recipe, Math.floorMod(turns + (clockwise ? 1 : -1), 4)));
  }

  private EditRecipe withTurns(EditRecipe recipe, int turns) {
    if (turns == 0) {
      return RecipeEdits.removeOp(recipe, RecipeEdits.ROTATE);
    }
    Operation op = op(RecipeEdits.ROTATE);
    return RecipeEdits.putGeometry(
        recipe, OpSpec.of(op, op.schema().defaults().toBuilder().set(TURNS, turns).build()));
  }

  /** Voltea; repetirlo lo quita, y voltear en el otro eje equivale a media vuelta. */
  public void flip(String axis) {
    EditRecipe recipe = env.session().recipe();
    int i = RecipeEdits.indexOf(recipe, RecipeEdits.FLIP);
    EditRecipe next;
    if (i < 0) {
      Operation op = op(RecipeEdits.FLIP);
      next =
          RecipeEdits.putGeometry(
              recipe, OpSpec.of(op, op.schema().defaults().toBuilder().set(AXIS, axis).build()));
    } else if (recipe.steps().get(i).params().getEnum(AXIS).equals(axis)) {
      next = recipe.remove(i);
    } else {
      EditRecipe without = recipe.remove(i);
      int r = RecipeEdits.indexOf(without, RecipeEdits.ROTATE);
      int turns = r >= 0 ? without.steps().get(r).params().getInt(TURNS) : 0;
      next = withTurns(without, Math.floorMod(turns + 2, 4));
    }
    applyGeometry(next);
  }

  /** Cambia el tamaño final; 0 lo quita. */
  public void resize(int longSide) {
    EditRecipe recipe = env.session().recipe();
    Operation op = op(RecipeEdits.RESIZE);
    env.session()
        .applyRecipe(
            longSide <= 0
                ? RecipeEdits.removeOp(recipe, RecipeEdits.RESIZE)
                : RecipeEdits.putGeometry(
                    recipe,
                    OpSpec.of(
                        op, op.schema().defaults().toBuilder().set(LONG_SIDE, longSide).build())),
            null);
  }

  private void applyGeometry(EditRecipe next) {
    env.session().applyRecipe(RecipeEdits.removeOp(next, RecipeEdits.CROP), null);
    if (active) {
      crop.set(CropModel.full(imageAspect()));
    }
  }

  @Override
  public void changed(ControlState control, boolean keyboard) {
    double degrees = ((Number) control.value()).doubleValue();
    EditRecipe recipe = env.session().recipe();
    Operation op = op(RecipeEdits.STRAIGHTEN);
    EditRecipe next =
        degrees == 0
            ? RecipeEdits.removeOp(recipe, RecipeEdits.STRAIGHTEN)
            : RecipeEdits.putGeometry(
                recipe,
                OpSpec.of(op, op.schema().defaults().toBuilder().set(DEGREES, degrees).build()));
    env.session().setEditingIndex(RecipeEdits.indexOf(next, RecipeEdits.STRAIGHTEN));
    env.session().applyRecipe(next, gesture.key(keyboard));
  }

  @Override
  public void gestureEnded() {
    gesture.end();
  }

  void sync(EditRecipe recipe) {
    int i = RecipeEdits.indexOf(recipe, RecipeEdits.STRAIGHTEN);
    straighten.sync(i >= 0 ? recipe.steps().get(i).params().getDouble(DEGREES) : 0.0);
  }
}
