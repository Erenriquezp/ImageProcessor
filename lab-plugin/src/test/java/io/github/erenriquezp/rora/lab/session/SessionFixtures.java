package io.github.erenriquezp.rora.lab.session;

import io.github.erenriquezp.rora.app.session.*;
import io.github.erenriquezp.rora.domain.ColorSpaceType;
import io.github.erenriquezp.rora.domain.ConvolutionKernel;
import io.github.erenriquezp.rora.domain.DepthSource;
import io.github.erenriquezp.rora.domain.EqualizeMode;
import io.github.erenriquezp.rora.domain.FragmentBlendMode;
import io.github.erenriquezp.rora.domain.LogicOpType;
import io.github.erenriquezp.rora.domain.StencilPattern;
import io.github.erenriquezp.rora.domain.StretchMode;
import io.github.erenriquezp.rora.domain.TextureFilterMode;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.util.SplittableRandom;

/** Parámetros de UI e imágenes deterministas para los tests de la sesión (fase 2). */
final class SessionFixtures {

  private SessionFixtures() {}

  /** Valor de slider continuo como lo lee la UI: {@code (float) slider.getValue()}. */
  private static float slider(SplittableRandom r, double min, double max) {
    return (float) (min + r.nextDouble() * (max - min));
  }

  private static int sliderInt(SplittableRandom r, int min, int max) {
    return (int) Math.round(min + r.nextDouble() * (max - min));
  }

  private static <E> E pick(SplittableRandom r, E[] values) {
    return values[r.nextInt(values.length)];
  }

  private static int channel(SplittableRandom r) {
    return (int) Math.round(r.nextDouble() * 255);
  }

  /** Parámetros con los rangos de {@code EditorFilterPane}. */
  static FilterParams randomParams(SplittableRandom r) {
    return new FilterParams(
        sliderInt(r, -100, 100),
        slider(r, 0, 2),
        slider(r, 0, 2),
        2 + r.nextInt(254),
        2 + r.nextInt(254),
        r.nextBoolean(),
        r.nextBoolean(),
        r.nextBoolean(),
        2 + r.nextInt(63),
        sliderInt(r, 0, 255),
        sliderInt(r, 0, 255),
        channel(r),
        channel(r),
        channel(r),
        pick(r, StretchMode.values()),
        pick(r, ConvolutionKernel.values()),
        pick(r, DepthSource.values()),
        sliderInt(r, 0, 255),
        sliderInt(r, 2, 64),
        slider(r, 0.25, 4.0),
        pick(r, TextureFilterMode.values()),
        sliderInt(r, 0, 255),
        r.nextBoolean(),
        2 + r.nextInt(7),
        pick(r, StencilPattern.values()),
        pick(r, FragmentBlendMode.values()),
        slider(r, 0, 1),
        pick(r, LogicOpType.values()),
        sliderInt(r, 0, 255),
        channel(r),
        channel(r),
        channel(r),
        channel(r),
        channel(r),
        channel(r),
        pick(r, EqualizeMode.values()),
        slider(r, 0, 2),
        slider(r, 0, 2),
        slider(r, 0, 2),
        slider(r, 0, 1),
        slider(r, -1, 2),
        slider(r, 0, 3),
        slider(r, -128, 128),
        sliderInt(r, 0, 255),
        sliderInt(r, 0, 64),
        slider(r, 0, 2),
        slider(r, -180, 180),
        pick(r, ColorSpaceType.values()),
        r.nextInt(6) == 0 ? 0f : (float) (sliderInt(r, 0, 100) / 100.0),
        r.nextBoolean(),
        r.nextBoolean());
  }

  /** Tipos de imagen cargada que cubren los formatos de la convolución. */
  static final String[] IMAGE_KINDS = {
    "INT_ARGB", "3BYTE_BGR", "4BYTE_ABGR", "INT_RGB", "BYTE_GRAY", "BYTE_INDEXED_CUSTOM"
  };

  /** Imagen de prueba del tipo indicado con contenido pseudoaleatorio. */
  static BufferedImage image(String kind, int w, int h, long seed) {
    SplittableRandom r = new SplittableRandom(seed);
    BufferedImage bi =
        switch (kind) {
          case "INT_ARGB" -> new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
          case "3BYTE_BGR" -> new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
          case "4BYTE_ABGR" -> new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
          case "INT_RGB" -> new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
          case "BYTE_GRAY" -> new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
          case "BYTE_INDEXED_CUSTOM" -> {
            byte[] rr = new byte[256];
            byte[] gg = new byte[256];
            byte[] bb = new byte[256];
            for (int i = 0; i < 256; i++) {
              rr[i] = (byte) r.nextInt(256);
              gg[i] = (byte) r.nextInt(256);
              bb[i] = (byte) r.nextInt(256);
            }
            yield new BufferedImage(
                w, h, BufferedImage.TYPE_BYTE_INDEXED, new IndexColorModel(8, 256, rr, gg, bb));
          }
          default -> throw new IllegalArgumentException(kind);
        };
    WritableRaster raster = bi.getRaster();
    int bands = raster.getNumBands();
    int[] sizes = bi.getSampleModel().getSampleSize();
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        for (int b = 0; b < bands; b++) {
          raster.setSample(x, y, b, r.nextInt(1 << sizes[b]));
        }
      }
    }
    return bi;
  }

  static boolean sameRgb(BufferedImage a, BufferedImage b) {
    if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
      return false;
    }
    int w = a.getWidth();
    int h = a.getHeight();
    return java.util.Arrays.equals(
        a.getRGB(0, 0, w, h, null, 0, w), b.getRGB(0, 0, w, h, null, 0, w));
  }
}
