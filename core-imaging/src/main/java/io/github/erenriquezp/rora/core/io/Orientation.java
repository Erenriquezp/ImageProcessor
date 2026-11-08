package io.github.erenriquezp.rora.core.io;

/** Orientación EXIF 1..8: se aplica a los píxeles al decodificar. */
public final class Orientation {

  private Orientation() {}

  /** Valor válido, o 1 si está fuera de rango. */
  public static int normalize(int value) {
    return value >= 1 && value <= 8 ? value : 1;
  }

  /** ¿La orientación intercambia ancho y alto? */
  public static boolean swapsAxes(int orientation) {
    return orientation >= 5 && orientation <= 8;
  }

  /**
   * Aplica la orientación a {@code px}. Las orientaciones 1 a 4 trabajan sobre el propio array; de
   * 5 a 8 devuelven uno nuevo, de {@code h} columnas por {@code w} filas.
   */
  public static int[] apply(int orientation, int[] px, int w, int h) {
    switch (orientation) {
      case 2 -> mirrorRows(px, w, h);
      case 3 -> reverse(px, 0, px.length);
      case 4 -> swapRows(px, w, h);
      case 5, 6, 7, 8 -> {
        return rotate(orientation, px, w, h);
      }
      default -> {
        // 1: ya está derecha.
      }
    }
    return px;
  }

  private static void mirrorRows(int[] px, int w, int h) {
    for (int y = 0; y < h; y++) {
      reverse(px, y * w, w);
    }
  }

  private static void reverse(int[] px, int off, int n) {
    for (int i = off, j = off + n - 1; i < j; i++, j--) {
      int t = px[i];
      px[i] = px[j];
      px[j] = t;
    }
  }

  private static void swapRows(int[] px, int w, int h) {
    int[] row = new int[w];
    for (int a = 0, b = h - 1; a < b; a++, b--) {
      int ao = a * w;
      int bo = b * w;
      System.arraycopy(px, ao, row, 0, w);
      System.arraycopy(px, bo, px, ao, w);
      System.arraycopy(row, 0, px, bo, w);
    }
  }

  private static int[] rotate(int orientation, int[] px, int w, int h) {
    int[] out = new int[px.length];
    boolean flipX = orientation == 6 || orientation == 7; // destino x' = h-1-y
    boolean flipY = orientation == 7 || orientation == 8; // destino y' = w-1-x
    for (int y = 0, i = 0; y < h; y++) {
      int dx = flipX ? h - 1 - y : y;
      for (int x = 0; x < w; x++, i++) {
        int dy = flipY ? w - 1 - x : x;
        out[dy * h + dx] = px[i];
      }
    }
    return out;
  }
}
