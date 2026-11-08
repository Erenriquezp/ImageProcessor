package com.example.imageprocessor.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GoldenSupportTest {

  @Test
  void missingGoldenFailsWithClearMessageAndIsNeverCreated() {
    assumeFalse(GoldenSupport.updating(), "no aplica con -Dgolden.update=true");
    BufferedImage img = TestImages.create(TestImages.Pattern.CHECKER, TestImages.Kind.RGB, 2, 2);
    assertThatThrownBy(() -> GoldenSupport.assertMatches("__no_existe__", "caso", img, 0))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("golden.update=true");
    Path created =
        Path.of(System.getProperty("basedir", "."), "src/test/resources/golden/__no_existe__");
    assertThat(Files.exists(created)).isFalse();
  }

  @Test
  void testImagesAreDeterministic() {
    for (TestImages.Pattern p : TestImages.Pattern.values()) {
      for (TestImages.Kind k : TestImages.Kind.values()) {
        ImageAssert.assertEqualExactly(
            TestImages.create(p, k, 33, 17), TestImages.create(p, k, 33, 17));
      }
    }
  }

  @Test
  void argbImagesHavePartialAlpha() {
    BufferedImage img =
        TestImages.create(TestImages.Pattern.GRADIENT, TestImages.Kind.ARGB, 16, 16);
    assertThat(img.getRGB(0, 0) >>> 24).isEqualTo(255);
    assertThat(img.getRGB(15, 0) >>> 24).isBetween(1, 254);
  }
}
