package io.github.erenriquezp.rora.core.ops.adjust;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.op.RandomParams;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Identidad exacta y paralelismo de {@code adjust.basic} sobre los 2^24 colores. */
@Tag("exhaustive")
class AdjustBasicAllColorsTest {

  private static final AdjustBasicOp OP = new AdjustBasicOp();

  @Test
  void neutralIsTheExactIdentityForEveryColor() {
    Img all = RandomParams.allColors(0x5EED_A0B0L);
    try (TileExecutor pool = TileExecutor.create(TileExecutor.defaultParallelism())) {
      Params neutral = OP.schema().neutral().orElseThrow();
      assertThat(OP.apply(all, neutral, OpContext.of(pool)).image().sameContent(all)).isTrue();
    }
  }

  @Test
  void oneAndManyThreadsGiveTheSameResultForEveryColor() {
    Img all = RandomParams.allColors(0x5EED_A0B1L);
    Params p =
        OP.schema().defaults().toBuilder()
            .set("exposure", 0.8)
            .set("contrast", 35.0)
            .set("highlights", -60.0)
            .set("shadows", 45.0)
            .set("temperature", 25.0)
            .set("tint", -10.0)
            .set("vibrance", 30.0)
            .set("saturation", -15.0)
            .build();
    Img one = OP.apply(all, p, OpContext.sequential()).image();
    try (TileExecutor pool = TileExecutor.create(TileExecutor.defaultParallelism())) {
      assertThat(OP.apply(all, p, OpContext.of(pool)).image().sameContent(one)).isTrue();
    }
  }
}
