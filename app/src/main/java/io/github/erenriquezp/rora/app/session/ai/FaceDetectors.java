package io.github.erenriquezp.rora.app.session.ai;

import io.github.erenriquezp.rora.ai.api.AiEngines;
import io.github.erenriquezp.rora.wallpapers.subject.SubjectDetector;

/**
 * Detectores de sujetos para Wallpapers. El motor solo se ve desde esta capa; el resto de la
 * aplicación usa la interfaz {@link SubjectDetector}.
 */
public final class FaceDetectors {

  private FaceDetectors() {}

  /** YuNet incluido. No carga ORT hasta la primera detección. */
  public static SubjectDetector bundled() {
    return AiEngines.faceDetector();
  }
}
