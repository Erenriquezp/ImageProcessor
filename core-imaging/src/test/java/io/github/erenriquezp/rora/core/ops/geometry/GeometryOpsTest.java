package io.github.erenriquezp.rora.core.ops.geometry;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy.Size;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.Params;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("property")
class GeometryOpsTest {

  private static final Operation CROP = new CropOp();
  private static final Operation FLIP = new FlipOp();
  private static final Operation ROTATE = new Rotate90Op();
  private static final Operation STRAIGHTEN = new StraightenOp();
  private static final Operation RESIZE = new ResizeOp();

  private static Params params(Operation op, Object... kv) {
    Params.Builder b = op.schema().defaults().toBuilder();
    for (int i = 0; i < kv.length; i += 2) {
      b.set((String) kv[i], kv[i + 1]);
    }
    return b.build();
  }

  private static Img run(Operation op, Img src, Object... kv) {
    return op.apply(src, params(op, kv), OpContext.sequential()).image();
  }

  private static Img noise(int w, int h, long seed) {
    SplittableRandom rnd = new SplittableRandom(seed);
    int[] px = new int[w * h];
    for (int i = 0; i < px.length; i++) {
      px[i] = rnd.nextInt();
    }
    return Img.adopt(w, h, px);
  }

  @Test
  void cropCopiesExactlyTheRequestedRectangle() {
    Img src = noise(10, 8, 1);
    Img out = run(CROP, src, "left", 0.2, "top", 0.25, "right", 0.7, "bottom", 0.75);
    assertThat(out.width()).isEqualTo(5);
    assertThat(out.height()).isEqualTo(4);
    for (int y = 0; y < 4; y++) {
      for (int x = 0; x < 5; x++) {
        assertThat(out.argb(x, y)).isEqualTo(src.argb(x + 2, y + 2));
      }
    }
  }

  @Test
  void cropKeepsTheSameFractionAtAnyResolution() {
    Params p = params(CROP, "left", 0.25, "top", 0.5, "right", 0.75, "bottom", 1.0);
    assertThat(CROP.outputSize(new Size(4000, 2000), p)).isEqualTo(new Size(2000, 1000));
    assertThat(CROP.outputSize(new Size(1000, 500), p)).isEqualTo(new Size(500, 250));
  }

  @Test
  void anEmptyOrInvertedCropStillGivesAValidImage() {
    Img src = noise(10, 8, 2);
    assertThat(run(CROP, src, "left", 0.5, "right", 0.5).width()).isEqualTo(1);
    assertThat(run(CROP, src, "left", 0.7, "right", 0.2).width()).isEqualTo(5);
  }

  @Test
  void flippingTwiceIsTheIdentity() {
    Img src = noise(13, 7, 3);
    for (String axis : new String[] {"horizontal", "vertical"}) {
      Img out = run(FLIP, run(FLIP, src, "axis", axis), "axis", axis);
      assertThat(out.sameContent(src)).as(axis).isTrue();
    }
  }

  @Test
  void flipMovesPixelsToTheMirrorPosition() {
    Img src = noise(5, 3, 4);
    assertThat(run(FLIP, src, "axis", "horizontal").argb(0, 1)).isEqualTo(src.argb(4, 1));
    assertThat(run(FLIP, src, "axis", "vertical").argb(1, 0)).isEqualTo(src.argb(1, 2));
  }

  @Test
  void fourQuarterTurnsAreTheIdentity() {
    Img src = noise(11, 6, 5);
    Img out = src;
    for (int i = 0; i < 4; i++) {
      out = run(ROTATE, out, "turns", 1);
    }
    assertThat(out.sameContent(src)).isTrue();
  }

  @Test
  void aQuarterTurnIsClockwiseAndSwapsTheSides() {
    Img src = noise(4, 2, 6);
    Img out = run(ROTATE, src, "turns", 1);
    assertThat(out.width()).isEqualTo(2);
    assertThat(out.height()).isEqualTo(4);
    // La esquina superior izquierda pasa a la superior derecha.
    assertThat(out.argb(1, 0)).isEqualTo(src.argb(0, 0));
    assertThat(out.argb(0, 0)).isEqualTo(src.argb(0, 1));
  }

  @Test
  void straightenZeroIsAnExactCopy() {
    Img src = noise(20, 10, 7);
    assertThat(run(STRAIGHTEN, src, "degrees", 0.0).sameContent(src)).isTrue();
  }

  @Test
  void straightenCropsToAnInscribedRectangleWithTheSameProportion() {
    Size out = STRAIGHTEN.outputSize(new Size(4000, 3000), params(STRAIGHTEN, "degrees", 5.0));
    assertThat(out.width()).isLessThan(4000);
    assertThat(Math.abs((double) out.width() / out.height() - 4.0 / 3)).isLessThan(0.01);
  }

  @Test
  void straightenOfAUniformImageIsUniform() {
    int[] px = new int[50 * 30];
    java.util.Arrays.fill(px, 0xFF3070B0);
    Img out = run(STRAIGHTEN, Img.adopt(50, 30, px), "degrees", 12.5);
    for (int y = 0; y < out.height(); y++) {
      for (int x = 0; x < out.width(); x++) {
        assertThat(out.argb(x, y)).isEqualTo(0xFF3070B0);
      }
    }
  }

  @Test
  void resizeZeroOrTheSameSizeKeepsThePixels() {
    Img src = noise(20, 10, 8);
    assertThat(run(RESIZE, src, "longSide", 0).sameContent(src)).isTrue();
    assertThat(run(RESIZE, src, "longSide", 20).sameContent(src)).isTrue();
  }

  @Test
  void resizeKeepsTheProportion() {
    Img out = run(RESIZE, noise(400, 200, 9), "longSide", 100);
    assertThat(out.width()).isEqualTo(100);
    assertThat(out.height()).isEqualTo(50);
  }

  @Test
  void enlargingIsCappedAtFourTimes() {
    assertThat(RESIZE.outputSize(new Size(10, 5), params(RESIZE, "longSide", 16384)))
        .isEqualTo(new Size(40, 20));
  }

  @Test
  void enlargingAUniformImageStaysUniform() {
    int[] px = new int[12 * 8];
    java.util.Arrays.fill(px, 0xFFA05020);
    Img out = run(RESIZE, Img.adopt(12, 8, px), "longSide", 40);
    assertThat(out.width()).isEqualTo(40);
    for (int y = 0; y < out.height(); y++) {
      for (int x = 0; x < out.width(); x++) {
        assertThat(out.argb(x, y)).isEqualTo(0xFFA05020);
      }
    }
  }

  @Test
  void thePreviewOfAResizeNeverEnlargesTheProxy() {
    Params p = params(RESIZE, "longSide", 8000);
    Size fullIn = new Size(6000, 4000);
    Size previewIn = new Size(2000, 1333);
    Params forPreview = RESIZE.forPreview(p, 2000.0 / 6000, fullIn, previewIn);
    assertThat(RESIZE.outputSize(previewIn, forPreview)).isEqualTo(previewIn);
  }

  @Test
  void thePreviewOfAStrongReductionUsesTheFinalSize() {
    Params p = params(RESIZE, "longSide", 1000);
    Size previewIn = new Size(2000, 1333);
    Params forPreview = RESIZE.forPreview(p, 2000.0 / 6000, new Size(6000, 4000), previewIn);
    assertThat(RESIZE.outputSize(previewIn, forPreview).longSide()).isEqualTo(1000);
  }
}
