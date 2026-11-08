package io.github.erenriquezp.rora.core.exec;

/**
 * La operación se canceló con su {@link CancellationToken}; el resultado parcial se descarta. Es no
 * comprobada para no obligar a declararla en cada kernel.
 */
public final class CancelledException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public CancelledException() {
    super("operation cancelled");
  }
}
