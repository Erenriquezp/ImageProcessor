package io.github.erenriquezp.rora.app.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FramePoolTest {

  /** Imagen falsa con tamaño. */
  record Fake(int w, int h, int serial) {}

  private int serial;
  private final FramePool<Fake> pool =
      new FramePool<>((w, h) -> new Fake(w, h, serial++), Fake::w, Fake::h);

  @Test
  void displayedAndRecentlyRetiredImagesAreNeverHandedOut() {
    Fake a = pool.acquire(10, 10);
    pool.displayed(a);
    Fake b = pool.acquire(10, 10);
    assertThat(b).isNotSameAs(a);
    pool.displayed(b); // a retirada
    pool.pulse(); // 1 pulso: aún no
    Fake c = pool.acquire(10, 10);
    assertThat(c).isNotSameAs(a).isNotSameAs(b);
    pool.discard(c);
    pool.pulse(); // 2 pulsos: a libre
    Fake d = pool.acquire(10, 10);
    Fake e = pool.acquire(10, 10);
    assertThat(java.util.List.of(d, e)).containsExactlyInAnyOrder(a, c);
    assertThat(pool.createdCount()).isEqualTo(3);
  }

  @Test
  void sizeMismatchCreatesANewImageAndFreeListIsBounded() {
    Fake a = pool.acquire(10, 10);
    pool.discard(a);
    Fake b = pool.acquire(20, 10);
    assertThat(b.w()).isEqualTo(20);
    for (int i = 0; i < 5; i++) {
      pool.discard(new Fake(1, 1, -i));
    }
    assertThat(pool.freeCount()).isEqualTo(2);
    pool.discard(null);
    pool.displayed(b);
    pool.discard(b);
    assertThat(pool.freeCount()).isEqualTo(2);
  }
}
