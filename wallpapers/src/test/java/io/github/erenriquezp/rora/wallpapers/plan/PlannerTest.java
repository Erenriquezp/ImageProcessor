package io.github.erenriquezp.rora.wallpapers.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import io.github.erenriquezp.rora.core.io.FormatId;
import io.github.erenriquezp.rora.wallpapers.geom.Fit;
import io.github.erenriquezp.rora.wallpapers.geom.FrameRect;
import io.github.erenriquezp.rora.wallpapers.plan.PlannedTarget.Warning;
import io.github.erenriquezp.rora.wallpapers.plan.Target.FitMode;
import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset;
import io.github.erenriquezp.rora.wallpapers.preset.PresetCatalog;
import io.github.erenriquezp.rora.wallpapers.render.FillSpec;
import io.github.erenriquezp.rora.wallpapers.subject.Subject;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlannerTest {

  static Target preset(String id) {
    return Target.of(
        PresetCatalog.builtIns().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow());
  }

  static Subject face(double x, double y, double w, double h) {
    return new Subject(x, y, w, h, 0.9, Subject.Kind.FACE, Optional.empty());
  }

  @Test
  void landscapeToPhoneIsCroppedWhenNothingIsLost() {
    // 16:9 hacia 1080x2400: se pierde el 75 %, por debajo del 80 %, y no hay caras.
    PlannedTarget p = Planner.plan(3840, 2160, List.of(), preset("phone-1080-2400"));
    assertThat(p.resolved()).isEqualTo(FitMode.COVER);
    assertThat(p.lostArea()).isCloseTo(1 - 972.0 / 3840, offset(1e-12));
    assertThat(p.warnings()).containsOnly(Warning.APPROXIMATE_PRESET);
  }

  @Test
  void panoramasAreContainedWithFill() {
    PlannedTarget p = Planner.plan(8000, 1200, List.of(), preset("phone-1179-2556"));
    assertThat(p.resolved()).isEqualTo(FitMode.CONTAIN);
    assertThat(p.content()).isEqualTo(Fit.contain(8000, 1200, 1179, 2556));
    assertThat(p.frame()).isEqualTo(new FrameRect(0, 0, 8000, 1200));
    assertThat(p.warnings()).contains(Warning.FILLED);
    assertThat(p.filled()).isTrue();
  }

  @Test
  void aGroupThatCannotFitGoesToContain() {
    List<Subject> six =
        List.of(
            face(0.18, 0.18, 0.05, 0.1),
            face(0.32, 0.2, 0.05, 0.1),
            face(0.46, 0.2, 0.05, 0.1),
            face(0.51, 0.18, 0.05, 0.1),
            face(0.66, 0.19, 0.05, 0.1),
            face(0.75, 0.19, 0.05, 0.1));
    PlannedTarget auto = Planner.plan(3840, 2160, six, preset("phone-1179-2556"));
    assertThat(auto.resolved()).isEqualTo(FitMode.CONTAIN);

    PlannedTarget forced =
        Planner.plan(3840, 2160, six, preset("phone-1179-2556").withFit(FitMode.COVER));
    assertThat(forced.resolved()).isEqualTo(FitMode.COVER);
    assertThat(forced.warnings()).contains(Warning.SUBJECT_CUT);
  }

  @Test
  void badgesFollowTheEffectiveScale() {
    assertThat(QualityBadge.of(1.15)).isEqualTo(QualityBadge.EXCELLENT);
    assertThat(QualityBadge.of(1.16)).isEqualTo(QualityBadge.ACCEPTABLE);
    assertThat(QualityBadge.of(2.0)).isEqualTo(QualityBadge.ACCEPTABLE);
    assertThat(QualityBadge.of(2.01)).isEqualTo(QualityBadge.BLURRY);

    PlannedTarget p = Planner.plan(1155, 866, List.of(), preset("pc-4k"));
    assertThat(p.effectiveScale()).isCloseTo(3840 / 1155.0, offset(1e-9));
    assertThat(p.badge()).isEqualTo(QualityBadge.BLURRY);
    assertThat(p.warnings()).contains(Warning.BLURRY);
  }

  /** Casos de la Puerta 0 (g). */
  @Test
  void requiredEnhanceScaleIsComputedOnceForTheWorstTarget() {
    WallpaperPlan a =
        Planner.plan(1155, 866, List.of(), List.of(preset("pc-4k"), preset("pc-fhd")));
    assertThat(a.requiredEnhanceScale()).hasValue(4);

    WallpaperPlan b = Planner.plan(650, 860, List.of(), List.of(preset("pc-4k")));
    assertThat(b.targets().getFirst().effectiveScale()).isCloseTo(5.9, offset(0.01));
    assertThat(b.requiredEnhanceScale()).hasValue(4);
    assertThat(b.targets().getFirst().effectiveScale() / 4).isCloseTo(1.48, offset(0.01));

    WallpaperPlan c = Planner.plan(2600, 1500, List.of(), List.of(preset("pc-4k")));
    assertThat(c.targets().getFirst().effectiveScale()).isCloseTo(1.477, offset(0.01));
    assertThat(c.requiredEnhanceScale()).isEmpty();

    WallpaperPlan d = Planner.plan(2000, 1125, List.of(), List.of(preset("pc-4k")));
    assertThat(d.requiredEnhanceScale()).hasValue(2);

    assertThat(Planner.plan(6000, 4000, List.of(), List.of(preset("pc-4k"))).requiredEnhanceScale())
        .isEmpty();
  }

  @Test
  void aManualFrameWinsAndIsClampedIntoTheSource() {
    FrameRect cover = Fit.cover(4000, 3000, 16, 9);
    FrameRect manual =
        new FrameRect(-100, 500, cover.w() * 0.5, cover.h() * 0.5).normalized(4000, 3000);
    Target t = preset("pc-4k").withFit(FitMode.CONTAIN).withManualFrame(Optional.of(manual));

    PlannedTarget p = Planner.plan(4000, 3000, List.of(), t);

    assertThat(p.resolved()).isEqualTo(FitMode.COVER);
    assertThat(p.frame().x()).isZero();
    assertThat(p.frame().w()).isCloseTo(cover.w() * 0.5, offset(1e-6));
    assertThat(p.effectiveScale()).isCloseTo(3840 / (cover.w() * 0.5), offset(1e-9));
  }

  @Test
  void aManualFrameWithTheWrongProportionIsDropped() {
    Target t = preset("pc-4k").withManualFrame(Optional.of(new FrameRect(0, 0, 0.5, 0.5)));
    PlannedTarget p = Planner.plan(3000, 3000, List.of(), t);
    assertThat(p.warnings()).contains(Warning.MANUAL_RESET);
    assertThat(p.frame()).isEqualTo(Fit.cover(3000, 3000, 3840, 2160));
  }

  @Test
  void warnsWhenAFaceSitsUnderTheClock() {
    // Retrato vertical con la cara arriba: en el móvil cae bajo el reloj (zona 7 %–27 % del alto).
    Subject high = face(0.4, 0.12, 0.2, 0.1);
    PlannedTarget p = Planner.plan(1179, 2556, List.of(high), preset("phone-1179-2556"));
    assertThat(p.warnings()).contains(Warning.SAFE_ZONE_OVERLAP);
    Subject low = face(0.4, 0.5, 0.2, 0.1);
    assertThat(Planner.plan(1179, 2556, List.of(low), preset("phone-1179-2556")).warnings())
        .doesNotContain(Warning.SAFE_ZONE_OVERLAP);
  }

  @Test
  void autoFormatUsesPngOnlyForSmoothFills() {
    PlannedTarget cover = Planner.plan(3840, 2160, List.of(), preset("pc-4k"));
    PlannedTarget blur = Planner.plan(8000, 1200, List.of(), preset("phone-1179-2556"));
    PlannedTarget mirror =
        Planner.plan(
            8000,
            1200,
            List.of(),
            preset("phone-1179-2556").withFill(new FillSpec(FillSpec.Kind.MIRROR, 0, 0)));
    assertThat(OutputFormat.AUTO.resolve(cover)).isEqualTo(FormatId.JPEG);
    assertThat(OutputFormat.AUTO.resolve(blur)).isEqualTo(FormatId.PNG);
    assertThat(OutputFormat.AUTO.resolve(mirror)).isEqualTo(FormatId.JPEG);
    assertThat(OutputFormat.PNG.resolve(cover)).isEqualTo(FormatId.PNG);
    assertThat(OutputFormat.dithers(FormatId.JPEG)).isFalse();
  }

  @Test
  void rotatingSwapsTheSidesAndForgetsTheManualFrame() {
    Target t =
        preset("tablet-1668-2388").withManualFrame(Optional.of(new FrameRect(0, 0, 0.5, 0.5)));
    Target r = t.rotated();
    assertThat(r.width()).isEqualTo(2388);
    assertThat(r.height()).isEqualTo(1668);
    assertThat(r.manualFrame()).isEmpty();
    assertThat(r.id()).isEqualTo("tablet-1668-2388@rot");
    assertThat(r.rotated().id()).isEqualTo("tablet-1668-2388");
  }

  @Test
  void everyPresetPlansInsideTheSourceForTheMatrix() {
    int[][] sources = {{1, 1}, {7, 1}, {640, 480}, {3000, 2000}, {8000, 1200}, {1200, 8000}};
    for (int[] s : sources) {
      for (DevicePreset d : PresetCatalog.builtIns()) {
        PlannedTarget p = Planner.plan(s[0], s[1], List.of(), Target.of(d));
        assertThat(p.frame().insideOf(s[0], s[1])).isTrue();
        assertThat(p.content().right()).isLessThanOrEqualTo(d.width());
        assertThat(p.content().bottom()).isLessThanOrEqualTo(d.height());
      }
    }
  }
}
