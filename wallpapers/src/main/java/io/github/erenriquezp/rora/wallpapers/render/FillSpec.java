package io.github.erenriquezp.rora.wallpapers.render;

import java.util.Objects;

/**
 * Relleno de los márgenes en "Contener".
 *
 * @param strength oscurecimiento en {@code [0, 1]} (en luz lineal); no se aplica al sólido
 * @param solidArgb color del relleno sólido (se fuerza opaco)
 */
public record FillSpec(Kind kind, double strength, int solidArgb) {

  /** Tipo de relleno. */
  public enum Kind {
    BLUR,
    GRADIENT,
    MIRROR,
    SOLID
  }

  /** Oscurecimiento por defecto (punto de partida; se afina con una hoja de contacto). */
  public static final double DEFAULT_STRENGTH = 0.35;

  public FillSpec {
    Objects.requireNonNull(kind, "kind");
    if (!(strength >= 0 && strength <= 1)) {
      throw new IllegalArgumentException("intensidad fuera de [0, 1]: " + strength);
    }
    solidArgb |= 0xFF000000;
  }

  /** Desenfoque con la intensidad por defecto. */
  public static FillSpec blur() {
    return new FillSpec(Kind.BLUR, DEFAULT_STRENGTH, 0xFF000000);
  }

  /** Si el relleno tiene degradados suaves (y por tanto se beneficia del tramado y de PNG). */
  public boolean smooth() {
    return kind == Kind.BLUR || kind == Kind.GRADIENT;
  }
}
