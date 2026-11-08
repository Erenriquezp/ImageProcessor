package io.github.erenriquezp.rora.core.internal;

import io.github.erenriquezp.rora.core.image.Img;

/**
 * Acceso sin copia al array interno de {@link Img}, solo para el propio módulo (este paquete no se
 * exporta). {@code Img} registra el accesor en su inicializador estático, que siempre se ha
 * ejecutado si existe alguna instancia.
 */
public final class ImgAccess {

  /** Devuelve el array interno de una imagen. */
  @FunctionalInterface
  public interface Accessor {
    int[] pixels(Img img);
  }

  private static volatile Accessor accessor;

  private ImgAccess() {}

  /** Lo llama {@code Img} una única vez. */
  public static void setAccessor(Accessor a) {
    if (accessor != null) {
      throw new IllegalStateException("El accesor de Img ya está registrado");
    }
    accessor = a;
  }

  /** Array interno de {@code img}: <b>solo lectura</b> salvo para quien lo acaba de crear. */
  public static int[] pixels(Img img) {
    return accessor.pixels(img);
  }
}
