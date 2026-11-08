package io.github.erenriquezp.rora.app.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.app.session.PipelineSupport.Step;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.domain.FilterType;
import io.github.erenriquezp.rora.service.LegacySteps;
import io.github.erenriquezp.rora.service.SourceFormat;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Cada filtro de la lista, traducido a un paso de receta y renderizado en FULL, es idéntico a la
 * llamada de la fachada que hacía la app ({@link ReferenceFilters}), en todos los tipos de imagen
 * cargada (incluida la convolución con su formato de origen).
 */
@Tag("equivalence")
class FilterSpecsTest {

  @Test
  void everyFilterMatchesTheFacadeOnEveryImageKind() {
    SplittableRandom rnd = new SplittableRandom(0x5EED_0070L);
    int cases = 0;
    for (FilterType type : FilterType.values()) {
      if (type == FilterType.NONE) {
        continue;
      }
      for (String kind : SessionFixtures.IMAGE_KINDS) {
        for (int k = 0; k < 3; k++) {
          int w = k == 0 ? 1 : 23;
          int h = k == 0 ? 1 : 17;
          BufferedImage img = SessionFixtures.image(kind, w, h, 0x5EED_7000L + cases);
          FilterParams p = SessionFixtures.randomParams(rnd);
          List<Step> steps = List.of(new Step(type, p));
          BufferedImage expected = PipelineSupport.reference(img, steps);
          PipelineSupport.Run run =
              PipelineSupport.pipeline(img, steps, true, OpContext.sequential());
          assertThat(PipelineSupport.same(expected, run.image()))
              .as(type + " " + kind + " " + w + "x" + h + " " + run.effective())
              .isTrue();
          cases++;
        }
      }
    }
    assertThat(cases).isEqualTo((FilterType.values().length - 1) * 6 * 3);
  }

  @Test
  void slidersEndingInTheSameFloatGiveTheSameStep() {
    OpRegistry reg = LegacySteps.registryWithLegacy(SourceFormat.NativeSource.NONE);
    FilterSpecs specs = new FilterSpecs(reg);
    FilterParams base = SessionFixtures.randomParams(new SplittableRandom(1));
    double a = 1.3;
    double b = Math.nextUp(1.3);
    assertThat((float) a).isEqualTo((float) b);
    FilterParams pa = withPointSaturation(base, (float) a);
    FilterParams pb = withPointSaturation(base, (float) b);
    assertThat(specs.of(FilterType.POINT_SATURATION, pa))
        .isEqualTo(specs.of(FilterType.POINT_SATURATION, pb));
    assertThat(specs.of(FilterType.NONE, pa)).isEmpty();
  }

  @Test
  void colorsOutsideEightBitsAreRejected() {
    assertThatThrownBy(() -> FilterSpecs.rgb8(256, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(FilterSpecs.rgb8(1, 2, 3)).isEqualTo(0xFF010203);
  }

  private static FilterParams withPointSaturation(FilterParams p, float v) {
    return new FilterParams(
        p.brightness(),
        p.saturation(),
        p.valueFactor(),
        p.retroLevels(),
        p.retro2Levels(),
        p.r2r(),
        p.r2g(),
        p.r2b(),
        p.grayQuantLevels(),
        p.threshold(),
        p.alpha(),
        p.tintR(),
        p.tintG(),
        p.tintB(),
        p.stretchMode(),
        p.kernel(),
        p.depthSource(),
        p.depthThreshold(),
        p.pixelBlockSize(),
        p.textureScale(),
        p.textureFilter(),
        p.alphaTestThreshold(),
        p.alphaTestLuminance(),
        p.multisampleSize(),
        p.stencilPattern(),
        p.fragmentBlendMode(),
        p.fragmentBlendAlpha(),
        p.logicOp(),
        p.logicMask(),
        p.planeR(),
        p.planeG(),
        p.planeB(),
        p.fogR(),
        p.fogG(),
        p.fogB(),
        p.equalizeMode(),
        p.gainR(),
        p.gainG(),
        p.gainB(),
        p.lerpFactor(),
        p.extrapolateFactor(),
        p.scale(),
        p.bias(),
        p.pointThreshold(),
        p.softThreshold(),
        v,
        p.hueDegrees(),
        p.colorSpace(),
        p.equalizeIntensity(),
        p.equalizeMarkBurned(),
        p.equalizeMarkDark());
  }
}
