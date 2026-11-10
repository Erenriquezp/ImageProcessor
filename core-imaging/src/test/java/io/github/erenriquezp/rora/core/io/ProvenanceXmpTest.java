package io.github.erenriquezp.rora.core.io;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.io.ExportSettings.MetadataPolicy;
import io.github.erenriquezp.rora.core.io.ProvenanceXmp.Provenance;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProvenanceXmpTest {

  private static final Provenance P = new Provenance("modelo-x", "1.0", LocalDate.of(2026, 1, 2));

  private static String text(byte[] b) {
    return new String(b, StandardCharsets.UTF_8);
  }

  private static ImageMetadata withXmp(String xmp) {
    return new ImageMetadata(
        Optional.empty(),
        Optional.of(xmp.getBytes(StandardCharsets.UTF_8)),
        Optional.empty(),
        Optional.empty(),
        Map.of(),
        false);
  }

  private static final String EXISTING =
      "<x:xmpmeta><rdf:RDF><rdf:Description dc:title='a'/></rdf:RDF></x:xmpmeta>";

  @Test
  void thePacketDeclaresOnlyTheTermTheModelAndTheDate() {
    String xml = text(ProvenanceXmp.packet(P));
    assertThat(xml)
        .contains(ProvenanceXmp.SOURCE_TYPE)
        .contains("rora:Model=\"modelo-x\"")
        .contains("rora:ModelVersion=\"1.0\"")
        .contains("rora:Date=\"2026-01-02\"")
        .doesNotContain("T00:");
    assertThat(ProvenanceXmp.isMarked(ProvenanceXmp.packet(P))).isTrue();
  }

  @Test
  void valuesAreEscaped() {
    String xml = text(ProvenanceXmp.packet(new Provenance("a\"<&>", "", P.date())));
    assertThat(xml).contains("a&quot;&lt;&amp;&gt;").doesNotContain("a\"<");
  }

  @Test
  void removeAllWritesNothing() {
    assertThat(ProvenanceXmp.apply(ImageMetadata.EMPTY, MetadataPolicy.REMOVE_ALL, P))
        .isSameAs(ImageMetadata.EMPTY);
  }

  @Test
  void withoutXmpAFreshPacketIsCreated() {
    ImageMetadata out = ProvenanceXmp.apply(ImageMetadata.EMPTY, MetadataPolicy.REMOVE_LOCATION, P);
    assertThat(text(out.xmp().orElseThrow())).contains(ProvenanceXmp.SOURCE_TYPE);
  }

  @Test
  void keepAddsTheMarkInsideTheExistingXmp() {
    String out =
        text(ProvenanceXmp.apply(withXmp(EXISTING), MetadataPolicy.KEEP, P).xmp().orElseThrow());
    assertThat(out).contains("dc:title='a'").contains(ProvenanceXmp.SOURCE_TYPE);
    assertThat(out.indexOf(ProvenanceXmp.SOURCE_TYPE)).isLessThan(out.indexOf("</rdf:RDF>"));
  }

  @Test
  void anXmpThatCannotBeExtendedIsKeptIntact() {
    for (String xmp :
        new String[] {
          "<sin-rdf/>",
          "<rdf:RDF><d Iptc4xmpExt:DigitalSourceType='digitalCapture'/></rdf:RDF>",
          "<rdf:RDF>" + "x".repeat(70_000) + "</rdf:RDF>"
        }) {
      assertThat(
              text(ProvenanceXmp.apply(withXmp(xmp), MetadataPolicy.KEEP, P).xmp().orElseThrow()))
          .isEqualTo(xmp);
    }
  }

  @Test
  void aForeignXmpIsNotConsideredMarked() {
    assertThat(ProvenanceXmp.isMarked(EXISTING.getBytes(StandardCharsets.UTF_8))).isFalse();
  }
}
