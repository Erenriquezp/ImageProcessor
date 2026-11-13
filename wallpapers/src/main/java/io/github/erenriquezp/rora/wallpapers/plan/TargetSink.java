package io.github.erenriquezp.rora.wallpapers.plan;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.FormatId;
import java.nio.file.Path;

/**
 * Dónde acaba cada destino renderizado. La aplicación escribe con el exportador atómico de la fase
 * 4; los tests, en memoria.
 */
@FunctionalInterface
public interface TargetSink {

  /** Archivo escrito y su tamaño. */
  record Written(Path file, long bytes) {}

  /**
   * Escribe un destino. Una excepción marca solo este destino como fallido; {@code
   * CancelledException} detiene la tanda.
   */
  Written write(PlannedTarget target, Img image, FormatId format, CancellationToken token)
      throws Exception;
}
