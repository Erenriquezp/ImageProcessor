package io.github.erenriquezp.rora.testsupport;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Imágenes deterministas para los tests diferenciales legacy vs núcleo: varios tipos de {@link
 * BufferedImage}, tamaños (incluidos 1×1, impares y multibanda) y contenidos, con semilla fija.
 * Cada imagen lleva una descripción que permite reproducir un fallo.
 */
public final class EquivalenceImages {

  private EquivalenceImages() {}

  /** Tipos de entrada probados (los que puede devolver la carga de imágenes o el legacy). */
  public static final List<Integer> TYPES =
      List.of(
          BufferedImage.TYPE_INT_ARGB,
          BufferedImage.TYPE_INT_RGB,
          BufferedImage.TYPE_3BYTE_BGR,
          BufferedImage.TYPE_4BYTE_ABGR,
          BufferedImage.TYPE_BYTE_GRAY,
          BufferedImage.TYPE_BYTE_INDEXED,
          BufferedImage.TYPE_INT_ARGB_PRE);

  /** {ancho, alto}: 1×1, pequeños, impares, una fila/columna, una banda y varias bandas. */
  public static final int[][] SIZES = {
    {1, 1}, {2, 2}, {3, 5}, {7, 1}, {1, 7}, {33, 17}, {64, 48}, {129, 200}
  };

  /** Contenido de la imagen. */
  public enum Content {
    /** Ruido ARGB uniforme (alpha incluido). */
    NOISE,
    /** Canales tomados de valores frontera: 0, 1, 15, 16, 127, 128, 239, 240, 254, 255. */
    EXTREMES,
    /** Un único color (histograma de una sola cubeta). */
    SOLID
  }

  private static final int[] EXTREME_VALUES = {0, 1, 15, 16, 127, 128, 239, 240, 254, 255};

  /** Imagen con su descripción reproducible. */
  public record Sample(String description, BufferedImage image) {
    @Override
    public String toString() {
      return description;
    }
  }

  public static String typeName(int type) {
    return switch (type) {
      case BufferedImage.TYPE_INT_ARGB -> "INT_ARGB";
      case BufferedImage.TYPE_INT_RGB -> "INT_RGB";
      case BufferedImage.TYPE_INT_BGR -> "INT_BGR";
      case BufferedImage.TYPE_3BYTE_BGR -> "3BYTE_BGR";
      case BufferedImage.TYPE_4BYTE_ABGR -> "4BYTE_ABGR";
      case BufferedImage.TYPE_4BYTE_ABGR_PRE -> "4BYTE_ABGR_PRE";
      case BufferedImage.TYPE_BYTE_GRAY -> "BYTE_GRAY";
      case BufferedImage.TYPE_USHORT_GRAY -> "USHORT_GRAY";
      case BufferedImage.TYPE_BYTE_INDEXED -> "BYTE_INDEXED";
      case BufferedImage.TYPE_BYTE_BINARY -> "BYTE_BINARY";
      case BufferedImage.TYPE_INT_ARGB_PRE -> "INT_ARGB_PRE";
      case BufferedImage.TYPE_USHORT_565_RGB -> "USHORT_565_RGB";
      case BufferedImage.TYPE_USHORT_555_RGB -> "USHORT_555_RGB";
      default -> "TYPE_" + type;
    };
  }

  /** Semilla de una combinación: fija y distinta por tamaño, tipo y contenido. */
  public static long seed(int type, int w, int h, Content c) {
    return 0x5EED_0000L + type * 1_000_003L + w * 10_007L + h * 101L + c.ordinal();
  }

  /** Imagen del tipo y contenido dados; los valores se escriben con {@code setRGB}. */
  public static BufferedImage create(int type, int w, int h, Content c, long seed) {
    BufferedImage img = new BufferedImage(w, h, type);
    SplittableRandom rnd = new SplittableRandom(seed);
    int solid = rnd.nextInt();
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int argb =
            switch (c) {
              case NOISE -> rnd.nextInt();
              case SOLID -> solid;
              case EXTREMES -> {
                int a = EXTREME_VALUES[rnd.nextInt(EXTREME_VALUES.length)];
                int r = EXTREME_VALUES[rnd.nextInt(EXTREME_VALUES.length)];
                int g = EXTREME_VALUES[rnd.nextInt(EXTREME_VALUES.length)];
                int b = EXTREME_VALUES[rnd.nextInt(EXTREME_VALUES.length)];
                yield (a << 24) | (r << 16) | (g << 8) | b;
              }
            };
        img.setRGB(x, y, argb);
      }
    }
    return img;
  }

  /** Todas las combinaciones de tamaño y contenido para un tipo. */
  public static List<Sample> samples(int type) {
    List<Sample> out = new ArrayList<>();
    for (int[] s : SIZES) {
      for (Content c : Content.values()) {
        long seed = seed(type, s[0], s[1], c);
        String d =
            typeName(type)
                + " "
                + s[0]
                + "x"
                + s[1]
                + " "
                + c
                + " seed=0x"
                + Long.toHexString(seed);
        out.add(new Sample(d, create(type, s[0], s[1], c, seed)));
      }
    }
    return out;
  }

  /**
   * Los 2^24 colores RGB en una imagen {@code TYPE_INT_ARGB} de 4096×4096, con alpha
   * pseudoaleatorio de semilla fija.
   */
  public static BufferedImage allColors(long alphaSeed) {
    BufferedImage img = new BufferedImage(4096, 4096, BufferedImage.TYPE_INT_ARGB);
    int[] px = new int[1 << 24];
    SplittableRandom rnd = new SplittableRandom(alphaSeed);
    for (int rgb = 0; rgb < px.length; rgb++) {
      px[rgb] = (rnd.nextInt(256) << 24) | rgb;
    }
    img.getRaster().setDataElements(0, 0, 4096, 4096, px);
    return img;
  }

  /** Píxeles por {@code getRGB} en bloque. */
  public static int[] pixels(BufferedImage img) {
    return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
  }
}
