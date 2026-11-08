package io.github.erenriquezp.rora.service;

import io.github.erenriquezp.rora.core.image.Img;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

/**
 * Reenvío a {@link io.github.erenriquezp.rora.app.imaging.SourceFormat}, que pasó al producto: mantiene sin editar el
 * test heredado de este paquete. Se puede quitar cambiando un import de {@code
 * SessionServicesTest}.
 */
public final class SourceFormat {

  public static final String ARGB = io.github.erenriquezp.rora.app.imaging.SourceFormat.ARGB;
  public static final String NATIVE = io.github.erenriquezp.rora.app.imaging.SourceFormat.NATIVE;
  public static final List<String> IDS = io.github.erenriquezp.rora.app.imaging.SourceFormat.IDS;

  private SourceFormat() {}

  /** Mismo contrato que el original. */
  public interface NativeSource extends io.github.erenriquezp.rora.app.imaging.SourceFormat.NativeSource {
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

  public static String of(BufferedImage bi) {
    return io.github.erenriquezp.rora.app.imaging.SourceFormat.of(bi);
  }

  public static boolean needsNative(String format) {
    return io.github.erenriquezp.rora.app.imaging.SourceFormat.needsNative(format);
  }

  public static String afterConvolution(String format, Optional<BufferedImage> nativeTemplate) {
    return io.github.erenriquezp.rora.app.imaging.SourceFormat.afterConvolution(format, nativeTemplate);
  }

  public static BufferedImage materialize(Img img, String format, NativeSource nativeSource) {
    return io.github.erenriquezp.rora.app.imaging.SourceFormat.materialize(img, format, nativeSource);
  }

  static int typeOf(String format) {
    return io.github.erenriquezp.rora.app.imaging.SourceFormat.typeOf(format);
  }
}
