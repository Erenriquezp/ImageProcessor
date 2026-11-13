package io.github.erenriquezp.rora.wallpapers.frame;

import io.github.erenriquezp.rora.wallpapers.geom.Fit;
import io.github.erenriquezp.rora.wallpapers.geom.FrameRect;
import io.github.erenriquezp.rora.wallpapers.subject.Subject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Encuadre automático "centrado protegido por rostros" (v3, validado a ciegas en la Puerta 0: no
 * perdió frente al centrado en 13 de 13 hojas y no cortó ningún rostro).
 *
 * <ol>
 *   <li>Parte del rectángulo centrado de escala máxima (sin zoom).
 *   <li>Sin rostros, lo devuelve tal cual.
 *   <li>Por eje, añade rostros de mayor a menor mientras su unión con margen quepa: 10 % del lado
 *       de la caja a los lados y abajo; arriba, el menor de media cara y un 5 % del recorte.
 *   <li>Se desplaza lo mínimo desde el centrado para contener esa unión.
 *   <li>Si no cabe con margen, lo intenta sin margen; si ni la cara mayor cabe en un eje, centra
 *       ese eje entre los ojos y la boca.
 * </ol>
 */
public final class FacePreservingFramer {

  /** Caras con lado menor que esta fracción del lado menor de la imagen se ignoran. */
  public static final double MIN_FACE = 0.015;

  static final double SIDE_MARGIN = 0.10;
  static final double TOP_FACE = 0.5;
  static final double TOP_FRAME = 0.05;

  private FacePreservingFramer() {}

  /**
   * @param frame encuadre elegido
   * @param allSubjectsInside si todas las cajas de cara (sin margen) quedan dentro
   * @param subjects caras consideradas (tras descartar las diminutas), de mayor a menor
   */
  public record Framing(FrameRect frame, boolean allSubjectsInside, List<Subject> subjects) {
    public Framing {
      subjects = List.copyOf(subjects);
    }
  }

  /** Encuadre de cubrir para una salida {@code width × height} sobre una fuente {@code sw × sh}. */
  public static Framing frame(int sw, int sh, int width, int height, List<Subject> subjects) {
    FrameRect c = Fit.cover(sw, sh, width, height);
    List<Subject> faces = relevant(sw, sh, subjects);
    if (faces.isEmpty()) {
      return new Framing(c, true, faces);
    }
    double x = axis(c.x(), c.w(), sw, faces, true);
    double y = axis(c.y(), c.h(), sh, faces, false);
    FrameRect f = new FrameRect(x, y, c.w(), c.h());
    return new Framing(f, allInside(f, sw, sh, faces), faces);
  }

  /** Caras de tamaño suficiente, de mayor a menor (desempate por x e y). */
  static List<Subject> relevant(int sw, int sh, List<Subject> subjects) {
    double min = MIN_FACE * Math.min(sw, sh);
    List<Subject> out = new ArrayList<>();
    for (Subject s : subjects) {
      if (s.kind() == Subject.Kind.FACE && Math.min(s.w() * sw, s.h() * sh) >= min) {
        out.add(s);
      }
    }
    out.sort(
        Comparator.comparingDouble((Subject s) -> -s.area())
            .thenComparingDouble(Subject::x)
            .thenComparingDouble(Subject::y));
    return out;
  }

  /** Si todas las cajas quedan dentro de {@code f} (tolerancia de redondeo). */
  public static boolean allInside(FrameRect f, int sw, int sh, List<Subject> faces) {
    double eps = 1e-6;
    for (Subject s : faces) {
      double x0 = s.x() * sw;
      double y0 = s.y() * sh;
      double x1 = (s.x() + s.w()) * sw;
      double y1 = (s.y() + s.h()) * sh;
      // Una caja que se sale de la imagen solo exige quedar dentro hasta el borde de la imagen.
      x0 = Math.max(0, x0);
      y0 = Math.max(0, y0);
      x1 = Math.min(sw, x1);
      y1 = Math.min(sh, y1);
      if (x0 < f.x() - eps || y0 < f.y() - eps || x1 > f.right() + eps || y1 > f.bottom() + eps) {
        return false;
      }
    }
    return true;
  }

  private static double axis(double start, double len, int full, List<Subject> faces, boolean h) {
    for (boolean withMargin : new boolean[] {true, false}) {
      double lo = Double.MAX_VALUE;
      double hi = -Double.MAX_VALUE;
      int taken = 0;
      for (Subject s : faces) {
        double b0 = (h ? s.x() : s.y()) * full;
        double bl = (h ? s.w() : s.h()) * full;
        double before;
        double after;
        if (!withMargin) {
          before = 0;
          after = 0;
        } else if (h) {
          before = SIDE_MARGIN * bl;
          after = SIDE_MARGIN * bl;
        } else {
          before = Math.min(TOP_FACE * bl, TOP_FRAME * len);
          after = SIDE_MARGIN * bl;
        }
        double n0 = Math.max(0, b0 - before);
        double n1 = Math.min(full, b0 + bl + after);
        double nlo = Math.min(lo, n0);
        double nhi = Math.max(hi, n1);
        if (nhi - nlo <= len + 1e-9) {
          lo = nlo;
          hi = nhi;
          taken++;
        } else if (taken == 0) {
          break;
        }
      }
      if (taken > 0) {
        double pos = Math.max(hi - len, Math.min(lo, start));
        return Math.max(0, Math.min(full - len, pos));
      }
    }
    Subject big = faces.getFirst();
    double mid;
    if (big.landmarks().isPresent()) {
      Subject.Point p = big.landmarks().get().eyesMouthCenter();
      mid = (h ? p.x() : p.y()) * full;
    } else {
      mid = (h ? big.x() + big.w() / 2 : big.y() + big.h() / 2) * full;
    }
    return Math.max(0, Math.min(full - len, mid - len / 2));
  }
}
