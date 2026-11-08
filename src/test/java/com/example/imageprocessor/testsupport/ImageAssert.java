package com.example.imageprocessor.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;

/** Aserciones sobre imágenes, comparando por {@code getRGB} (independiente del tipo interno). */
public final class ImageAssert {

  private ImageAssert() {}

  public static void assertSameSize(BufferedImage actual, BufferedImage expected) {
    assertThat(actual.getWidth()).as("ancho").isEqualTo(expected.getWidth());
    assertThat(actual.getHeight()).as("alto").isEqualTo(expected.getHeight());
  }

  public static void assertEqualExactly(BufferedImage actual, BufferedImage expected) {
    assertEqualWithin(actual, expected, 0);
  }

  /** Delta máximo por canal (A, R, G, B) menor o igual que la tolerancia. */
  public static void assertEqualWithin(
      BufferedImage actual, BufferedImage expected, int tolerance) {
    assertSameSize(actual, expected);
    int worst = 0;
    String where = "";
    for (int y = 0; y < expected.getHeight(); y++) {
      for (int x = 0; x < expected.getWidth(); x++) {
        int d = maxChannelDelta(actual.getRGB(x, y), expected.getRGB(x, y));
        if (d > worst) {
          worst = d;
          where =
              String.format(
                  "(%d,%d) actual=%08X esperado=%08X",
                  x, y, actual.getRGB(x, y), expected.getRGB(x, y));
        }
      }
    }
    assertThat(worst)
        .as("delta máximo por canal, peor píxel %s", where)
        .isLessThanOrEqualTo(tolerance);
  }

  public static int maxChannelDelta(int p, int q) {
    int d = 0;
    for (int shift = 0; shift <= 24; shift += 8) {
      d = Math.max(d, Math.abs(((p >> shift) & 0xFF) - ((q >> shift) & 0xFF)));
    }
    return d;
  }

  /** El canal alpha de cada píxel es igual al de la imagen de origen. */
  public static void assertAlphaPreserved(BufferedImage actual, BufferedImage source) {
    assertSameSize(actual, source);
    for (int y = 0; y < source.getHeight(); y++) {
      for (int x = 0; x < source.getWidth(); x++) {
        assertThat(actual.getRGB(x, y) >>> 24)
            .as("alpha en (%d,%d)", x, y)
            .isEqualTo(source.getRGB(x, y) >>> 24);
      }
    }
  }

  /**
   * Todas las muestras del raster están en [0,255]. Con INT_ARGB/INT_RGB es una garantía del tipo;
   * se comprueba sobre el raster para detectar un tipo de salida inesperado.
   */
  public static void assertChannelsInRange(BufferedImage img) {
    int[] px = new int[img.getRaster().getNumBands()];
    for (int y = 0; y < img.getHeight(); y++) {
      for (int x = 0; x < img.getWidth(); x++) {
        img.getRaster().getPixel(x, y, px);
        for (int v : px) {
          assertThat(v).as("muestra en (%d,%d)", x, y).isBetween(0, 255);
        }
      }
    }
  }

  public static void assertDiffers(BufferedImage a, BufferedImage b) {
    assertSameSize(a, b);
    for (int y = 0; y < a.getHeight(); y++) {
      for (int x = 0; x < a.getWidth(); x++) {
        if (a.getRGB(x, y) != b.getRGB(x, y)) {
          return;
        }
      }
    }
    throw new AssertionError("Las imágenes son idénticas y se esperaba que difirieran");
  }

  /** Copia de los píxeles (para comprobar que la entrada no se muta). */
  public static int[] snapshot(BufferedImage img) {
    return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
  }
}
