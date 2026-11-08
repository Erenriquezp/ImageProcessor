package io.github.erenriquezp.rora.core.op;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * El render FULL de una receta es idéntico bit a bit a aplicar sus operaciones una a una con el
 * registro: con y sin fusión, con caché fría y caliente, con 1 y 7 hilos.
 */
@Tag("equivalence")
class RecipeEquivalenceTest {

  private static final OpRegistry REGISTRY = OpRegistry.load();
  private static final TileExecutor SEVEN = TileExecutor.create(7);

  /** Operaciones que pueden ir en una receta (las de análisis no cambian la imagen, pero valen). */
  private static final List<Operation> OPS = REGISTRY.all();

  @AfterAll
  static void close() {
    SEVEN.close();
  }

  static List<OpSpec> randomRecipe(SplittableRandom rnd, int len) {
    List<OpSpec> steps = new ArrayList<>();
    for (int j = 0; j < len; j++) {
      Operation op;
      int kind = rnd.nextInt(3);
      // Mezcla: 1/3 puntual, 1/3 cualquiera, 1/3 no puntual.
      do {
        op = OPS.get(rnd.nextInt(OPS.size()));
      } while ((kind == 0 && !(op instanceof PointOperation))
          || (kind == 2 && op instanceof PointOperation));
      OpSpec s = OpSpec.of(op, RandomParams.random(op.schema(), rnd));
      if (rnd.nextInt(8) == 0) {
        s = s.withEnabled(false);
      }
      steps.add(s);
    }
    return steps;
  }

  static Img sequential(Img src, List<OpSpec> steps) {
    Img cur = src;
    for (OpSpec s : steps) {
      if (s.enabled()) {
        Operation op = REGISTRY.require(s.opId());
        cur = op.apply(cur, s.params().withSchema(op.schema()), OpContext.sequential()).image();
      }
    }
    return cur;
  }

  @Test
  void seededRecipesFullRenderEqualsSequentialApplication() {
    SplittableRandom rnd = new SplittableRandom(0x5EED_0050L);
    int[][] sizes = {{1, 1}, {5, 3}, {33, 17}, {64, 48}, {97, 130}};
    int nonPointSteps = 0;
    for (int t = 0; t < 300; t++) {
      int[] sz = sizes[t % sizes.length];
      Img src = RandomParams.noise(sz[0], sz[1], 0x5EED_5000L + t);
      List<OpSpec> steps = randomRecipe(rnd, 1 + rnd.nextInt(12));
      for (OpSpec s : steps) {
        if (!(REGISTRY.require(s.opId()) instanceof PointOperation)) {
          nonPointSteps++;
        }
      }
      int head = rnd.nextInt(steps.size() + 1);
      Img expected = sequential(src, steps);
      Renderer.Request req =
          new Renderer.Request(new Renderer.SourceImage(t, src), steps, head, RenderLevel.FULL);
      String what = "receta " + t + " " + EditRecipe.of(steps).canonical();
      for (boolean fusion : new boolean[] {false, true}) {
        Renderer r = new Renderer(REGISTRY, new ResultCache(64L << 20), fusion);
        Img cold = r.render(req, OpContext.sequential()).image();
        assertThat(cold.sameContent(expected)).as("fría fusion=" + fusion + " " + what).isTrue();
        Renderer.Result warm = r.render(req, OpContext.of(SEVEN));
        assertThat(warm.image().sameContent(expected))
            .as("caliente fusion=" + fusion + " " + what)
            .isTrue();
        assertThat(warm.stepsComputed()).as("todo en caché").isZero();
        Renderer r7 = new Renderer(REGISTRY, new ResultCache(64L << 20), fusion);
        assertThat(r7.render(req, OpContext.of(SEVEN)).image().sameContent(expected))
            .as("7 hilos fusion=" + fusion + " " + what)
            .isTrue();
      }
    }
    assertThat(nonPointSteps).as("la mezcla incluye vecindad y globales").isGreaterThan(300);
  }

  @Test
  void editingTheTailReusesTheCachedHead() {
    SplittableRandom rnd = new SplittableRandom(0x5EED_0051L);
    Img src = RandomParams.noise(40, 30, 7);
    List<OpSpec> head = randomRecipe(rnd, 10);
    Renderer r = new Renderer(REGISTRY, new ResultCache(64L << 20), true);
    Renderer.SourceImage source = new Renderer.SourceImage(1, src);
    Operation sat = REGISTRY.require("point.saturation");
    for (int tick = 0; tick < 20; tick++) {
      List<OpSpec> steps = new ArrayList<>(head);
      steps.add(OpSpec.of(sat, Params.builder(sat.schema()).set("factor", tick / 10f).build()));
      Renderer.Result res =
          r.render(
              new Renderer.Request(source, steps, head.size(), RenderLevel.FULL),
              OpContext.sequential());
      assertThat(res.image().sameContent(sequential(src, steps))).isTrue();
      if (tick > 0) {
        assertThat(res.stepsComputed()).as("solo el pendiente").isEqualTo(1);
      }
    }
  }
}
