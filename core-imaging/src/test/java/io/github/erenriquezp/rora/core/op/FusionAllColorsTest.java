package io.github.erenriquezp.rora.core.op;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Fusión exacta sobre los 2^24 colores en cadenas con operaciones HSB (y una posicional). */
@Tag("exhaustive")
class FusionAllColorsTest {

  private static final OpRegistry REGISTRY = OpRegistry.load();
  private static TileExecutor pool;
  private static Img allColors;

  @BeforeAll
  static void setUp() {
    pool = TileExecutor.create(TileExecutor.defaultParallelism());
    allColors = RandomParams.allColors(0x5EED_0020L);
  }

  @AfterAll
  static void tearDown() {
    pool.close();
    allColors = null;
  }

  private static PointFusion.Stage stage(String id, Object... kv) {
    PointOperation op = (PointOperation) REGISTRY.require(id);
    Params.Builder b = Params.builder(op.schema());
    for (int i = 0; i < kv.length; i += 2) {
      b.set((String) kv[i], kv[i + 1]);
    }
    return new PointFusion.Stage(op, b.build());
  }

  private static List<PointFusion.Stage> chain(int n) {
    return switch (n) {
      case 0 ->
          List.of(
              stage("point.saturation", "factor", 1.3f),
              stage("point.hue-rotate", "degrees", 45f),
              stage("color.hsv", "saturation", 0.8f, "value", 1.1f));
      case 1 ->
          List.of(
              stage("color.hsv", "saturation", 1.5f, "value", 0.9f),
              stage("color.matrix-preset", "preset", "sepia"),
              stage("point.saturation", "factor", 0.6f));
      case 2 ->
          List.of(
              stage("color.brightness", "value", 20),
              stage("point.hue-rotate", "degrees", -120f),
              stage("artistic.circular-fade"),
              stage("point.scale-bias", "scale", 1.2f, "bias", -10f));
      default -> throw new IllegalArgumentException();
    };
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void fusedEqualsSequentialOnAllColors(int n) {
    List<PointFusion.Stage> chain = chain(n);
    Img cur = allColors;
    List<String> ids = new ArrayList<>();
    for (PointFusion.Stage s : chain) {
      cur = s.op().apply(cur, s.params(), OpContext.of(pool)).image();
      ids.add(s.op().id());
    }
    Img fused = PointFusion.apply(allColors, chain, OpContext.of(pool));
    assertThat(fused.sameContent(cur)).as(ids.toString()).isTrue();
  }
}
