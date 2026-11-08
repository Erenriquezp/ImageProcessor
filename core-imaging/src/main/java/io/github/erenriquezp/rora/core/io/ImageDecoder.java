package io.github.erenriquezp.rora.core.io;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import java.nio.file.Path;
import java.util.Set;

/** Lee imágenes. Las instancias no guardan estado y se pueden usar desde varios hilos. */
public interface ImageDecoder {

  Set<FormatId> formats();

  /** Inspecciona el archivo sin decodificar sus píxeles. */
  ImageInfo inspect(Path file, CancellationToken token) throws ImagingException;

  /** Decodifica a sRGB de 8 bits, ya orientada. */
  DecodedImage decode(Path file, DecodeOptions options, CancellationToken token, Progress progress)
      throws ImagingException;
}
