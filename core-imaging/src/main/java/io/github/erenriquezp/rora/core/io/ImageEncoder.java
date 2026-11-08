package io.github.erenriquezp.rora.core.io;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.image.Img;
import java.nio.file.Path;

/** Escribe imágenes. Las instancias no guardan estado y se pueden usar desde varios hilos. */
public interface ImageEncoder {

  FormatId format();

  /**
   * Escribe {@code image} en {@code target}, creándolo o truncándolo.
   *
   * @param metadata metadatos ya filtrados por la política: se escribe todo lo que contengan
   */
  void encode(
      Img image,
      ExportSettings settings,
      ImageMetadata metadata,
      Path target,
      CancellationToken token,
      Progress progress)
      throws ImagingException;
}
