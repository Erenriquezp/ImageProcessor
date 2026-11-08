package io.github.erenriquezp.rora.app.session;

/**
 * Error de un trabajo en segundo plano, listo para mostrarse: nunca se traga.
 *
 * @param kind categoría (decide el mensaje de la barra de estado)
 * @param cause excepción original
 */
public record RenderFailure(Kind kind, Throwable cause) {

  public enum Kind {
    /** Memoria insuficiente: se ha vaciado la caché. */
    OUT_OF_MEMORY,
    /** Parámetros no válidos para la operación. */
    INVALID_PARAMS,
    /** Cualquier otro fallo de una operación. */
    OPERATION_FAILED
  }

  public static RenderFailure of(Throwable t) {
    Kind k =
        t instanceof OutOfMemoryError
            ? Kind.OUT_OF_MEMORY
            : t instanceof IllegalArgumentException ? Kind.INVALID_PARAMS : Kind.OPERATION_FAILED;
    return new RenderFailure(k, t);
  }

  /** Texto legible para la barra de estado. */
  public String message() {
    return switch (kind) {
      case OUT_OF_MEMORY -> "Memoria insuficiente para esta operación; se ha vaciado la caché";
      case INVALID_PARAMS -> "Parámetros no válidos: " + cause.getMessage();
      case OPERATION_FAILED ->
          cause.getMessage() == null
              ? cause.getClass().getSimpleName()
              : cause.getClass().getSimpleName() + ": " + cause.getMessage();
    };
  }
}
