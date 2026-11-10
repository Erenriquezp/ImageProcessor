package io.github.erenriquezp.rora.core.io;

import io.github.erenriquezp.rora.core.io.ExportSettings.MetadataPolicy;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Marca XMP que indica que una imagen contiene píxeles generados por un modelo. Solo lleva el
 * término IPTC, el modelo y la fecha, sin hora: nada de la persona ni del equipo.
 */
public final class ProvenanceXmp {

  /** Lo que se declara sobre la imagen. */
  public record Provenance(String model, String version, LocalDate date) {
    public Provenance {
      Objects.requireNonNull(model, "model");
      Objects.requireNonNull(version, "version");
      Objects.requireNonNull(date, "date");
    }
  }

  /** Término IPTC para una mezcla de contenido real y contenido generado por un modelo. */
  public static final String SOURCE_TYPE =
      "http://cv.iptc.org/newscodes/digitalsourcetype/compositeWithTrainedAlgorithmicMedia";

  private static final String RORA_NS = "https://erenriquezp.github.io/rora/ns/provenance/1.0/";
  private static final String PROPERTY = "Iptc4xmpExt:DigitalSourceType";
  private static final String RDF_END = "</rdf:RDF>";

  /** Por encima de este tamaño un JPEG ya no admite el XMP en un solo segmento. */
  private static final int MAX_MERGED_BYTES = 65_000;

  private ProvenanceXmp() {}

  /** Paquete XMP completo, en UTF-8. */
  public static byte[] packet(Provenance p) {
    String xml =
        "<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n"
            + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n"
            + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n"
            + description(p)
            + "\n</rdf:RDF>\n</x:xmpmeta>\n<?xpacket end=\"r\"?>";
    return xml.getBytes(StandardCharsets.UTF_8);
  }

  private static String description(Provenance p) {
    return "<rdf:Description rdf:about=\"\""
        + " xmlns:Iptc4xmpExt=\"http://iptc.org/std/Iptc4xmpExt/2008-02-29/\""
        + " xmlns:rora=\""
        + RORA_NS
        + "\" "
        + PROPERTY
        + "=\""
        + SOURCE_TYPE
        + "\" rora:Model=\""
        + escape(p.model())
        + "\" rora:ModelVersion=\""
        + escape(p.version())
        + "\" rora:Date=\""
        + p.date()
        + "\"/>";
  }

  private static String escape(String s) {
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  /** ¿Lleva este XMP la marca de procedencia de esta aplicación? */
  public static boolean isMarked(byte[] xmp) {
    return new String(xmp, StandardCharsets.UTF_8).contains(RORA_NS);
  }

  /**
   * Añade la marca a los metadatos de salida, ya filtrados por la política.
   *
   * <p>Con {@code KEEP}, un XMP existente se conserva siempre: si no se puede ampliar sin riesgo
   * (otra codificación, demasiado grande o ya declara su origen), se deja como está y sin marca.
   */
  public static ImageMetadata apply(ImageMetadata out, MetadataPolicy policy, Provenance p) {
    if (policy == MetadataPolicy.REMOVE_ALL) {
      return out;
    }
    byte[] xmp = out.xmp().isPresent() ? merge(out.xmp().get(), p) : packet(p);
    return new ImageMetadata(
        out.exif(), Optional.of(xmp), out.iptc(), out.icc(), out.text(), out.damaged());
  }

  private static byte[] merge(byte[] existing, Provenance p) {
    String xml = new String(existing, StandardCharsets.UTF_8);
    int end = xml.lastIndexOf(RDF_END);
    if (end < 0 || xml.contains(PROPERTY)) {
      return existing;
    }
    byte[] merged =
        (xml.substring(0, end) + description(p) + "\n" + xml.substring(end))
            .getBytes(StandardCharsets.UTF_8);
    return merged.length > MAX_MERGED_BYTES ? existing : merged;
  }
}
