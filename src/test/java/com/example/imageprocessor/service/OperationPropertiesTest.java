package com.example.imageprocessor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.imageprocessor.testsupport.ImageAssert;
import com.example.imageprocessor.testsupport.OpCase;
import com.example.imageprocessor.testsupport.OpCatalog;
import com.example.imageprocessor.testsupport.TestImages;
import com.example.imageprocessor.testsupport.TestImages.Kind;
import com.example.imageprocessor.testsupport.TestImages.Pattern;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Propiedades que toda operación (niveles A, B y C) debe cumplir, incluidos los casos límite 1x1 y
 * dimensiones impares: no lanza, conserva dimensiones, es determinista, no muta la entrada, deja
 * los canales en [0,255], conserva alpha cuando corresponde y los parámetros neutros son ≈
 * identidad.
 */
@Tag("property")
class OperationPropertiesTest {

  /** Añade la operación y la imagen al mensaje: surefire solo muestra el índice del caso. */
  private static void ctx(OpCase oc, Pattern p, Kind k, int w, int h, Runnable body) {
    try {
      body.run();
    } catch (AssertionError e) {
      throw new AssertionError(
          oc + " sobre " + TestImages.name(p, k, w, h) + ": " + e.getMessage(), e);
    }
  }

  private static final List<Object[]> IMAGES =
      List.of(
          new Object[] {Pattern.GRADIENT, Kind.ARGB, 1, 1},
          new Object[] {Pattern.HARD_EDGES, Kind.ARGB, 2, 2},
          new Object[] {Pattern.NOISE, Kind.RGB, 33, 17},
          new Object[] {Pattern.CHECKER, Kind.ARGB, 16, 16});

  static Stream<Object[]> combos() {
    return OpCatalog.all().stream()
        .flatMap(oc -> IMAGES.stream().map(im -> new Object[] {oc, im[0], im[1], im[2], im[3]}));
  }

  static Stream<Object[]> neutralCombos() {
    return combos().filter(a -> ((OpCase) a[0]).identity());
  }

  @ParameterizedTest(name = "{0} sobre {1}-{2} {3}x{4}")
  @MethodSource("combos")
  void preservesDimensionsAndRange(OpCase oc, Pattern p, Kind k, int w, int h) {
    BufferedImage in = TestImages.create(p, k, w, h);
    BufferedImage[] out = new BufferedImage[1];
    assertThatCode(() -> out[0] = oc.apply(in)).doesNotThrowAnyException();
    ImageAssert.assertSameSize(out[0], in);
    ImageAssert.assertChannelsInRange(out[0]);
  }

  @ParameterizedTest(name = "{0} sobre {1}-{2} {3}x{4}")
  @MethodSource("combos")
  void isDeterministicAndDoesNotMutateInput(OpCase oc, Pattern p, Kind k, int w, int h) {
    BufferedImage in = TestImages.create(p, k, w, h);
    int[] before = ImageAssert.snapshot(in);
    BufferedImage first = oc.apply(in);
    BufferedImage second = oc.apply(in);
    assertThat(ImageAssert.snapshot(in)).as("la entrada no debe mutar").isEqualTo(before);
    ImageAssert.assertEqualExactly(second, first);
  }

  @ParameterizedTest(name = "{0} sobre {1}-{2} {3}x{4}")
  @MethodSource("combos")
  void preservesAlphaWhenExpected(OpCase oc, Pattern p, Kind k, int w, int h) {
    if (!oc.alphaPreserved()) {
      return;
    }
    BufferedImage in = TestImages.create(p, k, w, h);
    ctx(oc, p, k, w, h, () -> ImageAssert.assertAlphaPreserved(oc.apply(in), in));
  }

  @ParameterizedTest(name = "{0} sobre {1}-{2} {3}x{4}")
  @MethodSource("neutralCombos")
  void neutralParametersAreIdentity(OpCase oc, Pattern p, Kind k, int w, int h) {
    BufferedImage in = TestImages.create(p, k, w, h);
    ctx(
        oc,
        p,
        k,
        w,
        h,
        () -> ImageAssert.assertEqualWithin(oc.apply(in), in, oc.identityTolerance()));
  }
}
