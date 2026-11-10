package io.github.erenriquezp.rora.ai.api;

import io.github.erenriquezp.rora.ai.onnx.OnnxEngine;

/** Punto de entrada: la aplicación solo ve estas interfaces. */
public final class AiEngines {

  private AiEngines() {}

  /** Motor local en CPU. La biblioteca nativa no se carga hasta abrir un modelo. */
  public static AiEngine onnx() {
    return new OnnxEngine();
  }

  /** El modelo incluido con la aplicación. */
  public static ModelSource bundledModel() {
    return new BundledModelSource();
  }

  /** Hilos por defecto: la mitad de los lógicos, dejando siempre uno libre para la interfaz. */
  public static int defaultThreads(int logicalProcessors) {
    if (logicalProcessors <= 2) {
      return Math.max(1, logicalProcessors);
    }
    return Math.max(2, Math.min(logicalProcessors / 2, logicalProcessors - 1));
  }
}
