package io.github.erenriquezp.rora.core.exec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TileExecutorTest {

  /** Kernel puntual determinista que depende de la posición. */
  private static final BandKernel MIX =
      (src, dst, w, h, y0, y1) -> {
        for (int y = y0; y < y1; y++) {
          for (int x = 0; x < w; x++) {
            int i = y * w + x;
            dst[i] = src[i] * 31 + x * 7 + y * 13;
          }
        }
      };

  /** Lee la fila anterior (radio 1 hacia arriba). */
  private static final BandKernel ROW_ABOVE =
      (src, dst, w, h, y0, y1) -> {
        for (int y = y0; y < y1; y++) {
          for (int x = 0; x < w; x++) {
            dst[y * w + x] = y == 0 ? src[x] : src[(y - 1) * w + x];
          }
        }
      };

  private static int[] noise(int n, long seed) {
    SplittableRandom r = new SplittableRandom(seed);
    int[] a = new int[n];
    for (int i = 0; i < n; i++) {
      a[i] = r.nextInt();
    }
    return a;
  }

  private static int[] run(TileExecutor ex, BandKernel k, int[] src, int w, int h, int halo) {
    int[] dst = new int[src.length];
    ex.forEachBand(src, dst, w, h, halo, 1, CancellationToken.none(), Progress.NONE, k);
    return dst;
  }

  @Test
  void bandsCoverTheImageAndRespectAlignment() {
    assertThat(TileExecutor.bands(1, 64, 1)).containsExactly(new Band(0, 1));
    assertThat(TileExecutor.bands(130, 64, 1))
        .containsExactly(new Band(0, 64), new Band(64, 128), new Band(128, 130));
    // 64 filas alineadas a bloques de 10 → bandas de 70
    assertThat(TileExecutor.bands(150, 64, 10))
        .containsExactly(new Band(0, 70), new Band(70, 140), new Band(140, 150));
    assertThat(TileExecutor.bands(5, 1, 1)).hasSize(5);
    assertThat(new Band(3, 7).rows()).isEqualTo(4);
    assertThatThrownBy(() -> TileExecutor.bands(0, 64, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Band(2, 2)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resultIsIdenticalForAnyThreadCountAndBandHeight() {
    int w = 37;
    int h = 301;
    int[] src = noise(w * h, 42);
    int[] expected = run(TileExecutor.sequential(), MIX, src, w, h, 0);
    try (TileExecutor four = TileExecutor.create(4);
        TileExecutor tiny = TileExecutor.create(3, 1)) {
      assertThat(run(four, MIX, src, w, h, 0)).isEqualTo(expected);
      assertThat(run(tiny, MIX, src, w, h, 0)).isEqualTo(expected);
      assertThat(run(TileExecutor.sequential(1), MIX, src, w, h, 0)).isEqualTo(expected);
      assertThat(four.parallelism()).isEqualTo(4);
      assertThat(tiny.bandRows()).isEqualTo(1);
    }
  }

  @Test
  void workRunsOnNamedDaemonThreadsOfItsOwnPool() {
    Set<String> names = ConcurrentHashMap.newKeySet();
    Set<Boolean> daemon = ConcurrentHashMap.newKeySet();
    try (TileExecutor ex = TileExecutor.create(2, 1)) {
      int[] src = new int[4 * 64];
      ex.forEachBand(
          src,
          new int[src.length],
          4,
          64,
          0,
          1,
          CancellationToken.none(),
          Progress.NONE,
          (s, d, w, h, y0, y1) -> {
            names.add(Thread.currentThread().getName());
            daemon.add(Thread.currentThread().isDaemon());
          });
    }
    assertThat(names).allMatch(n -> n.startsWith("rora-imaging-"));
    assertThat(daemon).containsExactly(true);
  }

  @Test
  void progressIsMonotonicAndReachesTotal() {
    List<long[]> calls = Collections.synchronizedList(new ArrayList<>());
    try (TileExecutor ex = TileExecutor.create(3, 2)) {
      int[] src = new int[3 * 20];
      ex.forEachBand(
          src,
          new int[src.length],
          3,
          20,
          0,
          1,
          CancellationToken.none(),
          (d, t) -> calls.add(new long[] {d, t}),
          MIX);
    }
    assertThat(calls).hasSize(10);
    for (int i = 0; i < calls.size(); i++) {
      assertThat(calls.get(i)).containsExactly(i + 1, 10);
    }
  }

  @Test
  void preCancelledTokenNeverRunsTheKernel() {
    CancellationToken token = CancellationToken.create();
    token.cancel();
    AtomicInteger calls = new AtomicInteger();
    BandKernel k = (s, d, w, h, y0, y1) -> calls.incrementAndGet();
    for (TileExecutor ex : List.of(TileExecutor.sequential(), TileExecutor.create(2, 1))) {
      try (ex) {
        assertThatThrownBy(
                () -> ex.forEachBand(new int[8], new int[8], 2, 4, 0, 1, token, Progress.NONE, k))
            .isInstanceOf(CancelledException.class);
      }
    }
    assertThat(calls).hasValue(0);
  }

  @Test
  void cancellingDuringABandStopsLaterBandsSequentially() {
    CancellationToken token = CancellationToken.create();
    List<Integer> started = new ArrayList<>();
    BandKernel k =
        (s, d, w, h, y0, y1) -> {
          started.add(y0);
          if (y0 == 2) {
            token.cancel();
          }
        };
    TileExecutor ex = TileExecutor.sequential(1);
    assertThatThrownBy(
            () -> ex.forEachBand(new int[10], new int[10], 1, 10, 0, 1, token, Progress.NONE, k))
        .isInstanceOf(CancelledException.class)
        .hasMessage("operation cancelled");
    assertThat(started).containsExactly(0, 1, 2);
  }

  @Test
  void cancellingDuringParallelWorkThrows() {
    CancellationToken token = CancellationToken.create();
    AtomicInteger calls = new AtomicInteger();
    BandKernel k =
        (s, d, w, h, y0, y1) -> {
          if (calls.incrementAndGet() == 3) {
            token.cancel();
          }
        };
    try (TileExecutor ex = TileExecutor.create(2, 1)) {
      assertThatThrownBy(
              () ->
                  ex.forEachBand(
                      new int[1000], new int[1000], 1, 1000, 0, 1, token, Progress.NONE, k))
          .isInstanceOf(CancelledException.class);
    }
    assertThat(calls.get()).isLessThan(1000);
  }

  @Test
  void noneTokenCannotBeCancelled() {
    assertThatThrownBy(() -> CancellationToken.none().cancel())
        .isInstanceOf(UnsupportedOperationException.class);
    CancellationToken t = CancellationToken.create();
    assertThat(t.isCancelled()).isFalse();
    t.throwIfCancelled();
    t.cancel();
    t.cancel();
    assertThat(t.isCancelled()).isTrue();
  }

  @Test
  void kernelExceptionsPropagate() {
    BandKernel boom =
        (s, d, w, h, y0, y1) -> {
          throw new IllegalStateException("boom");
        };
    try (TileExecutor ex = TileExecutor.create(2, 1)) {
      assertThatThrownBy(() -> run(ex, boom, new int[8], 2, 4, 0))
          .isInstanceOf(IllegalStateException.class);
    }
    assertThatThrownBy(() -> run(TileExecutor.sequential(), boom, new int[8], 2, 4, 0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
  }

  @Test
  void strictHaloDetectsUndeclaredReads() {
    int w = 5;
    int h = 9;
    int[] src = noise(w * h, 7);
    int[] expected = run(TileExecutor.sequential(), ROW_ABOVE, src, w, h, 1);
    assertThat(run(TileExecutor.strictHalo(2), ROW_ABOVE, src, w, h, 1)).isEqualTo(expected);
    assertThat(run(TileExecutor.strictHalo(2), ROW_ABOVE, src, w, h, 0)).isNotEqualTo(expected);
    assertThat(run(TileExecutor.strictHalo(2), ROW_ABOVE, src, w, h, Integer.MAX_VALUE))
        .isEqualTo(expected);
  }

  @Test
  void reduceMergesPartialsInBandOrder() {
    TileExecutor ex = TileExecutor.create(4, 1);
    try (ex) {
      List<Integer> order =
          ex.reduceBands(
              50,
              CancellationToken.none(),
              Progress.NONE,
              ArrayList::new,
              (acc, y0, y1) -> acc.add(y0),
              (a, b) -> {
                a.addAll(b);
                return a;
              });
      assertThat(order).hasSize(50).isSorted();
    }
    long sum =
        TileExecutor.sequential()
            .reduceBands(
                200,
                CancellationToken.none(),
                Progress.NONE,
                () -> new long[1],
                (acc, y0, y1) -> acc[0] += y1 - y0,
                (a, b) -> new long[] {a[0] + b[0]})[0];
    assertThat(sum).isEqualTo(200);
  }

  @Test
  void closedExecutorRejectsWork() {
    TileExecutor ex = TileExecutor.create(2);
    ex.close();
    ex.close();
    assertThat(ex.isClosed()).isTrue();
    assertThatThrownBy(() -> run(ex, MIX, new int[4], 2, 2, 0))
        .isInstanceOf(IllegalStateException.class);
    TileExecutor.sequential().close();
    assertThat(TileExecutor.sequential().isClosed()).isFalse();
    assertThat(TileExecutor.sequential().parallelism()).isEqualTo(1);
  }

  @Test
  void argumentsAreValidated() {
    TileExecutor ex = TileExecutor.sequential();
    assertThatThrownBy(() -> TileExecutor.create(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TileExecutor.sequential(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> run(ex, MIX, new int[5], 2, 2, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> run(ex, MIX, new int[4], 2, 2, -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                ex.forEachBand(
                    new int[4],
                    new int[3],
                    2,
                    2,
                    0,
                    1,
                    CancellationToken.none(),
                    Progress.NONE,
                    MIX))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(TileExecutor.defaultParallelism()).isPositive();
  }
}
