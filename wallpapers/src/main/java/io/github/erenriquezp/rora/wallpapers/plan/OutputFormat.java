package io.github.erenriquezp.rora.wallpapers.plan;

import io.github.erenriquezp.rora.core.io.FormatId;

/**
 * Formato de salida de la tanda (D3). Auto: PNG si el destino lleva relleno suave (desenfoque o
 * degradado), porque el tramado solo sobrevive en PNG; JPEG si solo es recorte.
 */
public enum OutputFormat {
  AUTO,
  JPEG,
  PNG;

  /** Formato de un destino concreto. */
  public FormatId resolve(PlannedTarget t) {
    return switch (this) {
      case JPEG -> FormatId.JPEG;
      case PNG -> FormatId.PNG;
      case AUTO -> t.filled() && t.target().fill().smooth() ? FormatId.PNG : FormatId.JPEG;
    };
  }

  /** El tramado solo se aplica cuando se escribe PNG. */
  public static boolean dithers(FormatId format) {
    return format == FormatId.PNG;
  }
}
