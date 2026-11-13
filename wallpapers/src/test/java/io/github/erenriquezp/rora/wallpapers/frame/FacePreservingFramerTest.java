package io.github.erenriquezp.rora.wallpapers.frame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import io.github.erenriquezp.rora.wallpapers.frame.FacePreservingFramer.Framing;
import io.github.erenriquezp.rora.wallpapers.geom.Fit;
import io.github.erenriquezp.rora.wallpapers.geom.FrameRect;
import io.github.erenriquezp.rora.wallpapers.subject.Subject;
import io.github.erenriquezp.rora.wallpapers.subject.Subject.Landmarks;
import io.github.erenriquezp.rora.wallpapers.subject.Subject.Point;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FacePreservingFramerTest {

  /** Las cuatro proporciones de la Puerta 0. */
  static final int[][] RATIOS = {{16, 9}, {90, 195}, {21, 9}, {4, 3}};

  static Subject face(double x, double y, double w, double h) {
    return new Subject(x, y, w, h, 0.9, Subject.Kind.FACE, Optional.empty());
  }

  static Subject faceWithPoints(double x, double y, double w, double h) {
    // Ojos al 40 % del alto de la caja, boca al 75 %.
    Landmarks l =
        new Landmarks(
            new Point(x + 0.3 * w, y + 0.4 * h),
            new Point(x + 0.7 * w, y + 0.4 * h),
            new Point(x + 0.5 * w, y + 0.55 * h),
            new Point(x + 0.35 * w, y + 0.75 * h),
            new Point(x + 0.65 * w, y + 0.75 * h));
    return new Subject(x, y, w, h, 0.9, Subject.Kind.FACE, Optional.of(l));
  }

  @Test
  void withoutFacesItIsTheCentredCover() {
    for (int[] r : RATIOS) {
      Framing f = FacePreservingFramer.frame(4000, 3000, r[0], r[1], List.of());
      assertThat(f.frame()).isEqualTo(Fit.cover(4000, 3000, r[0], r[1]));
      assertThat(f.allSubjectsInside()).isTrue();
    }
  }

  @Test
  void aFaceThatTheCentreAlreadyKeepsDoesNotMoveTheFrame() {
    Subject s = face(0.45, 0.4, 0.1, 0.13);
    for (int[] r : RATIOS) {
      Framing f = FacePreservingFramer.frame(4000, 3000, r[0], r[1], List.of(s));
      assertThat(f.frame()).isEqualTo(Fit.cover(4000, 3000, r[0], r[1]));
    }
  }

  @Test
  void aKnownFaceIsInsideInEveryRatioWhenItFits() {
    Subject high = face(0.15, 0.05, 0.08, 0.06);
    for (int[] r : RATIOS) {
      Framing f = FacePreservingFramer.frame(4000, 3000, r[0], r[1], List.of(high));
      assertThat(f.allSubjectsInside()).as("%d:%d", r[0], r[1]).isTrue();
      assertThat(f.frame().insideOf(4000, 3000)).isTrue();
      assertThat(f.frame().w()).isEqualTo(Fit.cover(4000, 3000, r[0], r[1]).w());
    }
  }

  @Test
  void theFrameMovesOnlyAsMuchAsNeeded() {
    // Retrato 3000x4000 hacia 16:9: el centrado empieza en y = 1156,25 y corta una cara alta.
    Subject s = face(0.4, 0.1, 0.1, 0.075);
    Framing f = FacePreservingFramer.frame(3000, 4000, 16, 9, List.of(s));
    double faceTop = 0.1 * 4000;
    double ch = 3000 * 9 / 16.0;
    double air = Math.min(0.5 * 300, 0.05 * ch);
    assertThat(f.frame().y()).isCloseTo(faceTop - air, offset(1e-9));
    assertThat(f.allSubjectsInside()).isTrue();
  }

  @Test
  void twoFacesThatDoNotFitKeepTheLargest() {
    Subject big = face(0.02, 0.3, 0.12, 0.2);
    Subject small = face(0.85, 0.3, 0.08, 0.13);
    Framing f = FacePreservingFramer.frame(4000, 3000, 90, 195, List.of(small, big));
    double bx = 0.02 * 4000;
    assertThat(f.frame().x()).isLessThanOrEqualTo(bx);
    assertThat(f.frame().right()).isGreaterThanOrEqualTo((0.02 + 0.12) * 4000);
    assertThat(f.allSubjectsInside()).isFalse();
    assertThat(f.subjects().getFirst()).isEqualTo(big);
  }

  @Test
  void aFaceLargerThanTheFrameIsCentredOnEyesAndMouth() {
    // Selfi cerrado 650x860 hacia 21:9 (como la foto 1): la cara no cabe en vertical.
    Subject s = faceWithPoints(0.253, 0.128, 0.578, 0.573);
    Framing f = FacePreservingFramer.frame(650, 860, 21, 9, List.of(s));
    double mid = (0.128 + 0.5 * (0.4 + 0.75) * 0.573) * 860;
    assertThat(f.frame().centerY()).isCloseTo(mid, offset(1e-9));
    assertThat(f.allSubjectsInside()).isFalse();
  }

  @Test
  void withoutLandmarksItCentresOnTheBox() {
    Subject s = face(0.253, 0.128, 0.578, 0.573);
    Framing f = FacePreservingFramer.frame(650, 860, 21, 9, List.of(s));
    assertThat(f.frame().centerY()).isCloseTo((0.128 + 0.573 / 2) * 860, offset(1e-9));
  }

  @Test
  void tinyFacesAreIgnored() {
    Subject tiny = face(0.01, 0.01, 0.005, 0.006);
    Framing f = FacePreservingFramer.frame(4000, 3000, 9, 16, List.of(tiny));
    assertThat(f.frame()).isEqualTo(Fit.cover(4000, 3000, 9, 16));
    assertThat(f.subjects()).isEmpty();
  }

  @Test
  void itIsDeterministicAndIndependentOfInputOrder() {
    List<Subject> faces =
        List.of(
            face(0.1, 0.2, 0.05, 0.08), face(0.7, 0.25, 0.05, 0.08), face(0.4, 0.1, 0.06, 0.09));
    FrameRect a = FacePreservingFramer.frame(3840, 2160, 9, 16, faces).frame();
    FrameRect b =
        FacePreservingFramer.frame(
                3840, 2160, 9, 16, List.of(faces.get(2), faces.get(0), faces.get(1)))
            .frame();
    assertThat(b).isEqualTo(a);
  }

  /** La misma escena a dos escalas da el mismo encuadre normalizado. */
  @ParameterizedTest
  @CsvSource({"16,9", "90,195", "21,9", "4,3"})
  void isInvariantToTheSourceScale(int w, int h) {
    List<Subject> faces = List.of(face(0.3, 0.12, 0.07, 0.1), face(0.62, 0.2, 0.05, 0.07));
    FrameRect full =
        FacePreservingFramer.frame(6000, 4000, w, h, faces).frame().normalized(6000, 4000);
    FrameRect half =
        FacePreservingFramer.frame(3000, 2000, w, h, faces).frame().normalized(3000, 2000);
    assertThat(half.x()).isCloseTo(full.x(), offset(1e-3));
    assertThat(half.y()).isCloseTo(full.y(), offset(1e-3));
    assertThat(half.w()).isCloseTo(full.w(), offset(1e-3));
  }

  @Test
  void faceBoxesThatLeaveTheImageOnlyNeedTheVisiblePart() {
    Subject edge = face(-0.02, 0.4, 0.1, 0.15);
    Framing f = FacePreservingFramer.frame(4000, 3000, 9, 16, List.of(edge));
    assertThat(f.frame().x()).isZero();
    assertThat(f.allSubjectsInside()).isTrue();
  }
}
