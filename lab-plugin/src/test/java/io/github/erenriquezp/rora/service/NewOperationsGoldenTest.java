package io.github.erenriquezp.rora.service;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.testsupport.GoldenSupport;
import io.github.erenriquezp.rora.testsupport.TestImages;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

/**
 * Goldens exactos de las operaciones nuevas de la fase 3, que no tienen referencia heredada. Se
 * generan solo con {@code -Dgolden.update=true}.
 */
@Tag("golden")
class NewOperationsGoldenTest {

  private record Case(String id, String name, Object... values) {}

  private static final List<Case> CASES =
      List.of(
          new Case("adjust.basic", "neutral"),
          new Case(
              "adjust.basic",
              "typical",
              "exposure",
              0.5,
              "contrast",
              20.0,
              "highlights",
              -30.0,
              "shadows",
              25.0,
              "temperature",
              15.0,
              "vibrance",
              20.0),
          new Case(
              "adjust.basic",
              "extreme",
              "exposure",
              5.0,
              "contrast",
              100.0,
              "whites",
              100.0,
              "blacks",
              -100.0,
              "saturation",
              100.0),
          new Case("blur.gaussian", "neutral", "radius", 0.0),
          new Case("blur.gaussian", "radius-3", "radius", 3.0),
          new Case("detail.unsharp-mask", "neutral", "amount", 0.0),
          new Case("detail.unsharp-mask", "typical", "amount", 120.0, "radius", 1.5),
          new Case("detail.unsharp-mask", "threshold", "amount", 200.0, "threshold", 16),
          new Case(
              "transform.crop", "center", "left", 0.25, "top", 0.25, "right", 0.75, "bottom", 0.75),
          new Case("transform.flip", "horizontal", "axis", "horizontal"),
          new Case("transform.flip", "vertical", "axis", "vertical"),
          new Case("transform.rotate90", "one-turn", "turns", 1),
          new Case("transform.straighten", "degrees-7", "degrees", 7.5),
          new Case("transform.resize", "reduce", "longSide", 16),
          new Case("transform.resize", "enlarge", "longSide", 80));

  @TestFactory
  Stream<DynamicTest> newOperationsMatchTheirGoldens() {
    OpRegistry registry = OpRegistry.load();
    List<DynamicTest> tests = new ArrayList<>();
    for (TestImages.Kind kind : TestImages.Kind.values()) {
      BufferedImage image = TestImages.create(TestImages.Pattern.NOISE, kind, 33, 17);
      String imageName = TestImages.name(TestImages.Pattern.NOISE, kind, 33, 17);
      for (Case c : CASES) {
        tests.add(
            dynamicTest(
                c.id() + " " + c.name() + " " + imageName,
                () -> check(registry.require(c.id()), c, image, imageName)));
      }
    }
    return tests.stream();
  }

  private static void check(Operation op, Case c, BufferedImage image, String imageName) {
    Params.Builder b = op.schema().defaults().toBuilder();
    for (int i = 0; i < c.values().length; i += 2) {
      b.set((String) c.values()[i], c.values()[i + 1]);
    }
    Img src = AwtImg.toImg(image);
    Img out = op.apply(src, b.build(), OpContext.sequential()).image();
    GoldenSupport.assertMatches(
        c.id(), c.name() + "__" + imageName, AwtImg.toBufferedImage(out), 0);
  }
}
