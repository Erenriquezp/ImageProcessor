package io.github.erenriquezp.rora.ai.onnx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.CancelledException;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;
import io.github.erenriquezp.rora.wallpapers.subject.Subject;
import io.github.erenriquezp.rora.wallpapers.subject.SubjectDetector;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** El modelo incluido de verdad sobre ORT, con imágenes sintéticas (sin caras). */
class YuNetDetectorTest {

  static Img noise(int w, int h, long seed) {
    Random r = new Random(seed);
    int[] px = new int[w * h];
    for (int i = 0; i < px.length; i++) {
      px[i] = 0xFF000000 | (r.nextInt() & 0xFFFFFF);
    }
    return Img.adopt(w, h, px);
  }

  @Test
  void findsNothingInSyntheticImagesAndIsRepeatable() throws Exception {
    try (SubjectDetector d = new YuNetDetector()) {
      assertThat(d.id()).isEqualTo("yunet-2023mar");
      assertThat(d.detect(Img.create(800, 600), CancellationToken.none())).isEmpty();
      Img n = noise(1024, 768, 7);
      List<Subject> a = d.detect(n, CancellationToken.none());
      assertThat(d.detect(n, CancellationToken.none())).isEqualTo(a);
    }
  }

  @Test
  void honoursCancellationAndClose() throws AiFailure {
    YuNetDetector d = new YuNetDetector();
    CancellationToken t = CancellationToken.create();
    t.cancel();
    assertThatThrownBy(() -> d.detect(Img.create(10, 10), t))
        .isInstanceOf(CancelledException.class);
    d.close();
    assertThatThrownBy(() -> d.detect(Img.create(10, 10), CancellationToken.none()))
        .isInstanceOf(AiFailure.class);
  }
}
