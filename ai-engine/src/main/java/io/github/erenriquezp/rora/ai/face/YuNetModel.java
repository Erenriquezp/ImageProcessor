package io.github.erenriquezp.rora.ai.face;

import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** El detector de caras incluido. Procedencia y licencia en {@code models/MODEL_NOTICE.md}. */
public final class YuNetModel {

  /** Identificador del detector. */
  public static final String ID = "yunet-2023mar";

  public static final String SHA256 =
      "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4";

  public static final long BYTES = 232_589L;

  static final String RESOURCE =
      "/io/github/erenriquezp/rora/ai/models/face_detection_yunet_2023mar.onnx";

  private YuNetModel() {}

  /** Bytes del modelo con el hash comprobado sobre el mismo array que se cargará. */
  public static byte[] readVerified() throws AiFailure {
    try (InputStream in = YuNetModel.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new AiFailure(AiFailure.Reason.MODEL_CORRUPT, "Falta el recurso " + RESOURCE, null);
      }
      return verified(in.readAllBytes(), SHA256);
    } catch (IOException e) {
      throw new AiFailure(AiFailure.Reason.MODEL_CORRUPT, "No se pudo leer el modelo", e);
    }
  }

  /** Devuelve {@code data} si su SHA-256 es {@code expected}. */
  public static byte[] verified(byte[] data, String expected) throws AiFailure {
    String actual;
    try {
      actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    if (!actual.equalsIgnoreCase(expected)) {
      throw new AiFailure(AiFailure.Reason.MODEL_CORRUPT, "SHA-256 inesperado: " + actual, null);
    }
    return data;
  }
}
