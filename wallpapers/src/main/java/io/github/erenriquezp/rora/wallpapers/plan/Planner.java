package io.github.erenriquezp.rora.wallpapers.plan;

import io.github.erenriquezp.rora.wallpapers.frame.FacePreservingFramer;
import io.github.erenriquezp.rora.wallpapers.geom.Fit;
import io.github.erenriquezp.rora.wallpapers.geom.FrameRect;
import io.github.erenriquezp.rora.wallpapers.geom.IntRect;
import io.github.erenriquezp.rora.wallpapers.plan.PlannedTarget.Warning;
import io.github.erenriquezp.rora.wallpapers.plan.Target.FitMode;
import io.github.erenriquezp.rora.wallpapers.preset.SafeZone;
import io.github.erenriquezp.rora.wallpapers.subject.Subject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Resuelve los destinos contra una fuente sin tocar píxeles: modo, encuadre, escala, insignia y
 * avisos. Es barato (se recalcula en cada gesto) y determinista.
 */
public final class Planner {

  /** Auto pasa a "Contener" si "Cubrir" pierde más que esta fracción del área. */
  public static final double MAX_LOST_AREA = 0.80;

  /** Fracción de una cara bajo una zona segura a partir de la que se avisa. */
  public static final double SAFE_ZONE_OVERLAP = 0.30;

  /** Tolerancia de proporción para aceptar un encuadre manual. */
  static final double MANUAL_ASPECT_TOLERANCE = 0.005;

  private Planner() {}

  /** Plan de todos los destinos para una fuente de {@code sw × sh} con estos sujetos. */
  public static WallpaperPlan plan(int sw, int sh, List<Subject> subjects, List<Target> targets) {
    List<PlannedTarget> out = new ArrayList<>();
    for (Target t : targets) {
      out.add(plan(sw, sh, subjects, t));
    }
    return new WallpaperPlan(sw, sh, subjects, out, requiredEnhanceScale(out));
  }

  /** Un destino. */
  public static PlannedTarget plan(int sw, int sh, List<Subject> subjects, Target t) {
    int w = t.width();
    int h = t.height();
    Set<Warning> warnings = EnumSet.noneOf(Warning.class);
    FacePreservingFramer.Framing auto = FacePreservingFramer.frame(sw, sh, w, h, subjects);
    FrameRect frame = null;
    FitMode mode;
    if (t.manualFrame().isPresent()) {
      FrameRect m = t.manualFrame().get().denormalized(sw, sh);
      double want = (double) w / h;
      if (Math.abs(m.aspect() / want - 1) <= MANUAL_ASPECT_TOLERANCE
          && m.w() <= sw * (1 + 1e-9)
          && m.h() <= sh * (1 + 1e-9)) {
        frame = m.clampedInto(sw, sh);
      } else {
        warnings.add(Warning.MANUAL_RESET);
      }
    }
    if (frame != null) {
      mode = FitMode.COVER;
    } else if (t.fit() == FitMode.AUTO) {
      boolean contain =
          !auto.allSubjectsInside() || Fit.lostArea(sw, sh, auto.frame()) > MAX_LOST_AREA;
      mode = contain ? FitMode.CONTAIN : FitMode.COVER;
    } else {
      mode = t.fit();
    }
    IntRect content;
    double scale;
    if (mode == FitMode.CONTAIN) {
      frame = new FrameRect(0, 0, sw, sh);
      content = Fit.contain(sw, sh, w, h);
      scale = (double) content.w() / sw;
      warnings.add(Warning.FILLED);
    } else {
      if (frame == null) {
        frame = auto.frame();
      }
      content = new IntRect(0, 0, w, h);
      scale = w / frame.w();
      if (!FacePreservingFramer.allInside(frame, sw, sh, auto.subjects())) {
        warnings.add(Warning.SUBJECT_CUT);
      }
    }
    QualityBadge badge = QualityBadge.of(scale);
    if (badge == QualityBadge.BLURRY) {
      warnings.add(Warning.BLURRY);
    }
    if (t.approximate()) {
      warnings.add(Warning.APPROXIMATE_PRESET);
    }
    if (overlapsSafeZone(t, frame, content, sw, sh, auto.subjects())) {
      warnings.add(Warning.SAFE_ZONE_OVERLAP);
    }
    return new PlannedTarget(
        t, mode, frame, content, scale, badge, Fit.lostArea(sw, sh, frame), warnings);
  }

  private static boolean overlapsSafeZone(
      Target t, FrameRect frame, IntRect content, int sw, int sh, List<Subject> faces) {
    for (Subject s : faces) {
      // Caja de la cara en píxeles de salida.
      double kx = content.w() / frame.w();
      double ky = content.h() / frame.h();
      double x0 = content.x() + (s.x() * sw - frame.x()) * kx;
      double y0 = content.y() + (s.y() * sh - frame.y()) * ky;
      double fw = s.w() * sw * kx;
      double fh = s.h() * sh * ky;
      double area = fw * fh;
      for (SafeZone z : t.safeZones()) {
        double zx = z.x() * t.width();
        double zy = z.y() * t.height();
        double ix = Math.min(x0 + fw, zx + z.w() * t.width()) - Math.max(x0, zx);
        double iy = Math.min(y0 + fh, zy + z.h() * t.height()) - Math.max(y0, zy);
        if (ix > 0 && iy > 0 && ix * iy / area > SAFE_ZONE_OVERLAP) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Escala de mejora con IA que conviene, una sola vez para todos: si el peor destino amplía más de
   * {@link QualityBadge#SUGGEST_AI}, la menor de {2, 4} que lo deja en "Excelente"; si ninguna lo
   * consigue, 4.
   */
  static OptionalInt requiredEnhanceScale(List<PlannedTarget> targets) {
    double worst = 0;
    for (PlannedTarget p : targets) {
      worst = Math.max(worst, p.effectiveScale());
    }
    if (worst <= QualityBadge.SUGGEST_AI) {
      return OptionalInt.empty();
    }
    for (int k : new int[] {2, 4}) {
      if (worst / k <= QualityBadge.EXCELLENT_MAX) {
        return OptionalInt.of(k);
      }
    }
    return OptionalInt.of(4);
  }
}
