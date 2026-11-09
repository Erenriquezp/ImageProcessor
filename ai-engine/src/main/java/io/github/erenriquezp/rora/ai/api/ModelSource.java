package io.github.erenriquezp.rora.ai.api;

import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;

/** Origen de un modelo. Hoy solo existe el incluido con la aplicación. */
public interface ModelSource {

  ModelInfo info();

  /**
   * Bytes del modelo, ya comprobados: el hash se calcula sobre el mismo array que se cargará.
   *
   * @throws AiFailure con {@code MODEL_CORRUPT} si el hash no coincide
   */
  byte[] readVerified() throws AiFailure;
}
