package io.github.erenriquezp.rora.core.op;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * La fusión de operaciones puntuales es exacta: una cadena fusionada da los mismos píxeles que
 * aplicar sus operaciones una a una (con cuantización a 8 bits entre etapas), con cualquier número
 * de hilos y cualquier tamaño de banda.
 */
@Tag("equivalence")
class FusionEquivalenceTest {

  private static final OpRegistry REGISTRY = OpRegistry.load();
  private static final List<PointOperation> POINT_OPS =
      REGISTRY.all().stream()
          .filter(PointOperation.class::isInstance)
          .map(PointOperation.class::cast)
          .toList();
  private static final TileExecutor SEVEN = TileExecutor.create(7);

  @AfterAll
  static void close() {
    SEVEN.close();
  }

  static List<PointFusion.Stage> randomChain(SplittableRandom rnd, int len) {
    List<PointFusion.Stage> chain = new ArrayList<>();
    for (int j = 0; j < len; j++) {
      PointOperation op = POINT_OPS.get(rnd.nextInt(POINT_OPS.size()));
      chain.add(new PointFusion.Stage(op, RandomParams.random(op.schema(), rnd)));
    }
    return chain;
  }

  static Img sequential(Img src, List<PointFusion.Stage> chain) {
    Img cur = src;
    for (PointFusion.Stage s : chain) {
      cur = s.op().apply(cur, s.params(), OpContext.sequential()).image();
    }
    return cur;
  }

  static String describe(List<PointFusion.Stage> chain) {
    return chain.stream().map(s -> s.op().id() + s.params().canonical()).toList().toString();
  }

  @Test
  void seededChainsMatchSequentialApplication() {
    int[][] sizes = {{1, 1}, {3, 5}, {7, 1}, {1, 7}, {64, 48}, {129, 200}};
    SplittableRandom rnd = new SplittableRandom(0x5EED_0010L);
    for (int i = 0; i < 1000; i++) {
      int[] sz = sizes[i % sizes.length];
      Img src = RandomParams.noise(sz[0], sz[1], 0x5EED_1000L + i);
      List<PointFusion.Stage> chain = randomChain(rnd, 2 + rnd.nextInt(5));
      Img expected = sequential(src, chain);
      String what = "caso " + i + " " + sz[0] + "x" + sz[1] + " " + describe(chain);
      assertThat(PointFusion.apply(src, chain, OpContext.sequential()).sameContent(expected))
          .as("1 hilo " + what)
          .isTrue();
      assertThat(PointFusion.apply(src, chain, OpContext.of(SEVEN)).sameContent(expected))
          .as("7 hilos " + what)
          .isTrue();
      OpContext oneRow =
          new OpContext(TileExecutor.sequential(1), CancellationToken.none(), Progress.NONE);
      assertThat(PointFusion.apply(src, chain, oneRow).sameContent(expected))
          .as("bandas de 1 fila " + what)
          .isTrue();
    }
  }

  @Test
  void fusionReadsOnlyItsBand() {
    // strictHalo envenena las filas de fuera de la banda: un kernel que leyera otras filas
    // fallaría.
    SplittableRandom rnd = new SplittableRandom(0x5EED_0011L);
    Img src = RandomParams.noise(31, 70, 0x5EED_0012L);
    for (int i = 0; i < 100; i++) {
      List<PointFusion.Stage> chain = randomChain(rnd, 1 + rnd.nextInt(6));
      OpContext strict =
          new OpContext(TileExecutor.strictHalo(8), CancellationToken.none(), Progress.NONE);
      assertThat(PointFusion.apply(src, chain, strict).sameContent(sequential(src, chain)))
          .as(describe(chain))
          .isTrue();
    }
  }

  @Test
  void sourceIsNotModifiedAndResultIsANewInstance() {
    Img src = RandomParams.noise(16, 16, 0x5EED_0013L);
    Img copy = Img.copyOf(16, 16, src.toArray());
    List<PointFusion.Stage> chain = randomChain(new SplittableRandom(1), 4);
    Img out = PointFusion.apply(src, chain, OpContext.sequential());
    assertThat(out).isNotSameAs(src);
    assertThat(src.sameContent(copy)).isTrue();
  }
}
