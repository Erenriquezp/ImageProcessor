package io.github.erenriquezp.rora.core.ops.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy.Size;
import io.github.erenriquezp.rora.core.op.ArtifactLookup;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.op.RenderLevel;
import io.github.erenriquezp.rora.core.op.Renderer;
import io.github.erenriquezp.rora.core.op.ResultCache;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EnhanceOpTest {

  private final EnhanceOp op = new EnhanceOp();

  private Params params(String scale, double intensity, int artifact) {
    return Params.builder(op.schema())
        .set("scale", scale)
        .set("intensity", intensity)
        .set("artifact", artifact)
        .build();
  }

  private static Img solid(int w, int h, int argb) {
    int[] p = new int[w * h];
    java.util.Arrays.fill(p, argb);
    return Img.adopt(w, h, p);
  }

  private static OpContext with(ArtifactLookup lookup) {
    return new OpContext(
        TileExecutor.sequential(), CancellationToken.none(), Progress.NONE, lookup);
  }

  private static ArtifactLookup store(Map<Integer, Img> full) {
    return new ArtifactLookup() {
      @Override
      public Optional<Img> full(int id) {
        return Optional.ofNullable(full.get(id));
      }

      @Override
      public Optional<Img> proxy(int id, int w, int h) {
        return Optional.ofNullable(full.get(id)).map(i -> solid(w, h, i.argb(0, 0)));
      }
    };
  }

  @Test
  void isRegisteredAndHasAStableId() {
    assertThat(OpRegistry.load().require("ai.enhance")).isInstanceOf(EnhanceOp.class);
  }

  @Test
  void withoutAnArtifactTheClassicResultHasTheOutputSize() {
    Img out =
        op.apply(solid(30, 20, 0xFF808080), params("X4", 100, 0), OpContext.sequential()).image();
    assertThat(out.width()).isEqualTo(120);
    assertThat(out.height()).isEqualTo(80);
  }

  @Test
  void aPreviewWithoutAnArtifactReturnsTheInputAtItsOwnSize() {
    Img src = solid(30, 20, 0xFF808080);
    Params p = op.forPreview(params("X4", 100, 0), 0.5, new Size(60, 40), new Size(30, 20));
    assertThat(op.outputSize(new Size(30, 20), p)).isEqualTo(new Size(30, 20));
    assertThat(op.apply(src, p, OpContext.sequential()).image()).isSameAs(src);
  }

  @Test
  void fullIntensityUsesTheArtifactWithoutCopyingIt() {
    Img artifact = solid(40, 40, 0xFF102030);
    Img out =
        op.apply(solid(20, 20, 0xFF808080), params("X2", 100, 7), with(store(Map.of(7, artifact))))
            .image();
    assertThat(out).isSameAs(artifact);
  }

  @Test
  void aPreviewUsesTheReducedArtifact() {
    Img artifact = solid(40, 40, 0xFF102030);
    Params p = op.forPreview(params("X2", 100, 7), 1, new Size(20, 20), new Size(20, 20));
    Img out = op.apply(solid(20, 20, 0xFF808080), p, with(store(Map.of(7, artifact)))).image();
    assertThat(out.width()).isEqualTo(20);
    assertThat(out.argb(5, 5)).isEqualTo(0xFF102030);
  }

  @Test
  void intensityBlendsThePreviewBetweenInputAndArtifact() {
    Img artifact = solid(20, 20, 0xFF000000);
    Params p = op.forPreview(params("X2", 50, 7), 1, new Size(20, 20), new Size(20, 20));
    Img out = op.apply(solid(20, 20, 0xFFC8C8C8), p, with(store(Map.of(7, artifact)))).image();
    assertThat((out.argb(3, 3) >> 16) & 0xFF).isEqualTo(100);
  }

  @Test
  void zeroIntensityEqualsTheClassicResult() {
    Img src = solid(20, 20, 0xFF606060);
    Img classic = op.apply(src, params("X2", 0, 0), OpContext.sequential()).image();
    Img out =
        op.apply(src, params("X2", 0, 7), with(store(Map.of(7, solid(40, 40, 0xFFFFFFFF)))))
            .image();
    assertThat(out.sameContent(classic)).isTrue();
  }

  @Test
  void aMissingArtifactIsAnErrorAndNeverSilentlyClassic() {
    Img src = solid(20, 20, 0xFF606060);
    assertThatThrownBy(() -> op.apply(src, params("X2", 100, 9), OpContext.sequential()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void anArtifactOfTheWrongSizeIsRejected() {
    Img src = solid(20, 20, 0xFF606060);
    ArtifactLookup wrong = store(Map.of(7, solid(50, 50, 0xFF000000)));
    assertThatThrownBy(() -> op.apply(src, params("X2", 100, 7), with(wrong)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void theRendererNeverUpscalesInAPreviewEvenAtFullSize() {
    Img src = solid(30, 20, 0xFF808080);
    Renderer r = new Renderer(OpRegistry.load(), new ResultCache(1 << 24), true);
    Renderer.SourceImage s = new Renderer.SourceImage(1, src);
    OpSpec step = new OpSpec(EnhanceOp.ID, params("X4", 100, 0), true);
    Renderer.Request req =
        new Renderer.Request(s, List.of(step), 1, new RenderLevel.Preview(30, 20));
    Renderer.Result res = r.render(req, OpContext.sequential());
    assertThat(res.image().width()).isEqualTo(30);
    assertThat(res.image()).isSameAs(src);
  }

  @Test
  void anAssignedArtifactChangesTheCacheKey() {
    Renderer r = new Renderer(OpRegistry.load(), new ResultCache(1 << 20), true);
    Renderer.SourceImage s = new Renderer.SourceImage(1, solid(4, 4, 0));
    var a = new OpSpec(EnhanceOp.ID, params("X2", 100, 0), true);
    var b = new OpSpec(EnhanceOp.ID, params("X2", 100, 3), true);
    assertThat(r.keyOf(new Renderer.Request(s, List.of(a), 1, RenderLevel.FULL)))
        .isNotEqualTo(r.keyOf(new Renderer.Request(s, List.of(b), 1, RenderLevel.FULL)));
  }
}
