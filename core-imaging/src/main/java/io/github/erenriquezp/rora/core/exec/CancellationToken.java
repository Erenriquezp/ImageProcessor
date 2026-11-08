package io.github.erenriquezp.rora.core.exec;

/**
 * Señal de cancelación cooperativa. El {@link TileExecutor} la comprueba antes de cada banda; un
 * kernel muy largo puede comprobarla también con {@link #throwIfCancelled()}.
 */
public final class CancellationToken {

  private static final CancellationToken NONE = new CancellationToken(false);

  private final boolean cancellable;
  private volatile boolean cancelled;

  private CancellationToken(boolean cancellable) {
    this.cancellable = cancellable;
  }

  /** Token nuevo, sin cancelar. */
  public static CancellationToken create() {
    return new CancellationToken(true);
  }

  /** Token compartido que nunca se cancela. */
  public static CancellationToken none() {
    return NONE;
  }

  /**
   * Marca el token como cancelado. Es idempotente.
   *
   * @throws UnsupportedOperationException sobre {@link #none()}
   */
  public void cancel() {
    if (!cancellable) {
      throw new UnsupportedOperationException("CancellationToken.none() no se puede cancelar");
    }
    cancelled = true;
  }

  public boolean isCancelled() {
    return cancelled;
  }

  /** Lanza {@link CancelledException} si el token está cancelado. */
  public void throwIfCancelled() {
    if (cancelled) {
      throw new CancelledException();
    }
  }
}
