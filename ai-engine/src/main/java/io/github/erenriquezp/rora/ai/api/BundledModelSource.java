package io.github.erenriquezp.rora.ai.api;

import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** El modelo incluido en el módulo. Procedencia en {@code models/MODEL_NOTICE.md}. */
final class BundledModelSource implements ModelSource {

  static final ModelInfo INFO =
      new ModelInfo(
          "realesr-general-x4v3",
          4,
          256,
          32,
          "7b10e80021ec3e0ff35017ae5022791879d93a799b2e201e1010a37510202266",
          4_866_420L,
          "BSD-3-Clause",
          "Real-ESRGAN, Xintao Wang et al.",
          "v0.2.5.0");

  private static final String RESOURCE =
      "/io/github/erenriquezp/rora/ai/models/realesr-general-x4v3.onnx";

  @Override
  public ModelInfo info() {
    return INFO;
  }

  @Override
  public byte[] readVerified() throws AiFailure {
    try (InputStream in = BundledModelSource.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new AiFailure(AiFailure.Reason.MODEL_CORRUPT, "Falta el recurso " + RESOURCE, null);
      }
      return verified(in.readAllBytes(), INFO.sha256());
    } catch (IOException e) {
      throw new AiFailure(AiFailure.Reason.MODEL_CORRUPT, "No se pudo leer el modelo", e);
    }
  }

  /** Devuelve {@code data} si su hash es {@code expected}. */
  static byte[] verified(byte[] data, String expected) throws AiFailure {
    String actual = sha256(data);
    if (!actual.equalsIgnoreCase(expected)) {
      throw new AiFailure(AiFailure.Reason.MODEL_CORRUPT, "SHA-256 inesperado: " + actual, null);
    }
    return data;
  }

  private static String sha256(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
