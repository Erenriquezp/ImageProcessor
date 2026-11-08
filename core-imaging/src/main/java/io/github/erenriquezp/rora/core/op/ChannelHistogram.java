package io.github.erenriquezp.rora.core.op;

/** Histograma de 256 niveles por canal R, G y B. Las copias protegen la inmutabilidad. */
public final class ChannelHistogram {

  private final int[] red;
  private final int[] green;
  private final int[] blue;

  public ChannelHistogram(int[] red, int[] green, int[] blue) {
    if (red.length != 256 || green.length != 256 || blue.length != 256) {
      throw new IllegalArgumentException("Se esperaban 256 niveles por canal");
    }
    this.red = red.clone();
    this.green = green.clone();
    this.blue = blue.clone();
  }

  public int[] red() {
    return red.clone();
  }

  public int[] green() {
    return green.clone();
  }

  public int[] blue() {
    return blue.clone();
  }
}
