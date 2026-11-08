package io.github.erenriquezp.rora.core.op;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.image.Img;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** {@link ResultCache} y {@link RenderKey}. */
class ResultCacheTest {

  private static final OpRegistry REGISTRY = OpRegistry.load();

  private static OpSpec bright(int v) {
    Operation op = REGISTRY.require("color.brightness");
    return OpSpec.of(op, Params.builder(op.schema()).set("value", v).build());
  }

  private static RenderKey key(long src, int... steps) {
    RenderKey k = RenderKey.root(src, RenderLevel.FULL);
    for (int s : steps) {
      k = k.then(bright(s));
    }
    return k;
  }

  /** Imagen de {@code kb} KiB exactos (256·kb píxeles). */
  private static Img img(int kb) {
    return Img.create(256 * kb, 1);
  }

  // ── RenderKey ─────────────────────────────────────────────────────────────

  @Test
  void keysAreStructural() {
    assertThat(key(1, 10, 20)).isEqualTo(key(1, 10, 20)).hasSameHashCodeAs(key(1, 10, 20));
    assertThat(key(1, 10, 20)).isNotEqualTo(key(1, 20, 10));
    assertThat(key(1, 10)).isNotEqualTo(key(2, 10));
    assertThat(key(1, 10)).isNotEqualTo(key(1, 10, 20));
    assertThat(RenderKey.root(1, RenderLevel.FULL))
        .isNotEqualTo(RenderKey.root(1, new RenderLevel.Preview(10, 10)));
    assertThat(RenderKey.root(1, new RenderLevel.Preview(10, 10)))
        .isEqualTo(RenderKey.root(1, new RenderLevel.Preview(10, 10)));
    assertThat(key(1, 10, 20).parent()).isEqualTo(key(1, 10));
    assertThat(key(1, 10, 20).steps()).containsExactly(bright(10), bright(20));
    assertThat(key(1, 10, 20).depth()).isEqualTo(2);
    assertThat(key(1, 10, 20).step()).isEqualTo(bright(20));
    assertThat(key(1).toString()).contains("src=1");
    assertThat(key(1, 5)).isNotEqualTo("x");
  }

  // ── LRU y peso ────────────────────────────────────────────────────────────

  @Test
  void lruEvictionByBytes() {
    ResultCache c = new ResultCache(3 * 1024);
    c.put(key(1, 1), img(1));
    c.put(key(1, 2), img(1));
    c.put(key(1, 3), img(1));
    assertThat(c.weightBytes()).isEqualTo(3 * 1024);
    c.get(key(1, 1)); // 1 pasa a ser el más reciente
    c.put(key(1, 4), img(1));
    assertThat(c.contains(key(1, 2))).isFalse();
    assertThat(c.contains(key(1, 1))).isTrue();
    assertThat(c.contains(key(1, 3))).isTrue();
    assertThat(c.contains(key(1, 4))).isTrue();
    assertThat(c.stats().evictions()).isEqualTo(1);
    assertThat(c.checkInvariants()).isTrue();
  }

  @Test
  void bigEntryEvictsSeveralButNeverItself() {
    ResultCache c = new ResultCache(4 * 1024);
    c.put(key(1, 1), img(1));
    c.put(key(1, 2), img(1));
    c.put(key(1, 3), img(4));
    assertThat(c.stats().entries()).isEqualTo(1);
    assertThat(c.contains(key(1, 3))).isTrue();
    assertThat(c.weightBytes()).isEqualTo(4 * 1024);
  }

  @Test
  void entryLargerThanBudgetIsNotStored() {
    ResultCache c = new ResultCache(1024);
    c.put(key(1, 1), img(2));
    assertThat(c.contains(key(1, 1))).isFalse();
    assertThat(c.weightBytes()).isZero();
  }

  @Test
  void sameInstanceUnderSeveralKeysIsCountedOnce() {
    ResultCache c = new ResultCache(10 * 1024);
    Img shared = img(2);
    c.put(key(1, 1), shared);
    c.put(key(1, 1, 2), shared);
    c.put(key(1, 1, 2, 3), shared);
    assertThat(c.weightBytes()).isEqualTo(2 * 1024);
    assertThat(c.checkInvariants()).isTrue();
    // Expulsar una de las claves no libera la instancia mientras otra la use.
    ResultCache small = new ResultCache(3 * 1024);
    small.put(key(1, 1), shared);
    small.put(key(1, 2), shared);
    small.put(key(1, 3), img(1));
    assertThat(small.weightBytes()).isEqualTo(3 * 1024);
    small.put(key(1, 4), img(1));
    assertThat(small.checkInvariants()).isTrue();
    assertThat(small.weightBytes()).isLessThanOrEqualTo(3 * 1024);
  }

  @Test
  void replacingAKeyReleasesTheOldImage() {
    ResultCache c = new ResultCache(10 * 1024);
    Img a = img(1);
    c.put(key(1, 1), a);
    c.put(key(1, 1), a);
    assertThat(c.weightBytes()).isEqualTo(1024);
    c.put(key(1, 1), img(3));
    assertThat(c.weightBytes()).isEqualTo(3 * 1024);
    assertThat(c.checkInvariants()).isTrue();
  }

  @Test
  void hitsMissesClearAndInvalidate() {
    ResultCache c = new ResultCache(10 * 1024);
    c.put(key(1, 1), img(1));
    c.put(key(2, 1), img(1));
    assertThat(c.get(key(1, 1))).isPresent();
    assertThat(c.get(key(1, 9))).isEmpty();
    assertThat(c.stats().hits()).isEqualTo(1);
    assertThat(c.stats().misses()).isEqualTo(1);
    c.invalidateSource(1);
    assertThat(c.contains(key(1, 1))).isFalse();
    assertThat(c.contains(key(2, 1))).isTrue();
    assertThat(c.weightBytes()).isEqualTo(1024);
    c.clear();
    assertThat(c.weightBytes()).isZero();
    assertThat(c.stats().entries()).isZero();
    assertThat(c.checkInvariants()).isTrue();
  }

  @Test
  void defaultBudgetIsBounded() {
    assertThat(ResultCache.defaultBudget()).isBetween(64L << 20, 1L << 30);
    assertThat(new ResultCache(123).budgetBytes()).isEqualTo(123);
  }

  // ── Concurrencia ──────────────────────────────────────────────────────────

  @Test
  void concurrentRandomOperationsKeepInvariants() throws Exception {
    ResultCache c = new ResultCache(20 * 1024);
    List<Img> pool = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      pool.add(img(1 + i % 4));
    }
    int threads = 8;
    CyclicBarrier start = new CyclicBarrier(threads);
    ExecutorService ex = Executors.newFixedThreadPool(threads);
    try {
      List<Future<?>> fs = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        long seed = 0x5EED_0040L + t;
        fs.add(
            ex.submit(
                () -> {
                  SplittableRandom rnd = new SplittableRandom(seed);
                  start.await();
                  for (int i = 0; i < 10_000; i++) {
                    RenderKey k = key(rnd.nextInt(3), rnd.nextInt(8), rnd.nextInt(4));
                    switch (rnd.nextInt(10)) {
                      case 0 -> c.invalidateSource(rnd.nextInt(3));
                      case 1 -> {
                        if (rnd.nextInt(100) == 0) {
                          c.clear();
                        }
                      }
                      case 2, 3, 4, 5 -> c.put(k, pool.get(rnd.nextInt(pool.size())));
                      default -> c.get(k);
                    }
                    if (c.weightBytes() > c.budgetBytes()) {
                      throw new AssertionError("Peso sobre el presupuesto: " + c.weightBytes());
                    }
                  }
                  return null;
                }));
      }
      for (Future<?> f : fs) {
        f.get();
      }
    } finally {
      ex.shutdownNow();
    }
    assertThat(c.checkInvariants()).isTrue();
    assertThat(c.weightBytes()).isLessThanOrEqualTo(c.budgetBytes());
  }
}
