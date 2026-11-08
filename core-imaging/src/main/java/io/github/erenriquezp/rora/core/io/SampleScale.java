package io.github.erenriquezp.rora.core.io;

/** Reducción de muestras de 16 a 8 bits, con redondeo. */
public final class SampleScale {

  private SampleScale() {}

  /** Muestra de 16 bits (0..65535) a 8 bits, redondeando. */
  public static int to8(int v16) {
    return (v16 * 255 + 32767) / 65535;
  }
}
