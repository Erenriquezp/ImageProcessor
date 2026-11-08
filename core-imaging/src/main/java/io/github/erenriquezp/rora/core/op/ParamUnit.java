package io.github.erenriquezp.rora.core.op;

/**
 * Unidad de un parámetro numérico. El preview sobre un proxy reducido escala los parámetros en
 * {@link #PIXELS} por el factor del proxy para que el efecto se parezca al de la imagen completa;
 * el render completo usa siempre el valor original.
 */
public enum ParamUnit {
  /** Sin unidad espacial (factores, umbrales, niveles…): no se escala. */
  NONE,
  /** Distancia o tamaño en píxeles de la imagen (bloque, desplazamiento…). */
  PIXELS
}
