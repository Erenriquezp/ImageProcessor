package io.github.erenriquezp.rora.ai.onnx;

import io.github.erenriquezp.rora.ai.api.TileRunner;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;

/** Abre modelos sintéticos desde pruebas de otros paquetes. */
public final class OnnxEngineAccess {

  private OnnxEngineAccess() {}

  public static TileRunner open(String model, int scale) throws AiFailure {
    return new OnnxEngine().open(TestModels.named(model, scale), 2);
  }
}
