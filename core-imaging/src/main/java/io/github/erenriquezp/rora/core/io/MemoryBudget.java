package io.github.erenriquezp.rora.core.io;

/** Comprobación de memoria previa a decodificar. */
public final class MemoryBudget {

  private static final double USABLE = 0.7;
  private static final long MAX_PIXELS = Integer.MAX_VALUE - 8L;

  private MemoryBudget() {}

  /**
   * @param extraBytesPerPixel memoria transitoria por píxel además de los 4 bytes de {@code Img}
   * @param retained bytes que siguen ocupados durante la decodificación
   * @param maxMemory heap máximo
   * @param maxPixels tope explícito (0 = ninguno)
   * @throws ImagingException.TooLarge si la imagen no cabe
   */
  public static void require(
      int width, int height, int extraBytesPerPixel, long retained, long maxMemory, long maxPixels)
      throws ImagingException.TooLarge {
    long pixels = (long) width * height;
    long need = pixels * (4L + extraBytesPerPixel);
    long budget = (long) (maxMemory * USABLE) - retained;
    if (pixels > MAX_PIXELS || (maxPixels > 0 && pixels > maxPixels) || need > budget) {
      throw new ImagingException.TooLarge(
          width, height, "necesita " + need + " bytes; disponibles " + budget);
    }
  }
}
