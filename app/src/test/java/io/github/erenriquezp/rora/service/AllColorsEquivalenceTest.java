package io.github.erenriquezp.rora.service;

import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.testsupport.EquivalenceImages;
import java.awt.image.BufferedImage;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Equivalencia exhaustiva: legacy vs núcleo (pool de {@link TileExecutor#defaultParallelism()}
 * hilos) sobre una imagen 4096×4096 con <b>los 2^24 colores RGB</b> y alpha pseudoaleatorio de
 * semilla fija. Para las operaciones puntuales cubre todas las entradas RGB posibles.
 */
@Tag("exhaustive")
class AllColorsEquivalenceTest {

  private static final long ALPHA_SEED = 0xA11C0105L;
  private static BufferedImage allColors;
  private static TileExecutor pool;

  @BeforeAll
  static void setUp() {
    allColors = EquivalenceImages.allColors(ALPHA_SEED);
    pool = TileExecutor.create(TileExecutor.defaultParallelism());
  }

  @AfterAll
  static void tearDown() {
    pool.close();
    allColors = null;
  }

  static Stream<PortedCases.Case> cases() {
    // Los casos marcados en PortedCases: combinación típica de cada operación (y 3 en las de HSB).
    return PortedCases.all().stream().filter(PortedCases.Case::exhaustive);
  }

  @ParameterizedTest(name = "{0} sobre 2^24 colores")
  @MethodSource("cases")
  void coreMatchesLegacyOnAllColors(PortedCases.Case c) {
    BufferedImage expected = c.legacy().apply(allColors);
    BufferedImage actual = CoreBridge.apply(c.coreId(), allColors, c.params(), OpContext.of(pool));
    CoreEquivalenceTest.assertSameImage(
        c + " sobre 2^24 colores (alpha seed=0x" + Long.toHexString(ALPHA_SEED) + ")",
        expected,
        actual);
  }
}
