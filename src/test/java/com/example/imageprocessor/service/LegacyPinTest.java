package com.example.imageprocessor.service;

import com.example.imageprocessor.testsupport.GoldenSupport;
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
 * Nivel A: fija con golden exacto el comportamiento heredado de las operaciones cuyo cálculo
 * sobrevivirá. Las fases siguientes pueden actualizar los goldens de forma deliberada
 * (-Dgolden.update=true) explicando el porqué en el commit.
 */
@Tag("legacy-pin")
class LegacyPinTest {

  /** Imágenes de golden: ARGB con alpha parcial 16x16 y RGB impar 33x17 con ruido con semilla. */
  private static final List<Object[]> IMAGES =
      List.of(
          new Object[] {Pattern.GRADIENT, Kind.ARGB, 16, 16},
          new Object[] {Pattern.NOISE, Kind.RGB, 33, 17});

  static Stream<Object[]> pins() {
    return OpCatalog.levelA().stream()
        .flatMap(oc -> IMAGES.stream().map(im -> new Object[] {oc, im[0], im[1], im[2], im[3]}));
  }

  @ParameterizedTest(name = "{0} sobre {1}-{2} {3}x{4}")
  @MethodSource("pins")
  void matchesGolden(OpCase oc, Pattern p, Kind k, int w, int h) {
    BufferedImage in = TestImages.create(p, k, w, h);
    BufferedImage out = oc.apply(in);
    GoldenSupport.assertMatches(oc.op(), oc.name() + "__" + TestImages.name(p, k, w, h), out, 0);
  }
}
