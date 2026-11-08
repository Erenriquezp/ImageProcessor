package io.github.erenriquezp.rora.app.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.core.op.EditRecipe;
import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.Operation;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PureModelsTest {

  private static final Messages MESSAGES = Messages.of(Locale.of("es"));
  private static final OpRegistry REGISTRY = OpRegistry.load();
  private static final double EPS = 1e-9;

  private static OpSpec spec(String id) {
    Operation op = REGISTRY.require(id);
    return OpSpec.of(op, op.schema().defaults());
  }

  // ── Cortina ───────────────────────────────────────────────────────────

  @Test
  void theCurtainIsClampedToTheVisiblePartOfTheImage() {
    ViewTransform t = ViewTransform.fitted(1000, 500, 1000, 600, 1);
    double left = t.imageToScreen(0, 0).x();
    assertThat(Curtain.atScreenX(-500, t).position()).isCloseTo(0, within(EPS));
    assertThat(Curtain.atScreenX(1e6, t).position()).isCloseTo(1, within(EPS));
    assertThat(Curtain.CENTER.screenX(t))
        .isCloseTo(left + t.imageWidth() * t.scale() / 2, within(EPS));
  }

  @Test
  void theCurtainFollowsTheImageWhenZooming() {
    ViewTransform t = ViewTransform.fitted(1000, 500, 1000, 600, 1).zoomAt(500, 300, 2);
    assertThat(Curtain.CENTER.screenX(t)).isCloseTo(t.imageToScreen(500, 0).x(), within(EPS));
  }

  @Test
  void theCurtainIsGrabbedNearTheLine() {
    ViewTransform t = ViewTransform.fitted(1000, 500, 1000, 600, 1);
    double x = Curtain.CENTER.screenX(t);
    assertThat(Curtain.CENTER.isGrabbed(x + 7, t)).isTrue();
    assertThat(Curtain.CENTER.isGrabbed(x + 20, t)).isFalse();
  }

  @Test
  void theCurtainMovesWithTheKeyboardAndStaysInRange() {
    assertThat(Curtain.CENTER.nudged(1, false).position()).isCloseTo(0.52, within(EPS));
    assertThat(Curtain.CENTER.nudged(-1, true).position()).isCloseTo(0.498, within(EPS));
    assertThat(new Curtain(1).nudged(1, false).position()).isEqualTo(1);
  }

  // ── Controles ─────────────────────────────────────────────────────────

  private static ControlSpec control(String opId, String key) {
    return ControlSpec.from(REGISTRY.require(opId).schema(), MESSAGES, Set.of()).stream()
        .filter(c -> c.key().equals(key))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void aDecimalBecomesASliderThatResetsToNeutral() {
    ControlSpec.Slider s = (ControlSpec.Slider) control("adjust.basic", "exposure");
    assertThat(s.label()).isEqualTo("Exposición");
    assertThat(s.decimals()).isEqualTo(2);
    assertThat(s.resetDouble()).isZero();
  }

  @Test
  void anIntegerWithoutNeutralResetsToItsDefault() {
    ControlSpec.Slider s = (ControlSpec.Slider) control("tone.posterize", "levels");
    assertThat(s.integer()).isTrue();
    assertThat(s.resetValue())
        .isEqualTo(
            REGISTRY
                .require("tone.posterize")
                .schema()
                .get("levels")
                .neutralValue()
                .orElse(REGISTRY.require("tone.posterize").schema().get("levels").defaultValue()));
  }

  @Test
  void keyboardStepsAreRegularAndFine() {
    ControlSpec.Slider s = (ControlSpec.Slider) control("adjust.basic", "exposure");
    assertThat(s.stepped(0.5, 1, false)).isCloseTo(0.51, within(EPS));
    assertThat(s.stepped(0.5, -1, true)).isCloseTo(0.499, within(EPS));
    assertThat(s.stepped(5, 1, false)).isEqualTo(5);
  }

  @Test
  void integerStepsNeverLeaveIntegers() {
    ControlSpec.Slider s = (ControlSpec.Slider) control("raster.pixelate", "block");
    assertThat(s.stepped(8, 1, true)).isEqualTo(9);
    assertThat(s.normalize(8.4)).isEqualTo(8);
  }

  @Test
  void slidersFormatWithTheLocaleAndASign() {
    ControlSpec.Slider s = (ControlSpec.Slider) control("adjust.basic", "exposure");
    assertThat(s.format(0.35, Locale.of("es"))).isEqualTo("+0,35");
    assertThat(s.format(-1.5, Locale.of("es"))).isEqualTo("-1,50");
    assertThat(s.format(0, Locale.of("es"))).isEqualTo("0,00");
  }

  @Test
  void fewOptionsAreSegmentedAndManyAreAList() {
    assertThat(((ControlSpec.Choice) control("tone.equalize", "mode")).segmented()).isTrue();
    assertThat(((ControlSpec.Choice) control("color.matrix-preset", "preset")).segmented())
        .isFalse();
  }

  @Test
  void everyParameterKindHasItsControl() {
    assertThat(control("tone.equalize", "markBurned")).isInstanceOf(ControlSpec.Toggle.class);
    assertThat(control("color.recolor", "tone")).isInstanceOf(ControlSpec.ColorPick.class);
    assertThat(control("tone.posterize-channels", "channels"))
        .isInstanceOf(ControlSpec.Checks.class);
  }

  @Test
  void hiddenParametersAreNotGenerated() {
    List<ControlSpec> all =
        ControlSpec.from(REGISTRY.require("tone.equalize").schema(), MESSAGES, Set.of("markDark"));
    assertThat(all).extracting(ControlSpec::key).doesNotContain("markDark");
  }

  // ── Recorte ───────────────────────────────────────────────────────────

  @Test
  void aFreeCropDragsOneCorner() {
    CropModel c = CropModel.full(1.5).dragged(CropModel.Handle.SE, 0.5, 0.25);
    assertThat(c.right()).isCloseTo(0.5, within(EPS));
    assertThat(c.bottom()).isCloseTo(0.25, within(EPS));
    assertThat(c.left()).isZero();
  }

  @Test
  void aCropCannotCollapseOrLeaveTheImage() {
    CropModel c = CropModel.full(1.5).dragged(CropModel.Handle.SE, -3, -3);
    assertThat(c.width()).isGreaterThan(0);
    assertThat(c.height()).isGreaterThan(0);
    assertThat(CropModel.full(1.5).dragged(CropModel.Handle.NW, -1, -1).isFull()).isTrue();
  }

  @Test
  void aFixedRatioIsKeptInImagePixels() {
    CropModel c = CropModel.full(1.5).withRatio(1.0);
    assertThat(c.width() * 1.5 / c.height()).isCloseTo(1.0, within(1e-6));
    CropModel d = c.dragged(CropModel.Handle.SE, c.right() - 0.1, c.bottom());
    assertThat(d.width() * 1.5 / d.height()).isCloseTo(1.0, within(1e-6));
    assertThat(d.left()).isCloseTo(c.left(), within(1e-6));
  }

  @Test
  void movingARectangleDoesNotLeaveTheImage() {
    CropModel c = CropModel.full(1).dragged(CropModel.Handle.SE, 0.5, 0.5).movedBy(0.9, 0.9);
    assertThat(c.right()).isCloseTo(1, within(EPS));
    assertThat(c.width()).isCloseTo(0.5, within(EPS));
  }

  // ── Orden de la receta ────────────────────────────────────────────────

  @Test
  void adjustGoesRightAfterGeometry() {
    EditRecipe r = EditRecipe.EMPTY.add(spec("color.negative"));
    r = RecipeEdits.putGeometry(r, spec(RecipeEdits.CROP));
    r = RecipeEdits.putAdjust(r, spec(RecipeEdits.ADJUST));
    assertThat(r.steps())
        .extracting(OpSpec::opId)
        .containsExactly(RecipeEdits.CROP, RecipeEdits.ADJUST, "color.negative");
  }

  @Test
  void geometryKeepsItsFixedOrder() {
    EditRecipe r = RecipeEdits.putGeometry(EditRecipe.EMPTY, spec(RecipeEdits.RESIZE));
    r = RecipeEdits.putGeometry(r, spec(RecipeEdits.ROTATE));
    r = RecipeEdits.putGeometry(r, spec(RecipeEdits.CROP));
    assertThat(r.steps())
        .extracting(OpSpec::opId)
        .containsExactly(RecipeEdits.ROTATE, RecipeEdits.CROP, RecipeEdits.RESIZE);
  }

  @Test
  void geometryCannotBeDragged() {
    EditRecipe r = RecipeEdits.putGeometry(EditRecipe.EMPTY, spec(RecipeEdits.CROP));
    r = r.add(spec("color.negative")).add(spec("tone.posterize"));
    assertThat(RecipeEdits.move(r, 0, 2)).isSameAs(r);
    EditRecipe moved = RecipeEdits.move(r, 2, 0);
    assertThat(moved.steps().get(0).opId()).isEqualTo(RecipeEdits.CROP);
    assertThat(moved.steps().get(1).opId()).isEqualTo("tone.posterize");
  }
}
