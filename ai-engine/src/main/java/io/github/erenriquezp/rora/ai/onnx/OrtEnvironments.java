package io.github.erenriquezp.rora.ai.onnx;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtLoggingLevel;

/**
 * El único sitio que obtiene el entorno de ONNX Runtime. Las compilaciones oficiales de ORT traen
 * la telemetría activada por defecto (su {@code Privacy.md}; en Windows, eventos ETW que el sistema
 * puede recoger); aquí se desactiva nada más crearlo. Según su documentación, ORT puede haber
 * emitido ya un evento mínimo de inicio antes de esta llamada: eso no se puede evitar sin compilar
 * ORT con {@code --no_telemetry}.
 */
final class OrtEnvironments {

  private static boolean telemetryOff;

  private OrtEnvironments() {}

  /** Entorno compartido, con registro solo de errores fatales y sin telemetría. */
  static synchronized OrtEnvironment quiet() throws OrtException {
    OrtEnvironment env =
        OrtEnvironment.getEnvironment(OrtLoggingLevel.ORT_LOGGING_LEVEL_FATAL, "rora");
    if (!telemetryOff) {
      env.setTelemetry(false);
      telemetryOff = true;
    }
    return env;
  }

  static synchronized boolean telemetryDisabled() {
    return telemetryOff;
  }
}
