package io.github.erenriquezp.rora.ai.onnx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.ai.api.TileRunner;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.CancelledException;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure.Reason;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OnnxEngineTest {

  private static float[] gradient(int h, int w) {
    float[] v = new float[3 * h * w];
    for (int i = 0; i < v.length; i++) {
      v[i] = (i % 256) / 255f;
    }
    return v;
  }

  private static float[] run(TileRunner r, float[] in, int h, int w) throws AiFailure {
    return r.run(in, h, w, CancellationToken.none());
  }

  @Test
  void nearestModelRepeatsEachSample() throws Exception {
    try (TileRunner r = new OnnxEngine().open(TestModels.named("nearest_x4", 4), 2)) {
      float[] in = gradient(8, 8);
      float[] out = run(r, in, 8, 8);
      assertThat(out).hasSize(3 * 32 * 32);
      assertThat(out[3 * 32 * 32 - 1]).isEqualTo(in[in.length - 1]);
    }
  }

  @Test
  void garbageBytesAreReportedAsACorruptModel() {
    byte[] garbage = new byte[512];
    Arrays.fill(garbage, (byte) 0x7F);
    assertFails(() -> new OnnxEngine().open(TestModels.raw(garbage, 4), 2), Reason.MODEL_CORRUPT);
  }

  @Test
  void truncatedModelIsReportedAsCorrupt() {
    byte[] full = TestModels.bytes("box5_x4");
    byte[] cut = Arrays.copyOf(full, full.length / 2);
    assertFails(() -> new OnnxEngine().open(TestModels.raw(cut, 4), 2), Reason.MODEL_CORRUPT);
  }

  @Test
  void modelWithTheWrongInputIsRejectedWhenOpening() {
    assertFails(
        () -> new OnnxEngine().open(TestModels.named("wrong_channels", 4), 2),
        Reason.SHAPE_MISMATCH);
  }

  @Test
  void outputOfTheWrongSizeIsRejected() throws Exception {
    try (TileRunner r = new OnnxEngine().open(TestModels.named("wrong_scale", 4), 2)) {
      assertFails(() -> run(r, gradient(8, 8), 8, 8), Reason.SHAPE_MISMATCH);
    }
  }

  @Test
  void inputOfTheWrongLengthIsRejected() throws Exception {
    try (TileRunner r = new OnnxEngine().open(TestModels.named("nearest_x4", 4), 2)) {
      assertFails(() -> run(r, new float[5], 8, 8), Reason.SHAPE_MISMATCH);
    }
  }

  @Test
  void closingTwiceIsHarmlessAndRunningAfterCloseFails() throws Exception {
    TileRunner r = new OnnxEngine().open(TestModels.named("nearest_x4", 4), 2);
    r.close();
    r.close();
    assertFails(() -> run(r, gradient(4, 4), 4, 4), Reason.FAILURE);
  }

  @Test
  void aCancelledTokenStopsBeforeRunning() throws Exception {
    CancellationToken token = CancellationToken.create();
    token.cancel();
    try (TileRunner r = new OnnxEngine().open(TestModels.named("nearest_x4", 4), 2)) {
      assertThatThrownBy(() -> r.run(gradient(4, 4), 4, 4, token))
          .isInstanceOf(CancelledException.class);
    }
  }

  @Test
  void cancellingDuringARunStopsItAndLeavesTheSessionUsable() throws Exception {
    AtomicReference<CancellationToken> current = new AtomicReference<>(CancellationToken.none());
    OnnxEngine engine =
        new OnnxEngine(
            () -> {
              CancellationToken t = current.get();
              if (t != CancellationToken.none()) {
                t.cancel();
              }
            });
    try (TileRunner r = engine.open(TestModels.named("slow", 1), 2)) {
      float[] in = gradient(16, 16);
      float[] before = run(r, in, 16, 16);

      CancellationToken token = CancellationToken.create();
      current.set(token);
      assertThatThrownBy(() -> r.run(gradient(256, 256), 256, 256, token))
          .isInstanceOf(CancelledException.class);

      current.set(CancellationToken.none());
      assertThat(run(r, in, 16, 16)).containsExactly(before);
    }
  }

  private interface Call {
    void run() throws Exception;
  }

  private static void assertFails(Call call, Reason reason) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(AiFailure.class, e -> assertThat(e.reason()).isEqualTo(reason));
  }
}
