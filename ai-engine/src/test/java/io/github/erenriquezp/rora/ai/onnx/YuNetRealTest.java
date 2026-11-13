package io.github.erenriquezp.rora.ai.onnx;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.wallpapers.subject.Subject;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * YuNet con una foto real de dominio público ({@code faces/NOTICE.md}). Fuera de {@code verify}:
 * {@code -Dgroups=ai-real -DexcludedGroups=}.
 */
@Tag("ai-real")
class YuNetRealTest {

  private static Img load() throws Exception {
    try (InputStream in = YuNetRealTest.class.getResourceAsStream("/faces/nasa-s69-31741.jpg")) {
      Assumptions.assumeTrue(in != null, "falta faces/nasa-s69-31741.jpg");
      BufferedImage b = ImageIO.read(in);
      int[] px = b.getRGB(0, 0, b.getWidth(), b.getHeight(), null, 0, b.getWidth());
      return Img.adopt(b.getWidth(), b.getHeight(), px);
    }
  }

  @Test
  void findsTheAstronautsFaceTheSameWayEveryTime() throws Exception {
    Img img = load();
    try (YuNetDetector d = new YuNetDetector()) {
      List<Subject> a = d.detect(img, CancellationToken.none());
      List<Subject> b = d.detect(img, CancellationToken.none());

      assertThat(b).as("bit a bit").isEqualTo(a);
      assertThat(a).hasSize(1);
      Subject f = a.getFirst();
      // Retrato de medio cuerpo: la cara está en la mitad superior y centrada en horizontal.
      assertThat(f.x() + f.w() / 2).isBetween(0.3, 0.7);
      assertThat(f.y() + f.h() / 2).isBetween(0.1, 0.5);
      assertThat(f.w()).isBetween(0.08, 0.5);
      assertThat(f.score()).isGreaterThan(0.8);
      Subject.Point eyes = f.landmarks().orElseThrow().rightEye();
      assertThat(eyes.y()).isBetween(f.y(), f.y() + f.h() / 2);
    }
  }
}
