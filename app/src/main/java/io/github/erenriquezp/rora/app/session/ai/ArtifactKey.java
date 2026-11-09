package io.github.erenriquezp.rora.app.session.ai;

import io.github.erenriquezp.rora.core.op.RenderKey;
import java.util.Objects;

/**
 * Identidad de un resultado de IA.
 *
 * @param prefix clave estructural de la imagen que entra al motor (solo en memoria)
 * @param modelSha256 hash del modelo
 * @param scale escala de salida
 * @param device dispositivo de cálculo
 */
public record ArtifactKey(RenderKey prefix, String modelSha256, int scale, String device) {

  public static final String CPU = "cpu";

  public ArtifactKey {
    Objects.requireNonNull(prefix, "prefix");
    Objects.requireNonNull(modelSha256, "modelSha256");
    Objects.requireNonNull(device, "device");
  }
}
