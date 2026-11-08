package io.github.erenriquezp.rora.app.model;

/**
 * Cortina antes/después. {@code position} es la fracción de la anchura de la imagen en {@code [0,
 * 1]}: la línea se mueve con la imagen al hacer zoom o pan.
 */
public record Curtain(double position) {

  public static final Curtain CENTER = new Curtain(0.5);

  /** Distancia en píxeles lógicos a la que el puntero agarra la cortina. */
  public static final double GRAB_TOLERANCE = 8;

  private static final double KEY_STEP = 0.02;
  private static final double FINE_KEY_STEP = 0.002;

  public Curtain {
    position = Math.max(0, Math.min(1, position));
  }

  /** Posición en pantalla, limitada a la parte visible de la imagen. */
  public double screenX(ViewTransform t) {
    double x = t.tx() + position * t.imageWidth() * t.scale();
    return Math.max(visibleStart(t), Math.min(visibleEnd(t), x));
  }

  /** Cortina situada en {@code sx}, limitada a la parte visible de la imagen. */
  public static Curtain atScreenX(double sx, ViewTransform t) {
    double x = Math.max(visibleStart(t), Math.min(visibleEnd(t), sx));
    return new Curtain((x - t.tx()) / (t.imageWidth() * t.scale()));
  }

  public boolean isGrabbed(double sx, ViewTransform t) {
    return Math.abs(sx - screenX(t)) <= GRAB_TOLERANCE;
  }

  /** Movimiento con el teclado; {@code fine} para el paso pequeño. */
  public Curtain nudged(int direction, boolean fine) {
    return new Curtain(position + direction * (fine ? FINE_KEY_STEP : KEY_STEP));
  }

  private static double visibleStart(ViewTransform t) {
    return Math.max(0, t.tx());
  }

  private static double visibleEnd(ViewTransform t) {
    return Math.min(t.viewportWidth(), t.tx() + t.imageWidth() * t.scale());
  }
}
