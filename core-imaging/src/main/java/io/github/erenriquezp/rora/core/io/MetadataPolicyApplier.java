package io.github.erenriquezp.rora.core.io;

import io.github.erenriquezp.rora.core.io.ExportSettings.MetadataPolicy;
import io.github.erenriquezp.rora.core.io.ImageMetadata.ExifData;
import io.github.erenriquezp.rora.core.io.ImageMetadata.ExifTag;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Construye los metadatos de salida desde cero: lo que no se añade aquí expresamente no llega al
 * archivo. El perfil de entrada no se copia nunca porque la salida siempre es sRGB.
 */
public final class MetadataPolicyApplier {

  private static final Set<String> KEPT_TEXT = Set.of("Title", "Author", "Copyright");

  private MetadataPolicyApplier() {}

  public static ImageMetadata apply(ImageMetadata source, MetadataPolicy policy) {
    return switch (policy) {
      case REMOVE_ALL -> ImageMetadata.EMPTY;
      case KEEP -> keep(source);
      case REMOVE_LOCATION -> removeLocation(source);
    };
  }

  private static ImageMetadata keep(ImageMetadata s) {
    Map<String, String> text = new HashMap<>();
    s.text()
        .forEach(
            (k, v) -> {
              if (KEPT_TEXT.contains(k)) {
                text.put(k, v);
              }
            });
    return new ImageMetadata(
        s.exif().filter(e -> !e.isEmpty()), s.xmp(), s.iptc(), Optional.empty(), text, false);
  }

  private static ImageMetadata removeLocation(ImageMetadata s) {
    Optional<ExifData> exif =
        s.exif()
            .map(e -> new ExifData(1, open(e.ifd0()), open(e.exif()), List.of()))
            .filter(e -> !e.isEmpty());
    return new ImageMetadata(
        exif, Optional.empty(), Optional.empty(), Optional.empty(), Map.of(), false);
  }

  private static List<ExifTag> open(List<ExifTag> tags) {
    return tags.stream().filter(t -> !t.sensitive()).toList();
  }
}
