package io.github.erenriquezp.rora.app.model;

/**
 * Rectángulo de recorte normalizado a {@code [0, 1]}, sin JavaFX. Con proporción fija, arrastrar un
 * asa conserva la proporción en píxeles de imagen.
 *
 * @param ratio ancho/alto en píxeles de imagen; {@code 0} = libre
 * @param imageAspect ancho/alto de la imagen
 */
public record CropModel(
    double left, double top, double right, double bottom, double ratio, double imageAspect) {

  /** Asas de la herramienta. */
  public enum Handle {
    NW,
    N,
    NE,
    E,
    SE,
    S,
    SW,
    W,
    BODY
  }

  private static final double MIN_SIZE = 0.02;

  public static CropModel full(double imageAspect) {
    return new CropModel(0, 0, 1, 1, 0, imageAspect);
  }

  public boolean isFull() {
    return left <= 0 && top <= 0 && right >= 1 && bottom >= 1;
  }

  public double width() {
    return right - left;
  }

  public double height() {
    return bottom - top;
  }

  /** Con otra proporción: el mayor rectángulo centrado en el actual que quepa dentro. */
  public CropModel withRatio(double newRatio) {
    if (newRatio <= 0) {
      return new CropModel(left, top, right, bottom, 0, imageAspect);
    }
    double normalized = newRatio / imageAspect;
    double w = width();
    double h = w / normalized;
    if (h > height()) {
      h = height();
      w = h * normalized;
    }
    double cx = (left + right) / 2;
    double cy = (top + bottom) / 2;
    return new CropModel(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, newRatio, imageAspect);
  }

  /** Desplaza todo el rectángulo sin salirse de la imagen. */
  public CropModel movedBy(double dx, double dy) {
    double x = Math.max(-left, Math.min(1 - right, dx));
    double y = Math.max(-top, Math.min(1 - bottom, dy));
    return new CropModel(left + x, top + y, right + x, bottom + y, ratio, imageAspect);
  }

  /** Arrastra {@code handle} hasta el punto normalizado {@code (x, y)}. */
  public CropModel dragged(Handle handle, double x, double y) {
    double l = left;
    double t = top;
    double r = right;
    double b = bottom;
    double cx = clamp(x, 0, 1);
    double cy = clamp(y, 0, 1);
    switch (handle) {
      case NW -> {
        l = cx;
        t = cy;
      }
      case NE -> {
        r = cx;
        t = cy;
      }
      case SE -> {
        r = cx;
        b = cy;
      }
      case SW -> {
        l = cx;
        b = cy;
      }
      case N -> t = cy;
      case S -> b = cy;
      case E -> r = cx;
      case W -> l = cx;
      case BODY -> {
        return this;
      }
    }
    l = Math.min(l, right - MIN_SIZE == l ? l : Math.min(l, r - MIN_SIZE));
    t = Math.min(t, b - MIN_SIZE);
    r = Math.max(r, l + MIN_SIZE);
    b = Math.max(b, t + MIN_SIZE);
    CropModel free = new CropModel(l, t, Math.min(r, 1), Math.min(b, 1), ratio, imageAspect);
    return ratio > 0 ? free.fitRatio(handle) : free;
  }

  /** Ajusta al ratio manteniendo fija la esquina o el borde opuesto al asa arrastrada. */
  private CropModel fitRatio(Handle handle) {
    double normalized = ratio / imageAspect;
    double w = width();
    double h = height();
    boolean driveByHeight = handle == Handle.N || handle == Handle.S;
    if (driveByHeight) {
      w = h * normalized;
    } else {
      h = w / normalized;
    }
    double anchorX =
        switch (handle) {
          case NW, SW, W -> right;
          case NE, SE, E -> left;
          default -> (left + right) / 2;
        };
    double anchorY =
        switch (handle) {
          case NW, NE, N -> bottom;
          case SW, SE, S -> top;
          default -> (top + bottom) / 2;
        };
    double spaceX =
        switch (handle) {
          case NW, SW, W -> anchorX;
          case NE, SE, E -> 1 - anchorX;
          default -> 2 * Math.min(anchorX, 1 - anchorX);
        };
    double spaceY =
        switch (handle) {
          case NW, NE, N -> anchorY;
          case SW, SE, S -> 1 - anchorY;
          default -> 2 * Math.min(anchorY, 1 - anchorY);
        };
    double k = Math.min(1, Math.min(spaceX / w, spaceY / h));
    w *= k;
    h *= k;
    double l =
        switch (handle) {
          case NW, SW, W -> anchorX - w;
          case NE, SE, E -> anchorX;
          default -> anchorX - w / 2;
        };
    double t =
        switch (handle) {
          case NW, NE, N -> anchorY - h;
          case SW, SE, S -> anchorY;
          default -> anchorY - h / 2;
        };
    return new CropModel(l, t, l + w, t + h, ratio, imageAspect);
  }

  private static double clamp(double v, double min, double max) {
    return Math.max(min, Math.min(max, v));
  }
}
