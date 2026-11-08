package io.github.erenriquezp.rora.core.op;

/**
 * Patrón de acceso a datos de una operación: decide cómo se trocea en bandas y qué filas puede leer
 * cada banda (su <i>halo</i>).
 */
public sealed interface OpKind {

  /** Puntual sin dependencia de la posición. */
  OpKind POINT = new Point(false);

  /** Puntual que usa {@code (x, y, ancho, alto)}, p. ej. una viñeta. */
  OpKind POSITIONAL = new Point(true);

  /** Cualquier píxel de salida puede depender de cualquier píxel de entrada. */
  OpKind GLOBAL = new Global();

  /** Filas adicionales, arriba y abajo de la banda, que la operación puede leer. */
  int haloRows();

  /** Las bandas deben empezar en múltiplos de este número de filas. */
  int rowAlign();

  /**
   * {@code out(x,y)} depende solo de {@code src(x,y)} (y de la posición, si {@code positional}).
   */
  record Point(boolean positional) implements OpKind {
    @Override
    public int haloRows() {
      return 0;
    }

    @Override
    public int rowAlign() {
      return 1;
    }
  }

  /**
   * {@code out(x,y)} depende de las filas {@code y - radius .. y + radius} de la entrada. Con
   * {@code rowAlign > 1} las bandas se alinean (p. ej. a la rejilla de bloques al pixelar).
   */
  record Neighborhood(int radius, int rowAlign) implements OpKind {
    public Neighborhood {
      if (radius < 0 || rowAlign < 1) {
        throw new IllegalArgumentException("radius=" + radius + ", rowAlign=" + rowAlign);
      }
    }

    @Override
    public int haloRows() {
      return radius;
    }
  }

  /** Global: la banda puede leer la imagen entera. */
  record Global() implements OpKind {
    @Override
    public int haloRows() {
      return Integer.MAX_VALUE;
    }

    @Override
    public int rowAlign() {
      return 1;
    }
  }
}
