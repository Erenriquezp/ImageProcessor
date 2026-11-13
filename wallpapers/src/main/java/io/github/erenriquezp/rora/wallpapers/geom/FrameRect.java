package io.github.erenriquezp.rora.wallpapers.geom;

/**
 * Rectángulo de encuadre en píxeles de la fuente, con coordenadas fraccionarias (no se redondea: el
 * remuestreador muestrea posiciones continuas). El borde izquierdo del píxel {@code 0} es {@code
 * 0}.
 */
public record FrameRect(double x, double y, double w, double h) {

  public FrameRect {
    if (!(Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(w) && Double.isFinite(h))
        || w <= 0
        || h <= 0) {
      throw new IllegalArgumentException(
          "encuadre no válido: " + x + ", " + y + ", " + w + ", " + h);
    }
  }

  public double right() {
    return x + w;
  }

  public double bottom() {
    return y + h;
  }

  public double centerX() {
    return x + w / 2;
  }

  public double centerY() {
    return y + h / 2;
  }

  public double aspect() {
    return w / h;
  }

  /** Si cabe en una fuente de {@code sw × sh} (con tolerancia de redondeo). */
  public boolean insideOf(int sw, int sh) {
    double eps = 1e-9 * Math.max(sw, sh);
    return x >= -eps && y >= -eps && right() <= sw + eps && bottom() <= sh + eps;
  }

  /** El mismo tamaño, desplazado lo mínimo para quedar dentro de la fuente. */
  public FrameRect clampedInto(int sw, int sh) {
    if (w > sw * (1 + 1e-12) || h > sh * (1 + 1e-12)) {
      throw new IllegalArgumentException("el encuadre es mayor que la fuente");
    }
    double nx = Math.max(0, Math.min(sw - w, x));
    double ny = Math.max(0, Math.min(sh - h, y));
    return new FrameRect(nx, ny, w, h);
  }

  /** Normalizado a {@code [0, 1]} respecto a la fuente. */
  public FrameRect normalized(int sw, int sh) {
    return new FrameRect(x / sw, y / sh, w / sw, h / sh);
  }

  /** Inverso de {@link #normalized}. */
  public FrameRect denormalized(int sw, int sh) {
    return new FrameRect(x * sw, y * sh, w * sw, h * sh);
  }
}
