package io.github.erenriquezp.rora.app.imaging;

import io.github.erenriquezp.rora.core.image.Img;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ConvolveOp;
import java.awt.image.IndexColorModel;
import java.awt.image.Kernel;
import java.awt.image.WritableRaster;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Formato de origen de la imagen que recibe la convolución legacy (fase 2, decisión 1).
 *
 * <p>{@code ConvolveOp} da resultados distintos según el tipo de {@code BufferedImage} (sobre ARGB
 * convoluciona también el alpha). La receta trabaja con {@link Img} (ARGB), así que el paso {@code
 * legacy.convolution} lleva un parámetro oculto {@code sourceFormat} con el id de uno de estos
 * formatos y el adaptador reconstruye ese tipo antes de llamar al legacy. Medición: ver {@code
 * .planning/FASE2_diseno.md} §3.1 y {@code ConvolutionFormatTest}.
 *
 * <ul>
 *   <li>{@code argb}: {@code INT_ARGB} y {@code 4BYTE_ABGR} (dan lo mismo que ARGB).
 *   <li>Reconstruibles sin pérdida desde los {@code int} ARGB: {@code int-rgb}, {@code int-bgr},
 *       {@code 3byte-bgr}, {@code ushort-565}, {@code ushort-555}, {@code int-argb-pre}, {@code
 *       4byte-abgr-pre}, {@code byte-binary-default} y {@code byte-indexed-default} (paleta por
 *       defecto del tipo).
 *   <li>{@code native}: tipos con pérdida (gris, paleta propia, {@code TYPE_CUSTOM}); se usa el
 *       {@code BufferedImage} original, que la sesión conserva solo en este caso.
 *   <li>{@code byte-gray}, {@code ushort-gray}: salida de una convolución sobre gris, reconstruida
 *       con pérdida (desviación documentada de la cadena convolución→convolución).
 * </ul>
 */
public final class SourceFormat {

  public static final String ARGB = "argb";
  public static final String NATIVE = "native";

  private static final Map<String, Integer> TYPES =
      Map.ofEntries(
          Map.entry("int-rgb", BufferedImage.TYPE_INT_RGB),
          Map.entry("int-bgr", BufferedImage.TYPE_INT_BGR),
          Map.entry("3byte-bgr", BufferedImage.TYPE_3BYTE_BGR),
          Map.entry("ushort-565", BufferedImage.TYPE_USHORT_565_RGB),
          Map.entry("ushort-555", BufferedImage.TYPE_USHORT_555_RGB),
          Map.entry("int-argb-pre", BufferedImage.TYPE_INT_ARGB_PRE),
          Map.entry("4byte-abgr-pre", BufferedImage.TYPE_4BYTE_ABGR_PRE),
          Map.entry("byte-binary-default", BufferedImage.TYPE_BYTE_BINARY),
          Map.entry("byte-indexed-default", BufferedImage.TYPE_BYTE_INDEXED),
          Map.entry("byte-gray", BufferedImage.TYPE_BYTE_GRAY),
          Map.entry("ushort-gray", BufferedImage.TYPE_USHORT_GRAY));

  /** Ids válidos, en un orden estable (valores del {@code EnumParam} oculto). */
  public static final List<String> IDS =
      List.of(
          ARGB,
          "int-rgb",
          "int-bgr",
          "3byte-bgr",
          "ushort-565",
          "ushort-555",
          "int-argb-pre",
          "4byte-abgr-pre",
          "byte-binary-default",
          "byte-indexed-default",
          "byte-gray",
          "ushort-gray",
          NATIVE);

  private SourceFormat() {}

  /** Formato de una imagen cargada. */
  public static String of(BufferedImage bi) {
    return switch (bi.getType()) {
      case BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_4BYTE_ABGR -> ARGB;
      case BufferedImage.TYPE_INT_RGB -> "int-rgb";
      case BufferedImage.TYPE_INT_BGR -> "int-bgr";
      case BufferedImage.TYPE_3BYTE_BGR -> "3byte-bgr";
      case BufferedImage.TYPE_USHORT_565_RGB -> "ushort-565";
      case BufferedImage.TYPE_USHORT_555_RGB -> "ushort-555";
      case BufferedImage.TYPE_INT_ARGB_PRE -> "int-argb-pre";
      case BufferedImage.TYPE_4BYTE_ABGR_PRE -> "4byte-abgr-pre";
      case BufferedImage.TYPE_BYTE_BINARY -> hasDefaultPalette(bi) ? "byte-binary-default" : NATIVE;
      case BufferedImage.TYPE_BYTE_INDEXED ->
          hasDefaultPalette(bi) ? "byte-indexed-default" : NATIVE;
      default -> NATIVE; // gris 8/16 bit y TYPE_CUSTOM
    };
  }

  /** ¿Hace falta conservar el {@code BufferedImage} original para este formato? */
  public static boolean needsNative(String format) {
    return NATIVE.equals(format);
  }

  /**
   * Formato de la salida de {@code ConvolveOp} para una entrada con este formato ({@code native}
   * necesita la imagen original como modelo). Se mide con una sonda 3×3 del mismo modelo de color.
   */
  public static String afterConvolution(String format, Optional<BufferedImage> nativeTemplate) {
    BufferedImage probe;
    if (NATIVE.equals(format)) {
      probe = blankLike(nativeTemplate.orElseThrow(), 3, 3);
    } else if (ARGB.equals(format)) {
      return ARGB;
    } else {
      probe = new BufferedImage(3, 3, typeOf(format));
    }
    BufferedImage out =
        new ConvolveOp(new Kernel(1, 1, new float[] {1f}), ConvolveOp.EDGE_NO_OP, null)
            .filter(probe, null);
    return switch (out.getType()) {
      case BufferedImage.TYPE_BYTE_GRAY -> "byte-gray";
      case BufferedImage.TYPE_USHORT_GRAY -> "ushort-gray";
      case BufferedImage.TYPE_BYTE_BINARY,
          BufferedImage.TYPE_BYTE_INDEXED,
          BufferedImage.TYPE_CUSTOM ->
          ARGB; // con pérdida y sin tipo reconstruible: desviación documentada
      default -> of(out);
    };
  }

  /**
   * {@code img} como el {@code BufferedImage} que tendría la cadena de la app con ese formato: el
   * original si el formato es {@code native} y {@code img} es la fuente; si no, {@code img}
   * reconstruida en el tipo del formato ({@code argb} = {@code TYPE_INT_ARGB}). La usan la entrada
   * de la convolución legacy y la exportación (mismo tipo que guardaba la app).
   */
  public static BufferedImage materialize(Img img, String format, NativeSource nativeSource) {
    if (ARGB.equals(format)) {
      return AwtImg.toBufferedImage(img);
    }
    BufferedImage target;
    if (NATIVE.equals(format)) {
      Optional<BufferedImage> exact = nativeSource.nativeFor(img);
      if (exact.isPresent()) {
        return exact.get();
      }
      // Preview sobre proxy (aproximado por diseño): mismo modelo de color, otros píxeles.
      BufferedImage template =
          nativeSource
              .template()
              .orElseThrow(() -> new IllegalStateException("Formato native sin imagen original"));
      target = blankLike(template, img.width(), img.height());
    } else {
      target = new BufferedImage(img.width(), img.height(), typeOf(format));
    }
    target.setRGB(0, 0, img.width(), img.height(), img.toArray(), 0, img.width());
    return target;
  }

  public static int typeOf(String format) {
    Integer t = TYPES.get(format);
    if (t == null) {
      throw new IllegalArgumentException("Formato sin tipo AWT: " + format);
    }
    return t;
  }

  private static BufferedImage blankLike(BufferedImage model, int w, int h) {
    ColorModel cm = model.getColorModel();
    WritableRaster r = cm.createCompatibleWritableRaster(w, h);
    return new BufferedImage(cm, r, cm.isAlphaPremultiplied(), null);
  }

  private static boolean hasDefaultPalette(BufferedImage bi) {
    if (!(bi.getColorModel() instanceof IndexColorModel icm)) {
      return false;
    }
    if (!(new BufferedImage(1, 1, bi.getType()).getColorModel() instanceof IndexColorModel def)) {
      return false;
    }
    if (icm.getMapSize() != def.getMapSize() || icm.getPixelSize() != def.getPixelSize()) {
      return false;
    }
    int[] a = new int[icm.getMapSize()];
    int[] b = new int[def.getMapSize()];
    icm.getRGBs(a);
    def.getRGBs(b);
    return java.util.Arrays.equals(a, b) && icm.getTransparentPixel() == def.getTransparentPixel();
  }

  /** Acceso al {@code BufferedImage} original de la sesión (formato {@code native}). */
  public interface NativeSource {
    /** El original si {@code img} es la instancia fuente de la sesión. */
    Optional<BufferedImage> nativeFor(Img img);

    /** El original como modelo de color (para el preview aproximado). */
    Optional<BufferedImage> template();

    NativeSource NONE =
        new NativeSource() {
          @Override
          public Optional<BufferedImage> nativeFor(Img img) {
            return Optional.empty();
          }

          @Override
          public Optional<BufferedImage> template() {
            return Optional.empty();
          }
        };
  }
}
