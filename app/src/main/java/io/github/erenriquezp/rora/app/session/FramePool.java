package io.github.erenriquezp.rora.app.session;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

/**
 * Reutilización de imágenes de visor (sin JavaFX; {@code T} = {@code WritableImage} en la app).
 *
 * <p>Un worker escribe en una imagen que <b>no</b> se muestra y el hilo FX la pone en pantalla. La
 * que deja de mostrarse no se reutiliza hasta que han pasado {@value #PULSES_BEFORE_REUSE} pulsos
 * de la escena ({@link #pulse()}): así ningún worker escribe en una imagen cuya textura puede estar
 * subiéndose en el frame en curso. Si no hay ninguna libre del tamaño pedido, se crea otra.
 */
public final class FramePool<T> {

  static final int PULSES_BEFORE_REUSE = 2;

  private record Retired<T>(T image, long pulse) {}

  private final BiFunction<Integer, Integer, T> factory;
  private final ToIntFunction<T> width;
  private final ToIntFunction<T> height;
  private final Deque<T> free = new ArrayDeque<>();
  private final List<Retired<T>> retired = new ArrayList<>();
  private T displayed;
  private long pulses;
  private long created;

  public FramePool(
      BiFunction<Integer, Integer, T> factory, ToIntFunction<T> width, ToIntFunction<T> height) {
    this.factory = factory;
    this.width = width;
    this.height = height;
  }

  /** (Worker) Imagen de {@code w × h} libre para escribir, nueva si no hay. */
  public synchronized T acquire(int w, int h) {
    for (var it = free.iterator(); it.hasNext(); ) {
      T t = it.next();
      if (width.applyAsInt(t) == w && height.applyAsInt(t) == h) {
        it.remove();
        return t;
      }
    }
    created++;
    return factory.apply(w, h);
  }

  /** (FX) {@code image} pasa a mostrarse; la anterior queda retirada. */
  public synchronized void displayed(T image) {
    if (displayed != null && displayed != image) {
      retired.add(new Retired<>(displayed, pulses));
    }
    displayed = image;
  }

  /** (FX) Una imagen preparada que finalmente no se muestra (obsoleta) vuelve al pool. */
  public synchronized void discard(T image) {
    if (image != null && image != displayed) {
      free.addLast(image);
      trimFree();
    }
  }

  /** (FX, tras cada pulso de la escena) Libera las retiradas hace suficientes pulsos. */
  public synchronized void pulse() {
    pulses++;
    for (var it = retired.iterator(); it.hasNext(); ) {
      Retired<T> r = it.next();
      if (pulses - r.pulse() >= PULSES_BEFORE_REUSE) {
        it.remove();
        free.addLast(r.image());
      }
    }
    trimFree();
  }

  /** Como mucho dos libres: no retener memoria de tamaños viejos. */
  private void trimFree() {
    while (free.size() > 2) {
      free.removeFirst();
    }
  }

  synchronized long createdCount() {
    return created;
  }

  synchronized int freeCount() {
    return free.size();
  }
}
