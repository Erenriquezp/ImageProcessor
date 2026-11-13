package io.github.erenriquezp.rora.ai.face;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;
import io.github.erenriquezp.rora.wallpapers.subject.Subject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class YuNetDecoderTest {

  /** Salidas vacías (confianza 0) con la forma real del modelo. */
  static Map<String, float[]> empty() {
    Map<String, float[]> m = new HashMap<>();
    for (int s : YuNetDecoder.STRIDES) {
      int n = (YuNetDecoder.SIZE / s) * (YuNetDecoder.SIZE / s);
      m.put("cls_" + s, new float[n]);
      m.put("obj_" + s, new float[n]);
      m.put("bbox_" + s, new float[4 * n]);
      m.put("kps_" + s, new float[10 * n]);
    }
    return m;
  }

  /** Pone una cara en el ancla (fila, columna) del paso {@code s}. */
  static void put(
      Map<String, float[]> m,
      int s,
      int row,
      int col,
      float score,
      float dx,
      float dy,
      int w,
      int h) {
    int i = row * (YuNetDecoder.SIZE / s) + col;
    m.get("cls_" + s)[i] = score;
    m.get("obj_" + s)[i] = score;
    float[] b = m.get("bbox_" + s);
    b[4 * i] = dx;
    b[4 * i + 1] = dy;
    b[4 * i + 2] = (float) Math.log((double) w / s);
    b[4 * i + 3] = (float) Math.log((double) h / s);
    float[] k = m.get("kps_" + s);
    for (int j = 0; j < 5; j++) {
      k[10 * i + 2 * j] = 0.1f * j;
      k[10 * i + 2 * j + 1] = 0.2f * j;
    }
  }

  @Test
  void decodesABoxAndItsLandmarks() {
    Map<String, float[]> m = empty();
    put(m, 16, 10, 20, 0.9f, 0.5f, 0.25f, 64, 80);

    List<Subject> faces = YuNetDecoder.decode(m::get, 640, 480);

    assertThat(faces).hasSize(1);
    Subject f = faces.getFirst();
    double cx = 20.5 * 16;
    double cy = 10.25 * 16;
    double q = YuNetDecoder.QUANTUM;
    assertThat(f.x()).isCloseTo((cx - 32) / 640, offset(q));
    assertThat(f.y()).isCloseTo((cy - 40) / 480, offset(q));
    assertThat(f.w()).isCloseTo(64 / 640.0, offset(q));
    assertThat(f.h()).isCloseTo(80 / 480.0, offset(q));
    assertThat(f.score()).isCloseTo(0.9, offset(1e-3));
    Subject.Landmarks l = f.landmarks().orElseThrow();
    assertThat(l.leftEye().x()).isCloseTo((20 + 0.1) * 16 / 640, offset(q));
    assertThat(l.mouthLeft().y()).isCloseTo((10 + 0.8) * 16 / 480, offset(q));
    assertThat(f.x() / q).as("cuantizada").isCloseTo(Math.rint(f.x() / q), offset(1e-6));
  }

  @Test
  void dropsLowConfidence() {
    Map<String, float[]> m = empty();
    put(m, 8, 5, 5, 0.59f, 0, 0, 20, 20);
    assertThat(YuNetDecoder.decode(m::get, 640, 640)).isEmpty();
  }

  @Test
  void suppressesOverlapsKeepingTheMostConfident() {
    Map<String, float[]> m = empty();
    put(m, 8, 20, 20, 0.8f, 0, 0, 40, 40);
    put(m, 8, 20, 21, 0.95f, 0, 0, 40, 40);
    put(m, 32, 2, 2, 0.7f, 0, 0, 100, 120);

    List<Subject> faces = YuNetDecoder.decode(m::get, 640, 640);

    assertThat(faces).hasSize(2);
    assertThat(faces.getFirst().w()).as("orden por área").isGreaterThan(faces.get(1).w());
    assertThat(faces.get(1).score()).isCloseTo(0.95, offset(1e-3));
  }

  @Test
  void theOrderDoesNotDependOnTheAnchorOrder() {
    Map<String, float[]> a = empty();
    put(a, 8, 10, 10, 0.9f, 0, 0, 30, 30);
    put(a, 16, 30, 30, 0.8f, 0, 0, 30, 30);
    Map<String, float[]> b = empty();
    put(b, 16, 30, 30, 0.8f, 0, 0, 30, 30);
    put(b, 8, 10, 10, 0.9f, 0, 0, 30, 30);

    assertThat(YuNetDecoder.decode(b::get, 640, 640))
        .isEqualTo(YuNetDecoder.decode(a::get, 640, 640));
  }

  @Test
  void rejectsUnexpectedShapes() {
    Map<String, float[]> m = empty();
    m.put("kps_32", new float[3]);
    assertThatThrownBy(() -> YuNetDecoder.decode(m::get, 640, 640))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void letterboxesTopLeftInBgr() {
    int[] px = new int[1280 * 720];
    java.util.Arrays.fill(px, 0xFF102030);
    YuNetDecoder.Letterbox lb =
        YuNetDecoder.input(
            Img.adopt(1280, 720, px), TileExecutor.sequential(), CancellationToken.none());

    assertThat(lb.contentWidth()).isEqualTo(640);
    assertThat(lb.contentHeight()).isEqualTo(360);
    int plane = 640 * 640;
    assertThat(lb.chw()[0]).isEqualTo(0x30);
    assertThat(lb.chw()[plane]).isEqualTo(0x20);
    assertThat(lb.chw()[2 * plane]).isEqualTo(0x10);
    assertThat(lb.chw()[359 * 640 + 639]).isEqualTo(0x30);
    assertThat(lb.chw()[360 * 640]).isZero();
  }

  @Test
  void smallImagesAreNotEnlarged() {
    YuNetDecoder.Letterbox lb =
        YuNetDecoder.input(
            Img.create(100, 50), TileExecutor.sequential(), CancellationToken.none());
    assertThat(lb.contentWidth()).isEqualTo(100);
    assertThat(lb.contentHeight()).isEqualTo(50);
  }

  @Test
  void theBundledModelMatchesItsHash() throws AiFailure {
    assertThat(YuNetModel.readVerified()).hasSize((int) YuNetModel.BYTES);
    assertThatThrownBy(() -> YuNetModel.verified(new byte[] {1, 2, 3}, YuNetModel.SHA256))
        .isInstanceOfSatisfying(
            AiFailure.class, e -> assertThat(e.reason()).isEqualTo(AiFailure.Reason.MODEL_CORRUPT));
  }
}
