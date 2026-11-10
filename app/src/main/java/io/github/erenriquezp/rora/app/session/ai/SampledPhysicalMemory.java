package io.github.erenriquezp.rora.app.session.ai;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Memoria física libre, leída en segundo plano. En Windows la consulta al sistema puede tardar más
 * de 100 ms (medido con el monitor FX), así que quien pregunta recibe el último valor conocido.
 */
final class SampledPhysicalMemory {

  static final long MAX_AGE_NANOS = TimeUnit.SECONDS.toNanos(2);

  private final LongSupplier reader;
  private final Executor background;
  private final LongSupplier clock;
  private final AtomicBoolean sampling = new AtomicBoolean();

  // Hasta la primera lectura se asume que hay memoria: el trabajo vuelve a comprobarlo.
  private volatile long free = Long.MAX_VALUE;
  private volatile boolean sampled;
  private volatile long sampledAt;

  SampledPhysicalMemory(LongSupplier reader, Executor background, LongSupplier clock) {
    this.reader = reader;
    this.background = background;
    this.clock = clock;
  }

  /** Último valor conocido; si está viejo, pide otro sin esperarlo. */
  long freeBytes() {
    if ((!sampled || clock.getAsLong() - sampledAt > MAX_AGE_NANOS)
        && sampling.compareAndSet(false, true)) {
      background.execute(this::sample);
    }
    return free;
  }

  private void sample() {
    try {
      free = reader.getAsLong();
      sampledAt = clock.getAsLong();
      sampled = true;
    } finally {
      sampling.set(false);
    }
  }
}
