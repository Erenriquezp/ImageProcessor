package io.github.erenriquezp.rora.ai.onnx;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.ai.api.TileRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** ORT crea siempre una carpeta temporal, pero con la ruta nativa fijada no debe copiar nada. */
class NoTempExtractionTest {

  @Test
  void loadingAModelLeavesNoFilesInTheTempDirectory() throws Exception {
    try (TileRunner r = new OnnxEngine().open(TestModels.named("nearest_x4", 4), 1)) {
      assertThat(r).isNotNull();
    }
    Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
    try (Stream<Path> dirs = Files.list(tmp)) {
      List<Path> ort =
          dirs.filter(p -> p.getFileName().toString().startsWith("onnxruntime-java")).toList();
      for (Path d : ort) {
        try (Stream<Path> files = Files.walk(d)) {
          assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
      }
    }
  }
}
