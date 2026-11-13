package io.github.erenriquezp.rora.wallpapers.geom;

/** Rectángulo entero en píxeles de la salida. */
public record IntRect(int x, int y, int w, int h) {

  public IntRect {
    if (w < 1 || h < 1) {
      throw new IllegalArgumentException("rectángulo vacío: " + w + "x" + h);
    }
  }

  public int right() {
    return x + w;
  }

  public int bottom() {
    return y + h;
  }

  public boolean contains(int px, int py) {
    return px >= x && px < right() && py >= y && py < bottom();
  }
}
