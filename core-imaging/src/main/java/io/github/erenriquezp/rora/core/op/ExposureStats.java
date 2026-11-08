package io.github.erenriquezp.rora.core.op;

/**
 * Recuento del diagnóstico de exposición: píxeles con R, G y B ≥ 240 ({@code burned}) y con R, G y
 * B ≤ 15 ({@code dark}). Sin porcentajes ni texto: el formato depende del locale y se hace fuera
 * del núcleo.
 */
public record ExposureStats(long total, long burned, long dark) {

  public ExposureStats {
    if (total < 1 || burned < 0 || dark < 0 || burned > total || dark > total) {
      throw new IllegalArgumentException(
          "total=" + total + ", burned=" + burned + ", dark=" + dark);
    }
  }
}
