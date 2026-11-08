package com.example.imageprocessor.testsupport;

import java.awt.image.BufferedImage;
import java.util.Random;

/** Generador determinista de imágenes sintéticas pequeñas (sin aleatoriedad sin semilla). */
public final class TestImages {

  public enum Pattern {
    GRADIENT,
    CHECKER,
    NOISE,
    HARD_EDGES
  }

  /** Tipo de imagen: RGB opaca o ARGB con alpha parcial. */
  public enum Kind {
    RGB(BufferedImage.TYPE_INT_RGB),
    ARGB(BufferedImage.TYPE_INT_ARGB);

    final int awtType;

    Kind(int awtType) {
      this.awtType = awtType;
    }
  }

  /** Semilla fija del patrón NOISE. */
  public static final long NOISE_SEED = 12345L;

  /** Tamaños estándar: 1x1, 2x2, 16x16, 33x17 (impares) y 64x48. */
  public static final int[][] SIZES = {{1, 1}, {2, 2}, {16, 16}, {33, 17}, {64, 48}};

  private TestImages() {}

  public static BufferedImage create(Pattern pattern, Kind kind, int w, int h) {
    BufferedImage img = new BufferedImage(w, h, kind.awtType);
    Random rnd = new Random(NOISE_SEED);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int r;
        int g;
        int b;
        switch (pattern) {
          case GRADIENT -> {
            r = w > 1 ? x * 255 / (w - 1) : 128;
            g = h > 1 ? y * 255 / (h - 1) : 64;
            b = (r + g) / 2;
          }
          case CHECKER -> {
            boolean on = ((x / 2) + (y / 2)) % 2 == 0;
            r = on ? 230 : 25;
            g = on ? 200 : 60;
            b = on ? 40 : 220;
          }
          case NOISE -> {
            r = rnd.nextInt(256);
            g = rnd.nextInt(256);
            b = rnd.nextInt(256);
          }
          case HARD_EDGES -> {
            boolean left = x < w / 2;
            boolean top = y < h / 2;
            r = left ? 255 : 0;
            g = top ? 255 : 0;
            b = (left ^ top) ? 255 : 0;
          }
          default -> throw new IllegalStateException();
        }
        int a = kind == Kind.ARGB ? partialAlpha(x, w) : 255;
        img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
      }
    }
    return img;
  }

  /** Alpha parcial determinista: de 255 (izquierda) a ~64 (derecha), nunca 0. */
  private static int partialAlpha(int x, int w) {
    return w > 1 ? 255 - (x * 191 / (w - 1)) : 200;
  }

  public static String name(Pattern p, Kind k, int w, int h) {
    return p.name().toLowerCase() + "-" + k.name().toLowerCase() + "-" + w + "x" + h;
  }
}
