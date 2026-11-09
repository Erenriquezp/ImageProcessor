package io.github.erenriquezp.rora.ai.api;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;

/** Sesión abierta sobre un modelo. No es segura para hilos: la usa un solo hilo a la vez. */
public interface TileRunner extends AutoCloseable {

  /**
   * Ejecuta el modelo sobre un tile.
   *
   * @param chw valores en [0,1], en orden canales, filas, columnas (RGB)
   * @return salida con la misma organización, de lado {@code h·scale} por {@code w·scale}
   * @throws io.github.erenriquezp.rora.core.exec.CancelledException si se cancela el token
   */
  float[] run(float[] chw, int h, int w, CancellationToken token) throws AiFailure;

  /** Libera la sesión. Es idempotente. */
  @Override
  void close();
}
