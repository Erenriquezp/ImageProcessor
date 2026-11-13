package io.github.erenriquezp.rora.wallpapers.plan;

import io.github.erenriquezp.rora.core.io.FileNames;
import io.github.erenriquezp.rora.core.io.FormatId;
import io.github.erenriquezp.rora.core.io.NameTemplate;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Nombres y carpetas de la tanda. Añade {@code {dispositivo}} y {@code {grupo}} a los tokens de la
 * fase 4 ({@code {nombre}}, {@code {fecha}}, {@code {hora}}, {@code {n}}, {@code {ancho}}, {@code
 * {alto}}, {@code {formato}}) y delega en {@link NameTemplate}.
 */
public final class TargetNames {

  /** Plantilla de archivo por defecto. */
  public static final String DEFAULT_FILE = "{nombre}_{dispositivo}_{ancho}x{alto}";

  /** Plantilla de carpeta por defecto (A4): una subcarpeta por grupo. */
  public static final String DEFAULT_FOLDER = "{nombre} - wallpapers/{grupo}";

  private TargetNames() {}

  /** Datos comunes de la tanda. */
  public record Batch(String photoName, LocalDateTime now) {}

  /** Nombre de archivo saneado y con extensión. */
  public static String fileName(
      String template, Batch batch, int number, PlannedTarget t, String groupName, FormatId f) {
    String expanded =
        template.replace("{dispositivo}", t.target().name()).replace("{grupo}", groupName);
    return NameTemplate.fileName(
        expanded,
        new NameTemplate.Values(
            batch.photoName(), batch.now(), number, t.target().width(), t.target().height(), f));
  }

  /**
   * Carpeta del destino dentro de {@code root}: cada segmento de la plantilla (separados por {@code
   * /}) se expande y se sanea por separado, así que nunca sale de {@code root}.
   */
  public static Path folder(Path root, String template, Batch batch, String groupName) {
    Path p = root;
    for (String seg : template.split("[/\\\\]")) {
      if (seg.isBlank()) {
        continue;
      }
      String expanded =
          NameTemplate.expand(
              seg.replace("{grupo}", groupName).replace("{dispositivo}", ""),
              new NameTemplate.Values(batch.photoName(), batch.now(), 1, 0, 0, FormatId.JPEG));
      p = p.resolve(FileNames.sanitize(expanded));
    }
    return p;
  }

  /**
   * Hace únicos los nombres de una misma carpeta (sin distinguir mayúsculas, como Windows): el
   * segundo {@code a.jpg} pasa a {@code a (2).jpg}.
   */
  public static List<String> unique(List<String> names) {
    Set<String> taken = new HashSet<>();
    List<String> out = new ArrayList<>();
    for (String n : names) {
      String candidate = n;
      String base = FileNames.baseName(n);
      String ext = n.substring(base.length());
      for (int k = 2; !taken.add(candidate.toLowerCase(Locale.ROOT)); k++) {
        candidate = base + " (" + k + ")" + ext;
      }
      out.add(candidate);
    }
    return out;
  }
}
