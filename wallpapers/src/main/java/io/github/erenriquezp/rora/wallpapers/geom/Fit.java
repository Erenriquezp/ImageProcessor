package io.github.erenriquezp.rora.wallpapers.geom;

/**
 * Geometría de cubrir y contener. Funciones puras, sin píxeles.
 *
 * <p><b>Convención de muestreo</b>: el centro del píxel de salida {@code i} (de {@code n}) cae en
 * la coordenada continua de la fuente {@code r0 + (i + 0,5)·len/n}; su índice continuo es ese valor
 * menos 0,5 (ver {@link #sourceIndex}). Con el encuadre igual a la fuente y {@code n} igual a su
 * tamaño, cada píxel cae exactamente en sí mismo.
 */
public final class Fit {

  private Fit() {}

  /** Índice continuo de la fuente al que corresponde el centro del píxel de salida {@code i}. */
  public static double sourceIndex(int i, double r0, double len, int n) {
    return r0 + (i + 0.5) * len / n - 0.5;
  }

  /**
   * Mayor rectángulo con proporción {@code width / height} dentro de {@code sw × sh}, centrado. El
   * lado limitante mide exactamente lo que la fuente.
   */
  public static FrameRect cover(int sw, int sh, int width, int height) {
    check(sw, sh);
    check(width, height);
    double cw;
    double ch;
    // Productos enteros antes de dividir: con la misma proporción el resultado es exacto.
    if ((long) sw * height > (long) sh * width) {
      ch = sh;
      cw = (double) sh * width / height;
    } else {
      cw = sw;
      ch = (double) sw * height / width;
    }
    return new FrameRect((sw - cw) / 2, (sh - ch) / 2, cw, ch);
  }

  /**
   * Dónde va la fuente entera dentro de una salida {@code width × height} sin recortar: rectángulo
   * entero centrado. La deformación por redondear es menor de medio píxel por eje.
   */
  public static IntRect contain(int sw, int sh, int width, int height) {
    check(sw, sh);
    check(width, height);
    double s = Math.min((double) width / sw, (double) height / sh);
    int cw = (int) Math.max(1, Math.min(width, Math.round(sw * s)));
    int ch = (int) Math.max(1, Math.min(height, Math.round(sh * s)));
    return new IntRect((width - cw) / 2, (height - ch) / 2, cw, ch);
  }

  /** Fracción del área de la fuente que queda fuera del encuadre, en {@code [0, 1]}. */
  public static double lostArea(int sw, int sh, FrameRect frame) {
    double kept =
        Math.max(0, Math.min(sw, frame.right()) - Math.max(0, frame.x()))
            * Math.max(0, Math.min(sh, frame.bottom()) - Math.max(0, frame.y()));
    return Math.max(0, Math.min(1, 1 - kept / ((double) sw * sh)));
  }

  /** Factor por el que se amplía la fuente: {@code > 1} amplía, {@code < 1} reduce. */
  public static double scale(FrameRect frame, int width) {
    return width / frame.w();
  }

  private static void check(int w, int h) {
    if (w < 1 || h < 1) {
      throw new IllegalArgumentException("dimensiones no válidas: " + w + "x" + h);
    }
  }
}
