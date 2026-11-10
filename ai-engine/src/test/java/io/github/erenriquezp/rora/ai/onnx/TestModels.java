package io.github.erenriquezp.rora.ai.onnx;

import io.github.erenriquezp.rora.ai.api.ModelInfo;
import io.github.erenriquezp.rora.ai.api.ModelSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Modelos sintéticos de {@code src/test/resources/models}. */
public final class TestModels {

  private TestModels() {}

  public static byte[] bytes(String name) {
    try (InputStream in = TestModels.class.getResourceAsStream("/models/" + name + ".onnx")) {
      if (in == null) {
        throw new IllegalArgumentException(name);
      }
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Fuente con los bytes tal cual, sin comprobar el hash. */
  public static ModelSource raw(byte[] data, int scale) {
    ModelInfo info = new ModelInfo("test", scale, 256, 32, "00", data.length, "", "", "");
    return new ModelSource() {
      @Override
      public ModelInfo info() {
        return info;
      }

      @Override
      public byte[] readVerified() {
        return data;
      }
    };
  }

  public static ModelSource named(String name, int scale) {
    return raw(bytes(name), scale);
  }
}
