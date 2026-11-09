package io.github.erenriquezp.rora.ai.api;

import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;

/** Motor de inferencia. Las implementaciones no exponen tipos de ONNX Runtime. */
public interface AiEngine {

  /**
   * Abre una sesión con el modelo.
   *
   * @param threads hilos de cálculo de la sesión
   */
  TileRunner open(ModelSource model, int threads) throws AiFailure;
}
