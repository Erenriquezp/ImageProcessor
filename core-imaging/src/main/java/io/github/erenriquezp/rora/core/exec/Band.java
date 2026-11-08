package io.github.erenriquezp.rora.core.exec;

/** Rango de filas {@code [y0, y1)} de la imagen, a lo ancho completo. */
public record Band(int y0, int y1) {

  public Band {
    if (y0 < 0 || y1 <= y0) {
      throw new IllegalArgumentException("Banda no válida: [" + y0 + ", " + y1 + ")");
    }
  }

  public int rows() {
    return y1 - y0;
  }
}
