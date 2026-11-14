package io.github.erenriquezp.rora.app.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.app.diag.Logs;
import io.github.erenriquezp.rora.app.diag.Redactor;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ExportSettings;
import io.github.erenriquezp.rora.core.io.FormatId;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Una exportación que falla con metadatos sensibles (GPS en XMP, autor en el texto) y píxeles
 * reconocibles: nada de eso llega al registro. Protege contra futuras llamadas que registren
 * objetos de imagen o de metadatos.
 */
class NoImageDataInLogsTest {

  private static final String GPS = "<exif:GPSLatitude>41,23.0451N</exif:GPSLatitude>";

  @TempDir Path dir;

  @Test
  void aFailedExportLeavesNoMetadataOrPixelsInTheLog() throws Exception {
    Path logs = dir.resolve("logs");
    Path notAFolder = Files.writeString(dir.resolve("archivo-normal"), "x");
    int[] px = new int[16 * 16];
    Arrays.fill(px, 0xFF123456);
    Img image = Img.adopt(16, 16, px);
    ImageMetadata metadata =
        new ImageMetadata(
            Optional.empty(),
            Optional.of(GPS.getBytes(StandardCharsets.UTF_8)),
            Optional.empty(),
            Optional.empty(),
            Map.of("Author", "Persona Fotografiada"),
            false);
    ExportTarget target =
        new ExportTarget(
            notAFolder.resolve("sub"),
            "salida.jpg",
            ExportTarget.Collision.ASK,
            false,
            false,
            Optional.empty());
    try (Logs installed =
        Logs.install(
            logs,
            new Redactor(new Redactor.Context(null, null, null, null, null, null)),
            false,
            Level.FINE,
            ZoneOffset.UTC,
            Logs.LIMIT_BYTES,
            Logs.COUNT)) {
      assertThatThrownBy(
              () ->
                  StudioIo.export(
                      image,
                      metadata,
                      ExportSettings.defaults(FormatId.JPEG)
                          .withMetadata(ExportSettings.MetadataPolicy.KEEP),
                      Optional.empty(),
                      target,
                      CancellationToken.none(),
                      Progress.NONE))
          .isInstanceOf(Exception.class);
      System.getLogger("rora.app")
          .log(System.Logger.Level.WARNING, "exportación fallida", new RuntimeException("prueba"));
      installed.tail(1, 1);
    }
    String text = Files.readString(logs.resolve("rora-0.log"), StandardCharsets.UTF_8);
    assertThat(text).contains("exportación fallida");
    assertThat(text)
        .doesNotContain("GPSLatitude")
        .doesNotContain("41,23")
        .doesNotContain("Persona Fotografiada")
        .doesNotContain("123456")
        .doesNotContain("ImageMetadata")
        .doesNotContain("archivo-normal");
  }
}
