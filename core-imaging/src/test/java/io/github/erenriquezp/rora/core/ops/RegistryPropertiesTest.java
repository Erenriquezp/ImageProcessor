package io.github.erenriquezp.rora.core.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.CancelledException;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.OpResult;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.ParamDef;
import io.github.erenriquezp.rora.core.op.ParamDef.BoolParam;
import io.github.erenriquezp.rora.core.op.ParamDef.ColorParam;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamDef.EnumParam;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import io.github.erenriquezp.rora.core.op.ParamDef.SelectionParam;
import io.github.erenriquezp.rora.core.op.Params;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Propiedades de <b>todas</b> las operaciones del registro, generadas a partir de sus metadatos y
 * de su esquema: una operación nueva queda cubierta con solo registrarse en {@code provides}.
 *
 * <p>Para cada operación y cada combinación de parámetros (por defecto, neutro, mínimo y máximo de
 * cada parámetro, cada valor de enum, booleanos invertidos, colores y selecciones extremas) sobre
 * varias imágenes deterministas: no lanza, conserva dimensiones y alpha si lo declara, es
 * determinista, no modifica la entrada, da lo mismo con 1 hilo, con N hilos, con bandas de 1 fila y
 * en modo <i>strict halo</i> (no lee fuera del halo que declara), es la identidad con el neutro
 * (dentro de {@code identityTolerance}), respeta la cancelación e informa del progreso hasta el
 * final.
 */
@Tag("property")
class RegistryPropertiesTest {

  private static final OpRegistry REGISTRY = OpRegistry.load();
  private static final TileExecutor POOL = TileExecutor.create(4);
  private static final TileExecutor TINY_BANDS = TileExecutor.create(3, 1);
  private static final List<Img> IMAGES = images();

  @AfterAll
  static void close() {
    POOL.close();
    TINY_BANDS.close();
  }

  /** Lista explícita: cambiarla obliga a revisar el catálogo y la documentación. */
  @Test
  void registryContainsThePortedOperations() {
    assertThat(REGISTRY.ids())
        .containsExactly(
            "accum.motion",
            "adjust.basic",
            "analysis.exposure",
            "analysis.histogram",
            "artistic.circular-fade",
            "blur.gaussian",
            "color.brightness",
            "color.grayscale-average",
            "color.grayscale-quantized",
            "color.hsv",
            "color.matrix-preset",
            "color.negative",
            "color.recolor",
            "color.threshold-bw",
            "detail.unsharp-mask",
            "fragment.blend-color",
            "point.color-gain",
            "point.hue-rotate",
            "point.luminance",
            "point.saturation",
            "point.scale-bias",
            "point.soft-threshold",
            "raster.depth-map",
            "raster.pixelate",
            "raster.texture-sample",
            "tone.equalize",
            "tone.posterize",
            "tone.posterize-channels");
  }

  @TestFactory
  Stream<DynamicTest> schemaIsWellFormed() {
    return REGISTRY.all().stream()
        .map(
            op ->
                dynamicTest(
                    op.id(),
                    () -> {
                      assertThat(op.schema()).isNotNull();
                      assertThat(op.meta()).isNotNull();
                      for (ParamDef d : op.schema().params()) {
                        assertThat(d.labelKey()).startsWith("op." + op.id() + ".");
                      }
                      // La clase es la que publica provides y tiene constructor público sin args.
                      assertThat(op.getClass().getConstructor().newInstance().id())
                          .isEqualTo(op.id());
                    }));
  }

  @TestFactory
  Stream<DynamicTest> propertiesHoldForEveryParameterCombination() {
    return REGISTRY.all().stream()
        .flatMap(
            op ->
                paramSets(op).stream()
                    .map(p -> dynamicTest(op.id() + " " + p, () -> checkProperties(op, p))));
  }

  @TestFactory
  Stream<DynamicTest> neutralParametersAreTheIdentity() {
    return REGISTRY.all().stream()
        .filter(op -> op.schema().neutral().isPresent())
        .map(
            op ->
                dynamicTest(
                    op.id(),
                    () -> {
                      Params n = op.schema().neutral().orElseThrow();
                      int tol = op.meta().identityTolerance();
                      for (Img src : IMAGES) {
                        Img out = op.apply(src, n, OpContext.sequential()).image();
                        assertWithin(op.id() + " neutro " + n + " sobre " + src, src, out, tol);
                      }
                    }));
  }

  @TestFactory
  Stream<DynamicTest> cancelledTokenStopsTheOperation() {
    return REGISTRY.all().stream()
        .map(
            op ->
                dynamicTest(
                    op.id(),
                    () -> {
                      CancellationToken t = CancellationToken.create();
                      t.cancel();
                      OpContext ctx = new OpContext(POOL, t, Progress.NONE);
                      // Con el neutro de tone.equalize no hay trabajo, pero también se respeta.
                      Params p = op.schema().neutral().orElse(op.schema().defaults());
                      assertThatThrownBy(() -> op.apply(IMAGES.get(4), p, ctx))
                          .as(op.id())
                          .isInstanceOf(CancelledException.class);
                      assertThatThrownBy(
                              () ->
                                  op.apply(
                                      IMAGES.get(4),
                                      op.schema().defaults(),
                                      new OpContext(TileExecutor.sequential(), t, Progress.NONE)))
                          .isInstanceOf(CancelledException.class);
                    }));
  }

  private static void checkProperties(Operation op, Params p) {
    for (Img src : IMAGES) {
      String where = op.id() + " " + p + " sobre " + src;
      int[] before = src.toArray();
      AtomicLong lastDone = new AtomicLong();
      AtomicLong lastTotal = new AtomicLong();
      OpResult r =
          op.apply(
              src,
              p,
              new OpContext(
                  TileExecutor.sequential(),
                  CancellationToken.none(),
                  (d, t) -> {
                    assertThat(d).as(where + " progreso").isGreaterThan(lastDone.get());
                    lastDone.set(d);
                    lastTotal.set(t);
                  }));
      Img out = r.image();
      assertThat(src.toArray()).as(where + ": entrada modificada").isEqualTo(before);
      if (lastTotal.get() > 0) {
        assertThat(lastDone.get()).as(where + ": progreso incompleto").isEqualTo(lastTotal.get());
      }
      if (op.meta().preservesDimensions()) {
        assertThat(out.width()).as(where).isEqualTo(src.width());
        assertThat(out.height()).as(where).isEqualTo(src.height());
      }
      if (op.meta().preservesAlpha()) {
        for (int i = 0; i < src.pixelCount(); i++) {
          int x = i % src.width();
          int y = i / src.width();
          if ((out.argb(x, y) >>> 24) != (src.argb(x, y) >>> 24)) {
            throw new AssertionError(where + ": alpha cambiado en (" + x + "," + y + ")");
          }
        }
      }
      assertSame(where + " determinismo", out, op.apply(src, p, OpContext.sequential()).image());
      assertSame(where + " 4 hilos", out, op.apply(src, p, OpContext.of(POOL)).image());
      assertSame(
          where + " bandas de 1 fila", out, op.apply(src, p, OpContext.of(TINY_BANDS)).image());
      assertSame(
          where + " strict halo (64 filas)",
          out,
          op.apply(src, p, OpContext.of(TileExecutor.strictHalo(64))).image());
      assertSame(
          where + " strict halo (1 fila)",
          out,
          op.apply(src, p, OpContext.of(TileExecutor.strictHalo(1))).image());
    }
  }

  /** Por defecto, neutro y una variación de cada parámetro sobre los valores por defecto. */
  static List<Params> paramSets(Operation op) {
    Set<Params> sets = new LinkedHashSet<>();
    Params defaults = op.schema().defaults();
    sets.add(defaults);
    op.schema().neutral().ifPresent(sets::add);
    for (ParamDef d : op.schema().params()) {
      List<Object> values = new ArrayList<>();
      switch (d) {
        case IntParam ip -> {
          values.add(ip.min());
          values.add(ip.max());
        }
        case DecimalParam dp -> {
          values.add(dp.min());
          values.add(dp.max());
          values.add((dp.min() + dp.max()) / 2);
        }
        case BoolParam bp -> values.add(!bp.defaultBool());
        case EnumParam ep -> values.addAll(ep.values());
        case ColorParam cp -> values.addAll(List.of(0xFF000000, 0xFFFFFFFF, 0xFF12A0F0));
        case SelectionParam sp -> {
          values.add(Set.of());
          values.add(Set.copyOf(sp.options()));
          values.add(Set.of(sp.options().get(0)));
        }
      }
      for (Object v : values) {
        sets.add(defaults.toBuilder().set(d.key(), v).build());
      }
    }
    return List.copyOf(sets);
  }

  /** 1×1, 2×2, impar, cuadrada con alpha y multibanda, con ruido y valores frontera. */
  private static List<Img> images() {
    int[][] sizes = {{1, 1}, {2, 2}, {3, 5}, {16, 16}, {33, 130}};
    int[] extremes = {0, 1, 15, 16, 127, 128, 239, 240, 254, 255};
    List<Img> out = new ArrayList<>();
    for (int k = 0; k < sizes.length; k++) {
      int w = sizes[k][0];
      int h = sizes[k][1];
      SplittableRandom rnd = new SplittableRandom(0x5EED_C0DEL + k);
      int[] px = new int[w * h];
      for (int i = 0; i < px.length; i++) {
        px[i] =
            (i % 3 == 0)
                ? rnd.nextInt()
                : (extremes[rnd.nextInt(10)] << 24)
                    | (extremes[rnd.nextInt(10)] << 16)
                    | (extremes[rnd.nextInt(10)] << 8)
                    | extremes[rnd.nextInt(10)];
      }
      out.add(Img.adopt(w, h, px));
    }
    return List.copyOf(out);
  }

  private static void assertSame(String where, Img expected, Img actual) {
    if (!expected.sameContent(actual)) {
      throw new AssertionError(where + ": resultado distinto");
    }
  }

  private static void assertWithin(String where, Img expected, Img actual, int tol) {
    assertThat(actual.width()).as(where).isEqualTo(expected.width());
    assertThat(actual.height()).as(where).isEqualTo(expected.height());
    for (int y = 0; y < expected.height(); y++) {
      for (int x = 0; x < expected.width(); x++) {
        int e = expected.argb(x, y);
        int a = actual.argb(x, y);
        for (int shift = 0; shift < 32; shift += 8) {
          int d = Math.abs(((e >>> shift) & 0xFF) - ((a >>> shift) & 0xFF));
          if (d > tol) {
            throw new AssertionError(
                String.format(
                    "%s: (%d,%d) esperado %08x, obtenido %08x (tolerancia %d)",
                    where, x, y, e, a, tol));
          }
        }
      }
    }
  }
}
