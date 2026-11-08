package io.github.erenriquezp.rora.core.op;

import io.github.erenriquezp.rora.core.image.Img;

/**
 * Operación de imagen (SPI). Las implementaciones se publican con {@code provides Operation with
 * ...} en {@code module-info} y se descubren con {@link OpRegistry#load()}.
 *
 * <p>Contrato:
 *
 * <ul>
 *   <li><b>Pura</b>: no modifica {@code src} ni guarda estado entre llamadas; mismo resultado para
 *       la misma entrada, con cualquier número de hilos.
 *   <li>Constructor público sin argumentos (requisito de {@code ServiceLoader}).
 *   <li>{@link #id()} es estable: no se reutiliza para otra operación.
 * </ul>
 */
public interface Operation {

  /** Id estable, p. ej. {@code "point.saturation"}. */
  String id();

  ParamSchema schema();

  OpMeta meta();

  /** Patrón de acceso con estos parámetros; por defecto, el de {@link #meta()}. */
  default OpKind kind(Params params) {
    return meta().kind();
  }

  /**
   * Promete que {@link #apply} devuelve <b>la misma instancia</b> de {@code src} con estos
   * parámetros (p. ej. la ecualización con factor 0 y sin marcas). Por defecto, {@code false}. Lo
   * usan la sesión (para propagar el formato de origen) y el renderer.
   */
  default boolean returnsInput(Params params) {
    return false;
  }

  /**
   * Aplica la operación.
   *
   * @throws io.github.erenriquezp.rora.core.exec.CancelledException si se cancela el token
   * @throws IllegalArgumentException si {@code params} no pertenece a {@link #schema()}
   */
  OpResult apply(Img src, Params params, OpContext ctx);
}
