package io.github.erenriquezp.rora.app.session;

import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.Renderer;
import io.github.erenriquezp.rora.service.ImageBridge;
import io.github.erenriquezp.rora.service.SourceFormat;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Imagen fuente de una sesión: la {@link Img} (lo único que se mantiene a resolución completa), su
 * formato de origen para la convolución legacy y, solo si ese formato es {@code native}, el {@code
 * BufferedImage} original (gris, paleta propia o {@code TYPE_CUSTOM}). En el resto de casos el
 * original se libera tras convertir.
 */
public final class SessionSource {

  private static final AtomicLong IDS = new AtomicLong();

  private final long id;
  private final Img image;
  private final String format;
  private final BufferedImage nativeImage;

  private SessionSource(long id, Img image, String format, BufferedImage nativeImage) {
    this.id = id;
    this.image = image;
    this.format = format;
    this.nativeImage = nativeImage;
  }

  /** Convierte {@code bi} (no se conserva una referencia salvo para formatos {@code native}). */
  public static SessionSource of(BufferedImage bi) {
    Objects.requireNonNull(bi, "bi");
    String fmt = SourceFormat.of(bi);
    Img img = ImageBridge.toImg(bi);
    return new SessionSource(
        IDS.incrementAndGet(), img, fmt, SourceFormat.needsNative(fmt) ? bi : null);
  }

  /** Id único en el proceso (forma parte de las claves de caché). */
  public long id() {
    return id;
  }

  public Img image() {
    return image;
  }

  public String format() {
    return format;
  }

  public Optional<BufferedImage> nativeImage() {
    return Optional.ofNullable(nativeImage);
  }

  public int width() {
    return image.width();
  }

  public int height() {
    return image.height();
  }

  Renderer.SourceImage asRenderSource() {
    return new Renderer.SourceImage(id, image);
  }

  /** {@code NativeSource} de esta fuente: el original solo para su propia {@code Img}. */
  SourceFormat.NativeSource nativeSource() {
    return new SourceFormat.NativeSource() {
      @Override
      public Optional<BufferedImage> nativeFor(Img img) {
        return img == image ? nativeImage() : Optional.empty();
      }

      @Override
      public Optional<BufferedImage> template() {
        return nativeImage();
      }
    };
  }
}
