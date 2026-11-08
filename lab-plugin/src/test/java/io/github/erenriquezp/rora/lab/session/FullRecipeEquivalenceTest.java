package io.github.erenriquezp.rora.lab.session;

import io.github.erenriquezp.rora.app.session.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.lab.session.PipelineSupport.Step;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.domain.FilterType;
import io.github.erenriquezp.rora.service.LegacySteps;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Recetas con semilla que mezclan pasos del núcleo y legacy: el render FULL es idéntico a encadenar
 * "Aplicar filtro" con la fachada, como hacía la app. Única excepción documentada: una convolución
 * cuya entrada es la salida de otra convolución sobre gris ({@code ConvolutionFormatTest}).
 */
@Tag("equivalence")
class FullRecipeEquivalenceTest {

  private static final TileExecutor SEVEN = TileExecutor.create(7);
  private static final Set<String> LOSSY_CHAIN = Set.of("byte-gray", "ushort-gray");

  @AfterAll
  static void close() {
    SEVEN.close();
  }

  private static FilterType randomType(SplittableRandom rnd) {
    // Más convoluciones y ecualizaciones (formato de origen y "misma instancia").
    int k = rnd.nextInt(10);
    if (k == 0) {
      return FilterType.CONVOLUTION;
    }
    if (k == 1) {
      return FilterType.HISTOGRAM_EQUALIZE;
    }
    FilterType[] all = FilterType.values();
    FilterType t;
    do {
      t = all[rnd.nextInt(all.length)];
    } while (t == FilterType.NONE);
    return t;
  }

  @Test
  void seededMixedRecipesMatchTheAppChain() {
    SplittableRandom rnd = new SplittableRandom(0x5EED_0080L);
    int compared = 0;
    int deviations = 0;
    int legacySteps = 0;
    for (int t = 0; t < 200; t++) {
      String kind = SessionFixtures.IMAGE_KINDS[t % SessionFixtures.IMAGE_KINDS.length];
      BufferedImage img =
          SessionFixtures.image(kind, 1 + rnd.nextInt(40), 1 + rnd.nextInt(30), 0x5EED_8000L + t);
      List<Step> steps = new ArrayList<>();
      int len = 1 + rnd.nextInt(8);
      for (int j = 0; j < len; j++) {
        // "Aplicar" consolida sin las marcas de la ecualización.
        steps.add(new Step(randomType(rnd), SessionFixtures.randomParams(rnd).withNoHighlights()));
      }
      PipelineSupport.Run run =
          PipelineSupport.pipeline(img, steps, t % 2 == 0, OpContext.of(SEVEN));
      boolean deviation = false;
      for (OpSpec s : run.effective()) {
        if (s.opId().startsWith("legacy.")) {
          legacySteps++;
        }
        if (s.opId().equals(LegacySteps.CONVOLUTION)
            && LOSSY_CHAIN.contains(s.params().getEnum("sourceFormat"))) {
          deviation = true;
        }
      }
      if (deviation) {
        deviations++;
        continue;
      }
      BufferedImage expected = PipelineSupport.reference(img, steps);
      assertThat(PipelineSupport.same(expected, run.image()))
          .as("receta " + t + " " + kind + " " + run.effective())
          .isTrue();
      compared++;
    }
    assertThat(compared).isGreaterThan(190);
    assertThat(legacySteps).isGreaterThan(100);
    assertThat(deviations).isLessThan(10);
  }
}
