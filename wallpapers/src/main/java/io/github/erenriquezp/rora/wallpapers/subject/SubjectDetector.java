package io.github.erenriquezp.rora.wallpapers.subject;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ImagingException;
import java.util.List;

/**
 * Detector de sujetos (SPI). Las implementaciones son opcionales: sin detector, el encuadre
 * automático es el centrado. Debe ser determinista: la misma imagen da la misma lista.
 */
public interface SubjectDetector extends AutoCloseable {

  /** Identificador del detector y su versión, p. ej. {@code yunet-2023mar}. */
  String id();

  /**
   * Sujetos de {@code image}, en coordenadas normalizadas, ordenados por área descendente y luego
   * por {@code x} e {@code y}.
   */
  List<Subject> detect(Img image, CancellationToken token) throws ImagingException;

  @Override
  void close();

  /** Detector que no encuentra nada. */
  static SubjectDetector none() {
    return new SubjectDetector() {
      @Override
      public String id() {
        return "none";
      }

      @Override
      public List<Subject> detect(Img image, CancellationToken token) {
        token.throwIfCancelled();
        return List.of();
      }

      @Override
      public void close() {}
    };
  }
}
