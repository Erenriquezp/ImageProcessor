package io.github.erenriquezp.rora.service;

import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.testsupport.EquivalenceImages;
import io.github.erenriquezp.rora.testsupport.EquivalenceImages.Sample;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Equivalencia <b>exacta</b> legacy vs núcleo de cada operación portada: por cada caso de {@link
 * PortedCases} y cada tipo de imagen, sobre todos los tamaños y contenidos de {@link
 * EquivalenceImages}, compara el legacy con
 *
 * <ol>
 *   <li>la fachada {@code ImageProcessor} (pool compartido; comprueba el mapeo de parámetros),
 *   <li>el núcleo con 1 hilo,
 *   <li>el núcleo con 7 hilos,
 *   <li>el núcleo con 3 hilos y bandas de 1 fila.
 * </ol>
 *
 * Mismo tipo de salida, mismas dimensiones, mismo {@code getRGB} en cada píxel y misma identidad
 * (si el legacy devuelve su entrada, el núcleo también).
 */
@Tag("equivalence")
class CoreEquivalenceTest {

  private static final TileExecutor SEVEN = TileExecutor.create(7);
  private static final TileExecutor TINY_BANDS = TileExecutor.create(3, 1);
  private static final Map<Integer, List<Sample>> SAMPLES = new ConcurrentHashMap<>();

  @AfterAll
  static void closePools() {
    SEVEN.close();
    TINY_BANDS.close();
  }

  static Stream<Object[]> casesAndTypes() {
    return PortedCases.all().stream()
        .flatMap(
            c ->
                EquivalenceImages.TYPES.stream()
                    .map(t -> new Object[] {c, EquivalenceImages.typeName(t), t}));
  }

  @ParameterizedTest(name = "{0} sobre {1}")
  @MethodSource("casesAndTypes")
  void coreMatchesLegacyExactly(PortedCases.Case c, String typeName, int type) {
    List<Sample> samples = SAMPLES.computeIfAbsent(type, EquivalenceImages::samples);
    // Parámetros no representables en el núcleo (canales fuera de 0-255): la fachada usa el
    // legacy y solo se compara ese camino.
    boolean coreApplicable = coreApplicable(c);
    for (Sample s : samples) {
      BufferedImage expected = c.legacy().apply(s.image());
      if (c.facade() != null) {
        check(c, s, "fachada", expected, c.facade());
      }
      if (coreApplicable) {
        check(c, s, "1 hilo", expected, core(c, OpContext.sequential()));
        check(c, s, "7 hilos", expected, core(c, OpContext.of(SEVEN)));
        check(c, s, "bandas de 1 fila", expected, core(c, OpContext.of(TINY_BANDS)));
      }
    }
  }

  /** Con un enum {@code null} el legacy lanza NPE en su {@code switch}; la fachada también. */
  @org.junit.jupiter.api.Test
  @SuppressWarnings("removal")
  void nullEnumsThrowNullPointerExceptionLikeTheLegacy() {
    BufferedImage img = EquivalenceImages.samples(BufferedImage.TYPE_INT_ARGB).get(3).image();
    java.util.List<Runnable> legacyAndFacade =
        java.util.List.of(
            () -> RasterPipelineFilters.depthMap(img, null),
            () -> ImageProcessor.depthMap(img, null),
            () -> FragmentOpsFilters.fragmentBlend(img, null, 1, 2, 3, 0.5f),
            () -> ImageProcessor.fragmentBlend(img, null, 1, 2, 3, 0.5f),
            () -> ImageProcessor.fragmentBlend(img, null, 300, 2, 3, 0.5f));
    for (Runnable r : legacyAndFacade) {
      org.assertj.core.api.Assertions.assertThatThrownBy(r::run)
          .isInstanceOf(NullPointerException.class);
    }
  }

  static boolean coreApplicable(PortedCases.Case c) {
    return c.params().apply(Params.builder(CoreBridge.op(c.coreId()).schema())) != null;
  }

  private static UnaryOperator<BufferedImage> core(PortedCases.Case c, OpContext ctx) {
    return i -> CoreBridge.apply(c.coreId(), i, c.params(), ctx);
  }

  static void check(
      PortedCases.Case c,
      Sample s,
      String path,
      BufferedImage expected,
      UnaryOperator<BufferedImage> fn) {
    BufferedImage in = s.image();
    BufferedImage actual = fn.apply(in);
    String where = c + " [" + path + "] sobre " + s;
    if ((expected == in) != (actual == in)) {
      throw new AssertionError(
          where + ": el legacy " + (expected == in ? "" : "no ") + "devuelve la misma instancia");
    }
    assertSameImage(where, expected, actual);
  }

  static void assertSameImage(String where, BufferedImage expected, BufferedImage actual) {
    if (expected.getType() != actual.getType()) {
      throw new AssertionError(
          where
              + ": tipo "
              + EquivalenceImages.typeName(actual.getType())
              + ", esperado "
              + EquivalenceImages.typeName(expected.getType()));
    }
    int w = expected.getWidth();
    if (w != actual.getWidth() || expected.getHeight() != actual.getHeight()) {
      throw new AssertionError(where + ": dimensiones distintas");
    }
    int[] e = EquivalenceImages.pixels(expected);
    int[] a = EquivalenceImages.pixels(actual);
    for (int i = 0; i < e.length; i++) {
      if (e[i] != a[i]) {
        throw new AssertionError(
            String.format(
                "%s: primer píxel distinto en (%d,%d): esperado %08x, obtenido %08x",
                where, i % w, i / w, e[i], a[i]));
      }
    }
  }
}
