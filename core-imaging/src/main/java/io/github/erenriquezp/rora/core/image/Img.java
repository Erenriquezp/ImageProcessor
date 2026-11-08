package io.github.erenriquezp.rora.core.image;

import io.github.erenriquezp.rora.core.internal.ImgAccess;
import java.util.Arrays;
import java.util.Objects;

/**
 * Imagen de 8 bits por canal: píxeles ARGB <b>no premultiplicados</b> empaquetados en {@code int}
 * (mismo formato que {@code BufferedImage.getRGB}), fila a fila, sin relleno ({@code stride ==
 * width}).
 *
 * <p>No tiene métodos de escritura: una vez creada es inmutable de hecho. Las operaciones escriben
 * en un array nuevo y lo envuelven con {@link #adopt}.
 */
public final class Img {

  static {
    ImgAccess.setAccessor(img -> img.argb);
  }

  private final int width;
  private final int height;
  private final int[] argb;

  private Img(int width, int height, int[] argb) {
    this.width = width;
    this.height = height;
    this.argb = argb;
  }

  /** Imagen con todos los píxeles a {@code 0} (negro transparente). */
  public static Img create(int width, int height) {
    return new Img(width, height, new int[checkedSize(width, height)]);
  }

  /** Copia defensiva de {@code argb}; el array de origen puede reutilizarse después. */
  public static Img copyOf(int width, int height, int[] argb) {
    checkLength(width, height, argb);
    return new Img(width, height, argb.clone());
  }

  /**
   * Toma posesión de {@code argb} sin copiarlo. Quien llama <b>no debe conservar ni modificar</b>
   * el array después: evita duplicar la memoria al importar o al terminar una operación.
   */
  public static Img adopt(int width, int height, int[] argb) {
    checkLength(width, height, argb);
    return new Img(width, height, argb);
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  public int pixelCount() {
    return argb.length;
  }

  /** Píxel ARGB en {@code (x, y)}. */
  public int argb(int x, int y) {
    Objects.checkIndex(x, width);
    Objects.checkIndex(y, height);
    return argb[y * width + x];
  }

  /** Copia la fila {@code y} en {@code dst[dstOff .. dstOff + width)}. */
  public void copyRow(int y, int[] dst, int dstOff) {
    Objects.checkIndex(y, height);
    System.arraycopy(argb, y * width, dst, dstOff, width);
  }

  /** Copia de todos los píxeles. */
  public int[] toArray() {
    return argb.clone();
  }

  /**
   * Copia los píxeles en {@code dst}, que debe tener exactamente {@link #pixelCount()} elementos.
   */
  public void copyTo(int[] dst) {
    if (dst.length != argb.length) {
      throw new IllegalArgumentException(
          "dst.length=" + dst.length + ", se esperaban " + argb.length);
    }
    System.arraycopy(argb, 0, dst, 0, argb.length);
  }

  /** Mismas dimensiones y mismos píxeles. */
  public boolean sameContent(Img other) {
    return other != null
        && other.width == width
        && other.height == height
        && Arrays.equals(other.argb, argb);
  }

  @Override
  public String toString() {
    return "Img[" + width + "x" + height + "]";
  }

  private static int checkedSize(int width, int height) {
    if (width < 1 || height < 1) {
      throw new IllegalArgumentException("Dimensiones no válidas: " + width + "x" + height);
    }
    long n = (long) width * height;
    if (n > Integer.MAX_VALUE - 8) {
      throw new IllegalArgumentException("Imagen demasiado grande: " + width + "x" + height);
    }
    return (int) n;
  }

  private static void checkLength(int width, int height, int[] argb) {
    Objects.requireNonNull(argb, "argb");
    int n = checkedSize(width, height);
    if (argb.length != n) {
      throw new IllegalArgumentException(
          "argb.length=" + argb.length + ", se esperaban " + width + "x" + height + "=" + n);
    }
  }
}
