package io.github.erenriquezp.rora.ai.face;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Downscale;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.wallpapers.subject.Subject;
import io.github.erenriquezp.rora.wallpapers.subject.Subject.Landmarks;
import io.github.erenriquezp.rora.wallpapers.subject.Subject.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Entrada y salida de YuNet ({@code face_detection_yunet_2023mar}) en Java puro: letterbox a 640,
 * decodificación de los tres niveles (pasos 8, 16 y 32), NMS y orden estable. No usa ORT: se prueba
 * con tensores sintéticos.
 */
public final class YuNetDecoder {

  /** Lado de la entrada del modelo. */
  public static final int SIZE = 640;

  /** Pasos de los tres niveles de salida. */
  static final int[] STRIDES = {8, 16, 32};

  /** Confianza mínima (la del experimento de la Puerta 0). */
  public static final double MIN_SCORE = 0.6;

  /** Solape máximo entre cajas que se conservan. */
  static final double NMS_IOU = 0.3;

  /** Las coordenadas se cuantizan a esta fracción: absorbe diferencias de los últimos bits. */
  static final double QUANTUM = 1.0 / 4096;

  private YuNetDecoder() {}

  /**
   * Tensor {@code [1, 3, 640, 640]} en BGR 0–255 con la imagen reducida (por área, si es mayor)
   * arriba a la izquierda y el resto en negro.
   */
  public record Letterbox(float[] chw, int contentWidth, int contentHeight) {}

  /**
   * Salidas del modelo por nombre ({@code cls_8}, {@code obj_8}, {@code bbox_8}, {@code kps_8}...).
   */
  @FunctionalInterface
  public interface Outputs {
    float[] get(String name);
  }

  /** Prepara la entrada. */
  public static Letterbox input(Img image, TileExecutor executor, CancellationToken token) {
    Img small = image;
    int w = image.width();
    int h = image.height();
    if (Math.max(w, h) > SIZE) {
      double k = (double) SIZE / Math.max(w, h);
      int dw = (int) Math.max(1, Math.min(SIZE, Math.round(w * k)));
      int dh = (int) Math.max(1, Math.min(SIZE, Math.round(h * k)));
      small = Downscale.areaAverage(image, dw, dh, executor, token, Progress.NONE);
    }
    int sw = small.width();
    int sh = small.height();
    float[] chw = new float[3 * SIZE * SIZE];
    int[] row = new int[sw];
    for (int y = 0; y < sh; y++) {
      small.copyRow(y, row, 0);
      for (int x = 0; x < sw; x++) {
        int p = row[x];
        int o = y * SIZE + x;
        chw[o] = p & 0xFF;
        chw[SIZE * SIZE + o] = (p >> 8) & 0xFF;
        chw[2 * SIZE * SIZE + o] = (p >> 16) & 0xFF;
      }
    }
    return new Letterbox(chw, sw, sh);
  }

  /**
   * Caras normalizadas a la imagen, por área descendente y luego {@code x} e {@code y}.
   *
   * @param contentWidth ancho de la imagen dentro del letterbox
   */
  public static List<Subject> decode(Outputs out, int contentWidth, int contentHeight) {
    List<Subject> raw = new ArrayList<>();
    for (int s : STRIDES) {
      float[] cls = out.get("cls_" + s);
      float[] obj = out.get("obj_" + s);
      float[] box = out.get("bbox_" + s);
      float[] kps = out.get("kps_" + s);
      int cols = SIZE / s;
      int anchors = cols * cols;
      if (cls.length != anchors
          || obj.length != anchors
          || box.length != 4 * anchors
          || kps.length != 10 * anchors) {
        throw new IllegalArgumentException("salidas de YuNet con forma inesperada en el paso " + s);
      }
      for (int i = 0; i < anchors; i++) {
        double score = Math.sqrt(clamp01(cls[i]) * clamp01(obj[i]));
        if (!(score >= MIN_SCORE)) {
          continue;
        }
        int row = i / cols;
        int col = i % cols;
        double cx = (col + box[4 * i]) * s;
        double cy = (row + box[4 * i + 1]) * s;
        double bw = Math.exp(box[4 * i + 2]) * s;
        double bh = Math.exp(box[4 * i + 3]) * s;
        if (!(bw > 0 && bh > 0 && Double.isFinite(bw) && Double.isFinite(bh))) {
          continue;
        }
        Point[] p = new Point[5];
        for (int j = 0; j < 5; j++) {
          p[j] =
              new Point(
                  q((col + kps[10 * i + 2 * j]) * s / contentWidth),
                  q((row + kps[10 * i + 2 * j + 1]) * s / contentHeight));
        }
        raw.add(
            new Subject(
                q((cx - bw / 2) / contentWidth),
                q((cy - bh / 2) / contentHeight),
                Math.max(QUANTUM, q(bw / contentWidth)),
                Math.max(QUANTUM, q(bh / contentHeight)),
                q(score),
                Subject.Kind.FACE,
                Optional.of(new Landmarks(p[0], p[1], p[2], p[3], p[4]))));
      }
    }
    List<Subject> kept = nms(raw);
    kept.sort(
        Comparator.comparingDouble((Subject f) -> -f.area())
            .thenComparingDouble(Subject::x)
            .thenComparingDouble(Subject::y));
    return kept;
  }

  /** Supresión de no máximos: por confianza descendente (desempate por posición). */
  static List<Subject> nms(List<Subject> in) {
    List<Subject> sorted = new ArrayList<>(in);
    sorted.sort(
        Comparator.comparingDouble((Subject f) -> -f.score())
            .thenComparingDouble(Subject::x)
            .thenComparingDouble(Subject::y));
    List<Subject> keep = new ArrayList<>();
    for (Subject b : sorted) {
      boolean ok = true;
      for (Subject k : keep) {
        if (iou(b, k) > NMS_IOU) {
          ok = false;
          break;
        }
      }
      if (ok) {
        keep.add(b);
      }
    }
    return keep;
  }

  static double iou(Subject a, Subject b) {
    double x0 = Math.max(a.x(), b.x());
    double y0 = Math.max(a.y(), b.y());
    double x1 = Math.min(a.x() + a.w(), b.x() + b.w());
    double y1 = Math.min(a.y() + a.h(), b.y() + b.h());
    double inter = Math.max(0, x1 - x0) * Math.max(0, y1 - y0);
    return inter / (a.area() + b.area() - inter);
  }

  private static double q(double v) {
    return Math.round(v / QUANTUM) * QUANTUM;
  }

  private static double clamp01(double v) {
    return v > 0 ? Math.min(1, v) : 0;
  }
}
