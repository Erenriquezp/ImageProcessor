package io.github.erenriquezp.rora.core.exec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;

/**
 * Reparte el trabajo de una imagen en <b>bandas de filas completas</b> y las ejecuta en un {@link
 * ForkJoinPool} propio (nunca el común).
 *
 * <p>La partición en bandas depende solo del alto de la imagen, de {@link #bandRows()} y de la
 * alineación pedida, <b>no del número de hilos</b>; cada banda escribe filas disjuntas y las
 * reducciones se combinan en el orden de las bandas. Por eso el resultado es el mismo con 1 o con N
 * hilos.
 *
 * <p>La cancelación se comprueba antes de empezar cada banda. Si se cancela, no empieza ninguna
 * banda más, se espera a las que están en curso y se lanza {@link CancelledException}.
 */
public final class TileExecutor implements AutoCloseable {

  /** Alto de banda por defecto, en filas. */
  public static final int DEFAULT_BAND_ROWS = 64;

  /** Valor con el que se rellenan las filas fuera del halo en modo {@link #strictHalo}. */
  public static final int POISON = 0xDEADBEEF;

  private static final TileExecutor SEQUENTIAL = new TileExecutor(null, DEFAULT_BAND_ROWS, false);
  private static final AtomicInteger POOL_IDS = new AtomicInteger();

  private final ForkJoinPool pool;
  private final int bandRows;
  private final boolean strictHalo;
  private volatile boolean closed;

  private TileExecutor(ForkJoinPool pool, int bandRows, boolean strictHalo) {
    if (bandRows < 1) {
      throw new IllegalArgumentException("bandRows=" + bandRows);
    }
    this.pool = pool;
    this.bandRows = bandRows;
    this.strictHalo = strictHalo;
  }

  /** {@code max(1, núcleos - 1)}: deja un núcleo libre para la interfaz. */
  public static int defaultParallelism() {
    return Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
  }

  /** Ejecutor que trabaja en el hilo llamante. Compartido; {@link #close()} no le afecta. */
  public static TileExecutor sequential() {
    return SEQUENTIAL;
  }

  /** Hilo llamante con un alto de banda distinto (para tests de equivalencia). */
  public static TileExecutor sequential(int bandRows) {
    return new TileExecutor(null, bandRows, false);
  }

  /** Pool propio de {@code threads} hilos daemon y bandas de {@link #DEFAULT_BAND_ROWS} filas. */
  public static TileExecutor create(int threads) {
    return create(threads, DEFAULT_BAND_ROWS);
  }

  /** Pool propio de {@code threads} hilos daemon y bandas de {@code bandRows} filas. */
  public static TileExecutor create(int threads, int bandRows) {
    if (threads < 1) {
      throw new IllegalArgumentException("threads=" + threads);
    }
    int id = POOL_IDS.incrementAndGet();
    ForkJoinPool.ForkJoinWorkerThreadFactory factory =
        p -> {
          ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(p);
          t.setDaemon(true);
          t.setName("rora-imaging-" + id + "-" + t.getPoolIndex());
          return t;
        };
    return new TileExecutor(new ForkJoinPool(threads, factory, null, false), bandRows, false);
  }

  /**
   * Solo para verificación: secuencial y, para cada banda, pasa al kernel una copia de la entrada
   * en la que las filas fuera de {@code [y0 - halo, y1 + halo)} valen {@link #POISON}. Si una
   * operación lee fuera del halo que declara, su resultado cambia y el test lo detecta.
   */
  public static TileExecutor strictHalo(int bandRows) {
    return new TileExecutor(null, bandRows, true);
  }

  /** Número de hilos de trabajo (1 si es secuencial). */
  public int parallelism() {
    return pool == null ? 1 : pool.getParallelism();
  }

  public int bandRows() {
    return bandRows;
  }

  /**
   * Bandas de {@code height} filas: alto {@code bandRows} redondeado al múltiplo de {@code align}.
   */
  public static List<Band> bands(int height, int bandRows, int rowAlign) {
    if (height < 1 || bandRows < 1 || rowAlign < 1) {
      throw new IllegalArgumentException(
          "height=" + height + ", bandRows=" + bandRows + ", rowAlign=" + rowAlign);
    }
    long step = ((bandRows + (long) rowAlign - 1) / rowAlign) * rowAlign;
    List<Band> out = new ArrayList<>();
    for (long y = 0; y < height; y += step) {
      out.add(new Band((int) y, (int) Math.min(height, y + step)));
    }
    return out;
  }

  /**
   * Ejecuta {@code kernel} sobre todas las bandas.
   *
   * @param haloRows filas extra que el kernel puede leer por arriba y por abajo ({@code
   *     Integer.MAX_VALUE} = toda la imagen)
   * @param rowAlign alineación de las bandas
   */
  public void forEachBand(
      int[] src,
      int[] dst,
      int width,
      int height,
      int haloRows,
      int rowAlign,
      CancellationToken token,
      Progress progress,
      BandKernel kernel) {
    Objects.requireNonNull(kernel, "kernel");
    if (width < 1 || (long) width * height != src.length || dst.length != src.length) {
      throw new IllegalArgumentException(
          "Tamaños incoherentes: "
              + width
              + "x"
              + height
              + ", src="
              + src.length
              + ", dst="
              + dst.length);
    }
    if (haloRows < 0) {
      throw new IllegalArgumentException("haloRows=" + haloRows);
    }
    List<Band> bands = bands(height, bandRows, rowAlign);
    run(
        bands,
        token,
        progress,
        (i, b) -> {
          int[] in = strictHalo ? poisoned(src, width, height, b, haloRows) : src;
          kernel.run(in, dst, width, height, b.y0(), b.y1());
        });
  }

  /**
   * Reducción por bandas: cada banda acumula en su propio {@code init.get()} y los parciales se
   * combinan con {@code merge} <b>en el orden de las bandas</b>.
   */
  public <A> A reduceBands(
      int height,
      CancellationToken token,
      Progress progress,
      Supplier<A> init,
      BandAccumulator<A> accumulator,
      BinaryOperator<A> merge) {
    Objects.requireNonNull(init, "init");
    Objects.requireNonNull(accumulator, "accumulator");
    Objects.requireNonNull(merge, "merge");
    List<Band> bands = bands(height, bandRows, 1);
    Object[] partial = new Object[bands.size()];
    run(
        bands,
        token,
        progress,
        (i, b) -> {
          A acc = init.get();
          accumulator.accept(acc, b.y0(), b.y1());
          partial[i] = acc;
        });
    @SuppressWarnings("unchecked")
    A result = (A) partial[0];
    for (int i = 1; i < partial.length; i++) {
      @SuppressWarnings("unchecked")
      A next = (A) partial[i];
      result = merge.apply(result, next);
    }
    return result;
  }

  @FunctionalInterface
  private interface IndexedBandTask {
    void run(int index, Band band);
  }

  private void run(
      List<Band> bands, CancellationToken token, Progress progress, IndexedBandTask task) {
    Objects.requireNonNull(token, "token");
    Objects.requireNonNull(progress, "progress");
    if (closed) {
      throw new IllegalStateException("TileExecutor cerrado");
    }
    token.throwIfCancelled();
    ProgressCounter counter = new ProgressCounter(progress, bands.size());
    if (pool == null || bands.size() == 1) {
      for (int i = 0; i < bands.size(); i++) {
        token.throwIfCancelled();
        task.run(i, bands.get(i));
        counter.bandDone();
      }
      return;
    }
    List<ForkJoinTask<?>> tasks = new ArrayList<>(bands.size());
    for (int i = 0; i < bands.size(); i++) {
      int index = i;
      Band band = bands.get(i);
      tasks.add(
          ForkJoinTask.adapt(
              () -> {
                if (token.isCancelled()) {
                  return;
                }
                task.run(index, band);
                counter.bandDone();
              }));
    }
    pool.invoke(ForkJoinTask.adapt(() -> ForkJoinTask.invokeAll(tasks)));
    token.throwIfCancelled();
  }

  /** Copia de {@code src} con las filas fuera de la ventana de lectura envenenadas. */
  private static int[] poisoned(int[] src, int width, int height, Band b, int haloRows) {
    long from = Math.max(0L, (long) b.y0() - haloRows);
    long to = Math.min((long) height, (long) b.y1() + haloRows);
    int[] copy = src.clone();
    Arrays.fill(copy, 0, (int) from * width, POISON);
    Arrays.fill(copy, (int) to * width, copy.length, POISON);
    return copy;
  }

  /** Cierra el pool y espera a que terminen sus hilos (como mucho 30 s). Idempotente. */
  @Override
  public void close() {
    if (pool == null || closed) {
      return;
    }
    closed = true;
    pool.shutdown();
    try {
      pool.awaitTermination(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public boolean isClosed() {
    return closed;
  }

  /** Notifica el progreso de forma serializada y monótona. */
  private static final class ProgressCounter {
    private final Progress progress;
    private final long total;
    private long done;

    ProgressCounter(Progress progress, long total) {
      this.progress = progress;
      this.total = total;
    }

    synchronized void bandDone() {
      done++;
      progress.update(done, total);
    }
  }
}
