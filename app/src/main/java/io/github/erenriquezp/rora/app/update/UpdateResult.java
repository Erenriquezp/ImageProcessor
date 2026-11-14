package io.github.erenriquezp.rora.app.update;

/** Resultado de una comprobación de actualizaciones. */
public sealed interface UpdateResult {

  /** Ya es la última (o más nueva que cualquier release publicada). */
  record UpToDate(VersionTag current) implements UpdateResult {}

  /**
   * Hay una más nueva.
   *
   * @param page página de la release en GitHub, ya validada
   * @param published fecha de publicación ({@code 2027-01-10}), o vacía
   * @param majorChange cambia el número mayor
   */
  record NewVersion(VersionTag version, String page, String published, boolean majorChange)
      implements UpdateResult {}

  /** No se pudo comprobar. */
  record Failed(Reason reason) implements UpdateResult {}

  /** Por qué no se pudo. */
  enum Reason {
    NO_NETWORK,
    TIMEOUT,
    RATE_LIMITED,
    INVALID_RESPONSE,
    DISABLED
  }
}
