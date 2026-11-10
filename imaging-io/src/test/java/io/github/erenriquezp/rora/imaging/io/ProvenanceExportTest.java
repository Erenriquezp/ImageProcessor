package io.github.erenriquezp.rora.imaging.io;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ExportSettings;
import io.github.erenriquezp.rora.core.io.ExportSettings.MetadataPolicy;
import io.github.erenriquezp.rora.core.io.FormatId;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import io.github.erenriquezp.rora.core.io.ProvenanceXmp;
import io.github.erenriquezp.rora.core.io.ProvenanceXmp.Provenance;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** La marca de IA en cada formato y con cada política de metadatos. */
class ProvenanceExportTest {

  private static final Provenance P = new Provenance("modelo-x", "1.0", LocalDate.of(2026, 1, 2));

  @TempDir Path dir;

  private static ImageMetadata sourceWithXmp() {
    return new ImageMetadata(
        Optional.empty(),
        Optional.of(Fixtures.xmpPacket().getBytes(StandardCharsets.UTF_8)),
        Optional.empty(),
        Optional.empty(),
        Map.of(),
        false);
  }

  private byte[] export(
      FormatId format, MetadataPolicy policy, ImageMetadata source, Optional<Provenance> mark)
      throws Exception {
    int[] px = new int[8 * 8];
    Arrays.fill(px, 0xFF336699);
    Path file =
        new Exporter()
            .export(
                Img.adopt(8, 8, px),
                source,
                ExportSettings.defaults(format).withMetadata(policy),
                mark,
                Exporter.Request.ask(dir, "salida-" + policy + "." + format),
                TileExecutor.sequential(),
                CancellationToken.none(),
                Progress.NONE)
            .file();
    return Files.readAllBytes(file);
  }

  private static String raw(byte[] b) {
    return new String(b, StandardCharsets.ISO_8859_1);
  }

  @ParameterizedTest
  @EnumSource(
      value = FormatId.class,
      names = {"JPEG", "PNG", "TIFF"})
  void removeAllLeavesNoTraceOfTheMark(FormatId format) throws Exception {
    String out = raw(export(format, MetadataPolicy.REMOVE_ALL, sourceWithXmp(), Optional.of(P)));
    assertThat(out)
        .doesNotContain(ProvenanceXmp.SOURCE_TYPE)
        .doesNotContain("modelo-x")
        .doesNotContain("rora:Model")
        .doesNotContain("GPSLatitude");
  }

  @ParameterizedTest
  @EnumSource(
      value = FormatId.class,
      names = {"JPEG", "PNG", "TIFF"})
  void removeLocationWritesTheMarkAndDropsTheSourceXmp(FormatId format) throws Exception {
    String out =
        raw(export(format, MetadataPolicy.REMOVE_LOCATION, sourceWithXmp(), Optional.of(P)));
    assertThat(out).contains(ProvenanceXmp.SOURCE_TYPE).doesNotContain("GPSLatitude");
  }

  @ParameterizedTest
  @EnumSource(
      value = FormatId.class,
      names = {"JPEG", "PNG", "TIFF"})
  void keepAddsTheMarkToTheSourceXmp(FormatId format) throws Exception {
    String out = raw(export(format, MetadataPolicy.KEEP, sourceWithXmp(), Optional.of(P)));
    assertThat(out).contains(ProvenanceXmp.SOURCE_TYPE).contains("GPSLatitude");
  }

  @ParameterizedTest
  @EnumSource(
      value = FormatId.class,
      names = {"JPEG", "PNG", "TIFF"})
  void withoutAMarkNothingIsAdded(FormatId format) throws Exception {
    String out =
        raw(export(format, MetadataPolicy.REMOVE_LOCATION, ImageMetadata.EMPTY, Optional.empty()));
    assertThat(out).doesNotContain(ProvenanceXmp.SOURCE_TYPE);
  }

  @Test
  void theGeneratedPacketsAreWellFormedXml() {
    assertThat(XmpValidator.isValid(ProvenanceXmp.packet(P))).isTrue();
    ImageMetadata merged = ProvenanceXmp.apply(sourceWithXmp(), MetadataPolicy.KEEP, P);
    assertThat(XmpValidator.isValid(merged.xmp().orElseThrow())).isTrue();
  }

  @Test
  void tiffCarriesTheMarkInTag700() throws Exception {
    byte[] file =
        export(FormatId.TIFF, MetadataPolicy.REMOVE_LOCATION, ImageMetadata.EMPTY, Optional.of(P));
    assertThat(tag700(file)).isEqualTo(ProvenanceXmp.packet(P));
  }

  @Test
  void anUnmarkedTiffStillHasNoXmpTag() throws Exception {
    byte[] file = export(FormatId.TIFF, MetadataPolicy.KEEP, sourceWithXmp(), Optional.empty());
    assertThat(tag700(file)).isNull();
  }

  /** Lee la etiqueta directamente del directorio del TIFF, sin depender de ningún lector. */
  private static byte[] tag700(byte[] file) {
    ByteBuffer b =
        ByteBuffer.wrap(file)
            .order(file[0] == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
    int ifd = b.getInt(4);
    int entries = b.getShort(ifd) & 0xFFFF;
    for (int i = 0; i < entries; i++) {
      int at = ifd + 2 + i * 12;
      if ((b.getShort(at) & 0xFFFF) == 700) {
        int count = b.getInt(at + 4);
        int offset = b.getInt(at + 8);
        return Arrays.copyOfRange(file, offset, offset + count);
      }
    }
    return null;
  }
}
