package io.github.erenriquezp.rora.core.op;

import io.github.erenriquezp.rora.core.image.Img;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Caché LRU de resultados intermedios acotada en bytes. Segura para hilos.
 *
 * <p>El peso es el tamaño real de los píxeles ({@code 4·w·h}) y se cuenta <b>una vez por
 * instancia</b> de {@link Img}: si varias claves apuntan a la misma imagen (un paso que devuelve su
 * entrada, la cabeza que coincide con el final…), no se duplica. Tras cada {@link #put} se expulsan
 * las entradas menos usadas hasta volver al presupuesto; la recién insertada no se expulsa en ese
 * mismo {@code put}. Una imagen mayor que el presupuesto no se guarda.
 */
public final class ResultCache {

  /** Estadísticas acumuladas desde la creación. */
  public record Stats(long hits, long misses, long puts, long evictions, long bytes, int entries) {}

  private final long budgetBytes;
  private final ReentrantLock lock = new ReentrantLock();
  private final LinkedHashMap<RenderKey, Img> map = new LinkedHashMap<>(64, 0.75f, true);
  private final IdentityHashMap<Img, int[]> refs = new IdentityHashMap<>();
  private long weight;
  private long hits;
  private long misses;
  private long puts;
  private long evictions;

  public ResultCache(long budgetBytes) {
    if (budgetBytes < 0) {
      throw new IllegalArgumentException("budgetBytes=" + budgetBytes);
    }
    this.budgetBytes = budgetBytes;
  }

  /** {@code maxMemory / 4}, acotado a [64 MB, 1 GB]. */
  public static long defaultBudget() {
    long quarter = Runtime.getRuntime().maxMemory() / 4;
    return Math.max(64L << 20, Math.min(1L << 30, quarter));
  }

  /** Bytes de píxeles de {@code img}. */
  public static long bytesOf(Img img) {
    return 4L * img.pixelCount();
  }

  public long budgetBytes() {
    return budgetBytes;
  }

  public Optional<Img> get(RenderKey key) {
    Objects.requireNonNull(key, "key");
    lock.lock();
    try {
      Img img = map.get(key);
      if (img == null) {
        misses++;
      } else {
        hits++;
      }
      return Optional.ofNullable(img);
    } finally {
      lock.unlock();
    }
  }

  /** ¿Está la clave? No cuenta como acceso ni cambia el orden LRU. */
  public boolean contains(RenderKey key) {
    lock.lock();
    try {
      return map.containsKey(key);
    } finally {
      lock.unlock();
    }
  }

  public void put(RenderKey key, Img img) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(img, "img");
    lock.lock();
    try {
      puts++;
      Img old = map.get(key);
      if (old == img) {
        return;
      }
      if (old != null) {
        map.remove(key);
        release(old);
      }
      if (bytesOf(img) > budgetBytes && !refs.containsKey(img)) {
        return;
      }
      map.put(key, img);
      int[] count = refs.get(img);
      if (count == null) {
        refs.put(img, new int[] {1});
        weight += bytesOf(img);
      } else {
        count[0]++;
      }
      Iterator<Map.Entry<RenderKey, Img>> it = map.entrySet().iterator();
      while (weight > budgetBytes && it.hasNext()) {
        Map.Entry<RenderKey, Img> e = it.next();
        if (e.getKey().equals(key)) {
          continue;
        }
        it.remove();
        evictions++;
        release(e.getValue());
      }
    } finally {
      lock.unlock();
    }
  }

  private void release(Img img) {
    int[] count = refs.get(img);
    if (--count[0] == 0) {
      refs.remove(img);
      weight -= bytesOf(img);
    }
  }

  /** Quita las entradas de una fuente (al cargar otra imagen). */
  public void invalidateSource(long sourceId) {
    lock.lock();
    try {
      Iterator<Map.Entry<RenderKey, Img>> it = map.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<RenderKey, Img> e = it.next();
        if (e.getKey().sourceId() == sourceId) {
          it.remove();
          release(e.getValue());
        }
      }
    } finally {
      lock.unlock();
    }
  }

  /** Vacía la caché (p. ej. tras un {@link OutOfMemoryError}). */
  public void clear() {
    lock.lock();
    try {
      map.clear();
      refs.clear();
      weight = 0;
    } finally {
      lock.unlock();
    }
  }

  /** Bytes retenidos (instancias distintas). */
  public long weightBytes() {
    lock.lock();
    try {
      return weight;
    } finally {
      lock.unlock();
    }
  }

  public Stats stats() {
    lock.lock();
    try {
      return new Stats(hits, misses, puts, evictions, weight, map.size());
    } finally {
      lock.unlock();
    }
  }

  /** Comprobación de invariantes para los tests: peso = suma de instancias distintas. */
  boolean checkInvariants() {
    lock.lock();
    try {
      IdentityHashMap<Img, int[]> counted = new IdentityHashMap<>();
      long w = 0;
      for (Img img : map.values()) {
        int[] c = counted.get(img);
        if (c == null) {
          counted.put(img, new int[] {1});
          w += bytesOf(img);
        } else {
          c[0]++;
        }
      }
      if (w != weight || counted.size() != refs.size()) {
        return false;
      }
      for (Map.Entry<Img, int[]> e : counted.entrySet()) {
        int[] r = refs.get(e.getKey());
        if (r == null || r[0] != e.getValue()[0]) {
          return false;
        }
      }
      return true;
    } finally {
      lock.unlock();
    }
  }
}
