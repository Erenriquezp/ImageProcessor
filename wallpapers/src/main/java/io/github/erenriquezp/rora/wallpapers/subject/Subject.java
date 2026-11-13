package io.github.erenriquezp.rora.wallpapers.subject;

import java.util.Objects;
import java.util.Optional;

/**
 * Sujeto detectado, con coordenadas normalizadas a {@code [0, 1]} respecto a la imagen analizada.
 *
 * @param score confianza en {@code [0, 1]}
 */
public record Subject(
    double x,
    double y,
    double w,
    double h,
    double score,
    Kind kind,
    Optional<Landmarks> landmarks) {

  /** Tipo de sujeto. */
  public enum Kind {
    FACE
  }

  /** Punto normalizado. */
  public record Point(double x, double y) {}

  /** Cinco puntos de una cara (como los da YuNet). */
  public record Landmarks(
      Point rightEye, Point leftEye, Point nose, Point mouthRight, Point mouthLeft) {

    public Landmarks {
      Objects.requireNonNull(rightEye, "rightEye");
      Objects.requireNonNull(leftEye, "leftEye");
      Objects.requireNonNull(nose, "nose");
      Objects.requireNonNull(mouthRight, "mouthRight");
      Objects.requireNonNull(mouthLeft, "mouthLeft");
    }

    /** Punto medio entre el centro de los ojos y el de la boca. */
    public Point eyesMouthCenter() {
      double ex = (rightEye.x() + leftEye.x()) / 2;
      double ey = (rightEye.y() + leftEye.y()) / 2;
      double mx = (mouthRight.x() + mouthLeft.x()) / 2;
      double my = (mouthRight.y() + mouthLeft.y()) / 2;
      return new Point((ex + mx) / 2, (ey + my) / 2);
    }
  }

  public Subject {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(landmarks, "landmarks");
    if (!(Double.isFinite(x)
        && Double.isFinite(y)
        && w > 0
        && h > 0
        && Double.isFinite(w)
        && Double.isFinite(h))) {
      throw new IllegalArgumentException("caja no válida: " + x + ", " + y + ", " + w + ", " + h);
    }
    if (!(score >= 0 && score <= 1)) {
      throw new IllegalArgumentException("confianza fuera de [0, 1]: " + score);
    }
  }

  public double area() {
    return w * h;
  }
}
