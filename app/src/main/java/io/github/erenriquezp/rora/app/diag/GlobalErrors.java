package io.github.erenriquezp.rora.app.diag;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/**
 * Manejador de las excepciones que nadie captura, en cualquier hilo. Registra (redactado por el
 * formateador), avisa una sola vez por error repetido y, ante un {@link OutOfMemoryError}, libera
 * una reserva de memoria, cancela el trabajo y vacía las cachés para que la app siga viva.
 *
 * <p>No conoce JavaFX: la interfaz se engancha con {@link #attach}.
 */
public final class GlobalErrors implements Thread.UncaughtExceptionHandler {

  /** Lo que la interfaz hace con un error. Se llama en el {@code publisher}. */
  public interface Listener {
    void onError(Throwable error);

    /**
     * @param repeated hubo otro en los últimos {@link #OOM_REPEAT_NANOS}
     */
    void onOutOfMemory(boolean repeated);
  }

  /** Reserva liberada al primer {@code OutOfMemoryError}: deja sitio para registrar y avisar. */
  static final int RESERVE_BYTES = 8 << 20;

  static final long SAME_ERROR_NANOS = 2_000_000_000L;
  static final long OOM_REPEAT_NANOS = 30_000_000_000L;

  private static final System.Logger LOG = System.getLogger("rora.error");

  private final LongSupplier nanoTime;
  private volatile byte[] reserve;
  private volatile Listener listener;
  private volatile Executor publisher = Runnable::run;
  private volatile Runnable memoryReleaser = () -> {};
  private String lastKey;
  private long lastErrorAt;
  private long lastOomAt = Long.MIN_VALUE;
  private volatile String lastSummary;

  public GlobalErrors(LongSupplier nanoTime) {
    this.nanoTime = Objects.requireNonNull(nanoTime);
    this.reserve = allocateReserve();
  }

  /** Instala el manejador por defecto de la JVM. */
  public static GlobalErrors installDefault() {
    GlobalErrors g = new GlobalErrors(System::nanoTime);
    Thread.setDefaultUncaughtExceptionHandler(g);
    return g;
  }

  private static byte[] allocateReserve() {
    try {
      return new byte[RESERVE_BYTES];
    } catch (OutOfMemoryError e) {
      return null;
    }
  }

  /**
   * Engancha la interfaz.
   *
   * @param memoryReleaser cancela render, IA y Wallpapers y vacía cachés; se llama en el hilo del
   *     error
   */
  public void attach(Listener listener, Executor publisher, Runnable memoryReleaser) {
    this.publisher = Objects.requireNonNull(publisher);
    this.memoryReleaser = Objects.requireNonNull(memoryReleaser);
    this.listener = listener;
  }

  boolean hasReserve() {
    return reserve != null;
  }

  /** Tipo y mensaje del último error de la sesión, para el informe (el informe lo redacta). */
  public Optional<String> lastError() {
    return Optional.ofNullable(lastSummary);
  }

  static boolean isOutOfMemory(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof OutOfMemoryError) {
        return true;
      }
      if (t.getCause() == t) {
        break;
      }
    }
    return false;
  }

  @Override
  public void uncaughtException(Thread thread, Throwable error) {
    if (isOutOfMemory(error)) {
      outOfMemory(thread, error);
      return;
    }
    LOG.log(System.Logger.Level.ERROR, "Error no capturado en el hilo " + thread.getName(), error);
    String key = error.getClass().getName() + ": " + error.getMessage();
    lastSummary = key;
    boolean notify;
    synchronized (this) {
      long now = nanoTime.getAsLong();
      notify = !key.equals(lastKey) || now - lastErrorAt > SAME_ERROR_NANOS;
      lastKey = key;
      lastErrorAt = now;
    }
    Listener l = listener;
    if (notify && l != null) {
      publisher.execute(() -> l.onError(error));
    }
  }

  private void outOfMemory(Thread thread, Throwable error) {
    reserve = null; // primero: deja sitio para lo que sigue
    boolean repeated;
    synchronized (this) {
      long now = nanoTime.getAsLong();
      repeated = lastOomAt != Long.MIN_VALUE && now - lastOomAt <= OOM_REPEAT_NANOS;
      lastOomAt = now;
    }
    try {
      memoryReleaser.run();
    } catch (RuntimeException | OutOfMemoryError e) {
      // Si liberar también falla, al menos se avisa.
    }
    lastSummary = "OutOfMemoryError en " + thread.getName();
    LOG.log(System.Logger.Level.ERROR, "Memoria agotada en el hilo " + thread.getName(), error);
    Listener l = listener;
    if (l != null) {
      publisher.execute(() -> l.onOutOfMemory(repeated));
    }
    reserve = allocateReserve();
  }
}
