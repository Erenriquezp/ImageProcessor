package io.github.erenriquezp.rora.core.op;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.CancelledException;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class OperationSpiTest {

  /** Suma una constante al azul (op de prueba). */
  static class AddBlueOp extends BandOperation {
    static final ParamSchema SCHEMA =
        ParamSchema.of(new IntParam("amount", "op.test.amount", 0, 255, 1, OptionalInt.of(0)));

    @Override
    public String id() {
      return "test.add-blue";
    }

    @Override
    public ParamSchema schema() {
      return SCHEMA;
    }

    @Override
    public OpMeta meta() {
      return OpMeta.point();
    }

    @Override
    protected BandKernel prepare(Img src, Params p) {
      int amount = p.getInt("amount");
      return (in, out, w, h, y0, y1) -> {
        for (int i = y0 * w; i < y1 * w; i++) {
          out[i] = (in[i] & 0xFFFFFF00) | ((in[i] + amount) & 0xFF);
        }
      };
    }
  }

  @Test
  void bandOperationProducesANewImageAndLeavesTheInputUntouched() {
    Img src = Img.copyOf(2, 2, new int[] {0xFF000001, 0x80102030, 0, 0xFFFFFFFF});
    AddBlueOp op = new AddBlueOp();
    Params p = Params.builder(op.schema()).set("amount", 2).build();
    OpResult r = op.apply(src, p, OpContext.sequential());
    assertThat(r.image().toArray()).containsExactly(0xFF000003, 0x80102032, 2, 0xFFFFFF01);
    assertThat(src.toArray()).containsExactly(0xFF000001, 0x80102030, 0, 0xFFFFFFFF);
    assertThat(r.image()).isNotSameAs(src);
    assertThat(r.aux()).isNull();
    assertThat(op.kind(p)).isEqualTo(OpKind.POINT);
  }

  @Test
  void paramsFromAnotherSchemaAreRejected() {
    AddBlueOp op = new AddBlueOp();
    Params foreign = ParamsTest.SCHEMA.defaults();
    assertThatThrownBy(() -> op.apply(Img.create(1, 1), foreign, OpContext.sequential()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("test.add-blue");
    assertThatThrownBy(() -> op.apply(null, op.schema().defaults(), OpContext.sequential()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> op.apply(Img.create(1, 1), op.schema().defaults(), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void bandOperationHonoursCancellationAndThreads() {
    AddBlueOp op = new AddBlueOp();
    Img src = Img.copyOf(3, 200, new int[600]);
    CancellationToken token = CancellationToken.create();
    token.cancel();
    OpContext cancelled = new OpContext(TileExecutor.sequential(), token, Progress.NONE);
    assertThatThrownBy(() -> op.apply(src, op.schema().defaults(), cancelled))
        .isInstanceOf(CancelledException.class);
    try (TileExecutor ex = TileExecutor.create(3, 1)) {
      Img a = op.apply(src, op.schema().defaults(), OpContext.of(ex)).image();
      Img b = op.apply(src, op.schema().defaults(), OpContext.sequential()).image();
      assertThat(a.sameContent(b)).isTrue();
    }
  }

  @Test
  void contextAndResultValidateArguments() {
    assertThatThrownBy(() -> new OpContext(null, CancellationToken.none(), Progress.NONE))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new OpContext(TileExecutor.sequential(), null, Progress.NONE))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> new OpContext(TileExecutor.sequential(), CancellationToken.none(), null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> OpResult.of(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> OpResult.of(Img.create(1, 1), null))
        .isInstanceOf(NullPointerException.class);

    ExposureStats stats = new ExposureStats(10, 2, 3);
    OpResult r = OpResult.of(Img.create(1, 1), stats);
    assertThat(r.aux(ExposureStats.class)).contains(stats);
    assertThat(r.aux(ChannelHistogram.class)).isEmpty();
  }

  @Test
  void metadataRecordsValidateAndCopy() {
    OpMeta m = OpMeta.point();
    assertThat(m.preservesAlpha()).isTrue();
    assertThat(m.withAlphaChanged().preservesAlpha()).isFalse();
    assertThat(m.withCost(Cost.EXPENSIVE).cost()).isEqualTo(Cost.EXPENSIVE);
    assertThat(m.withIdentityTolerance(1).identityTolerance()).isEqualTo(1);
    assertThat(m.withKind(OpKind.GLOBAL).kind()).isEqualTo(OpKind.GLOBAL);
    assertThatThrownBy(() -> m.withIdentityTolerance(256))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new OpMeta(null, true, true, Cost.CHEAP, 0))
        .isInstanceOf(NullPointerException.class);

    assertThat(OpKind.POINT.haloRows()).isZero();
    assertThat(OpKind.POSITIONAL.rowAlign()).isEqualTo(1);
    assertThat(OpKind.GLOBAL.haloRows()).isEqualTo(Integer.MAX_VALUE);
    assertThat(OpKind.GLOBAL.rowAlign()).isEqualTo(1);
    OpKind n = new OpKind.Neighborhood(3, 8);
    assertThat(n.haloRows()).isEqualTo(3);
    assertThat(n.rowAlign()).isEqualTo(8);
    assertThatThrownBy(() -> new OpKind.Neighborhood(-1, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new OpKind.Neighborhood(0, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void auxiliaryDataIsValidatedAndDefensivelyCopied() {
    assertThatThrownBy(() -> new ExposureStats(0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ExposureStats(5, 6, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ExposureStats(5, 0, -1))
        .isInstanceOf(IllegalArgumentException.class);

    int[] r = new int[256];
    r[7] = 3;
    ChannelHistogram hist = new ChannelHistogram(r, new int[256], new int[256]);
    r[7] = 99;
    assertThat(hist.red()[7]).isEqualTo(3);
    hist.red()[7] = 50;
    assertThat(hist.red()[7]).isEqualTo(3);
    assertThat(hist.green()).hasSize(256);
    assertThat(hist.blue()).hasSize(256);
    assertThatThrownBy(() -> new ChannelHistogram(new int[255], new int[256], new int[256]))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void registryOfSortsFindsAndRejectsDuplicatesOrBadIds() {
    Operation other =
        new AddBlueOp() {
          @Override
          public String id() {
            return "test.aaa";
          }
        };
    OpRegistry reg = OpRegistry.of(List.of(new AddBlueOp(), other));
    assertThat(reg.ids()).containsExactly("test.aaa", "test.add-blue");
    assertThat(reg.all()).hasSize(2);
    assertThat(reg.size()).isEqualTo(2);
    assertThat(reg.find("test.add-blue")).isPresent();
    assertThat(reg.find("nope.x")).isEqualTo(Optional.empty());
    assertThat(reg.require("test.aaa")).isSameAs(other);
    assertThatThrownBy(() -> reg.require("nope.x")).hasMessageContaining("desconocida");
    assertThatThrownBy(() -> OpRegistry.of(List.of(new AddBlueOp(), new AddBlueOp())))
        .hasMessageContaining("duplicado");
    for (String bad : new String[] {"noDots", "Upper.case", "a..b", "a.b-", null}) {
      Operation op =
          new AddBlueOp() {
            @Override
            public String id() {
              return bad;
            }
          };
      assertThatThrownBy(() -> OpRegistry.of(List.of(op)))
          .as("id %s", bad)
          .isInstanceOf(IllegalStateException.class);
    }
  }
}
