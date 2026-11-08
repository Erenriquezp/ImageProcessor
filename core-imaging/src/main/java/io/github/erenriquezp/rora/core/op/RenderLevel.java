package io.github.erenriquezp.rora.core.op;

/** Nivel de render: la imagen completa (exacta) o un proxy reducido para el preview. */
public sealed interface RenderLevel {

  /** Resolución completa: resultado exacto. */
  RenderLevel FULL = new Full();

  /** Resolución completa. */
  record Full() implements RenderLevel {}

  /** Proxy de {@code width × height} (aproximado por diseño). */
  record Preview(int width, int height) implements RenderLevel {
    public Preview {
      if (width < 1 || height < 1) {
        throw new IllegalArgumentException(width + "x" + height);
      }
    }
  }
}
