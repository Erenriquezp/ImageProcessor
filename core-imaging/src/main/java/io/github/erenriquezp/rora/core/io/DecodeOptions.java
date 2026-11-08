package io.github.erenriquezp.rora.core.io;

/**
 * Opciones de decodificación.
 *
 * @param retainedBytes memoria que la sesión ya retiene y no se puede liberar
 * @param maxPixels tope explícito de píxeles (0 = solo el derivado de la memoria)
 */
public record DecodeOptions(long retainedBytes, long maxPixels) {

  public static final DecodeOptions DEFAULT = new DecodeOptions(0, 0);
}
