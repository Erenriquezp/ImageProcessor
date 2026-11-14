package io.github.erenriquezp.rora.app.session.wallpaper;

import io.github.erenriquezp.rora.app.session.ExportTarget;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ExportSettings;
import io.github.erenriquezp.rora.core.io.ExportSettings.MetadataPolicy;
import io.github.erenriquezp.rora.core.io.FormatId;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import io.github.erenriquezp.rora.core.io.ImagingException;
import io.github.erenriquezp.rora.core.io.ProvenanceXmp;
import io.github.erenriquezp.rora.imaging.io.Exporter;
import io.github.erenriquezp.rora.wallpapers.plan.OutputFormat;
import io.github.erenriquezp.rora.wallpapers.plan.PlannedTarget;
import io.github.erenriquezp.rora.wallpapers.plan.Target;
import io.github.erenriquezp.rora.wallpapers.plan.TargetNames;
import io.github.erenriquezp.rora.wallpapers.plan.TargetSink;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperJob;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperPlan;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperResult;
import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Exportación multi-destino de Wallpapers sobre el exportador atómico de la fase 4. Se ejecuta
 * fuera del hilo FX; un destino a la vez.
 */
public final class WallpaperExport {

  /** Calidad JPEG por defecto: q95 con 4:4:4 (Puerta 0, banding). */
  public static final int JPEG_QUALITY = 95;

  private WallpaperExport() {}

  /**
   * Ajustes de la tanda.
   *
   * @param collision {@code NUMBER} u {@code OVERWRITE}; la pregunta se hace antes, una sola vez
   * @param background color sobre el que se aplana el alfa
   */
  public record Settings(
      Path root,
      String folderTemplate,
      String fileTemplate,
      OutputFormat format,
      int jpegQuality,
      MetadataPolicy metadata,
      ExportTarget.Collision collision,
      int background) {

    public Settings {
      Objects.requireNonNull(root, "root");
      Objects.requireNonNull(folderTemplate, "folderTemplate");
      Objects.requireNonNull(fileTemplate, "fileTemplate");
      Objects.requireNonNull(format, "format");
      Objects.requireNonNull(metadata, "metadata");
      Objects.requireNonNull(collision, "collision");
      if (collision == ExportTarget.Collision.ASK) {
        throw new IllegalArgumentException("la tanda no pregunta archivo a archivo");
      }
      if (jpegQuality < 1 || jpegQuality > 100) {
        throw new IllegalArgumentException("calidad JPEG fuera de 1..100");
      }
    }

    /** Valores por defecto: plantillas de la fase, formato Auto y sin metadatos. */
    public static Settings defaults(Path root) {
      return new Settings(
          root,
          TargetNames.DEFAULT_FOLDER,
          TargetNames.DEFAULT_FILE,
          OutputFormat.AUTO,
          JPEG_QUALITY,
          MetadataPolicy.REMOVE_ALL,
          ExportTarget.Collision.NUMBER,
          0xFFFFFFFF);
    }

    public Settings withCollision(ExportTarget.Collision c) {
      return new Settings(
          root, folderTemplate, fileTemplate, format, jpegQuality, metadata, c, background);
    }

    public Settings withFormat(OutputFormat f) {
      return new Settings(
          root, folderTemplate, fileTemplate, f, jpegQuality, metadata, collision, background);
    }

    public Settings withMetadata(MetadataPolicy m) {
      return new Settings(
          root, folderTemplate, fileTemplate, format, jpegQuality, m, collision, background);
    }
  }

  /** Dónde y cómo se escribirá un destino. */
  public record Item(PlannedTarget planned, Path folder, String fileName, FormatId format) {
    public Path file() {
      return folder.resolve(fileName);
    }
  }

  /**
   * Nombres y carpetas de toda la tanda, ya únicos dentro de cada carpeta. Barato: sin píxeles ni
   * disco.
   */
  public static List<Item> layout(
      WallpaperPlan plan,
      Settings s,
      TargetNames.Batch batch,
      Function<DevicePreset.Group, String> groupName) {
    List<Path> folders = new ArrayList<>();
    List<String> names = new ArrayList<>();
    List<FormatId> formats = new ArrayList<>();
    int n = 0;
    for (PlannedTarget t : plan.targets()) {
      String g = groupName.apply(t.target().group());
      FormatId f = s.format().resolve(t);
      folders.add(TargetNames.folder(s.root(), s.folderTemplate(), batch, g));
      names.add(TargetNames.fileName(s.fileTemplate(), batch, ++n, t, g, f));
      formats.add(f);
    }
    Map<Path, List<Integer>> byFolder = new LinkedHashMap<>();
    for (int i = 0; i < folders.size(); i++) {
      byFolder.computeIfAbsent(folders.get(i), k -> new ArrayList<>()).add(i);
    }
    String[] unique = new String[names.size()];
    for (List<Integer> idx : byFolder.values()) {
      List<String> local = new ArrayList<>();
      for (int i : idx) {
        local.add(names.get(i));
      }
      List<String> u = TargetNames.unique(local);
      for (int k = 0; k < idx.size(); k++) {
        unique[idx.get(k)] = u.get(k);
      }
    }
    List<Item> out = new ArrayList<>();
    for (int i = 0; i < unique.length; i++) {
      out.add(new Item(plan.targets().get(i), folders.get(i), unique[i], formats.get(i)));
    }
    return out;
  }

  /** Archivos de la tanda que ya existen (para preguntar una sola vez antes de empezar). */
  public static List<Path> existing(List<Item> items) {
    List<Path> out = new ArrayList<>();
    for (Item i : items) {
      if (Files.exists(i.file())) {
        out.add(i.file());
      }
    }
    return out;
  }

  /** Lado mayor de las miniaturas sobre las que se estima el peso. */
  static final int ESTIMATE_SIDE = 768;

  /**
   * Peso aproximado de cada archivo de la tanda, en bytes y en el orden de {@code items}: cada
   * destino se dibuja sobre la vista reducida, se codifica en memoria con sus ajustes y se escala
   * por píxeles. Aproximado (sin tramado y con menos detalle que la foto completa). Hilo de
   * trabajo.
   */
  public static List<Long> estimate(
      Img preview, WallpaperPlan plan, List<Item> items, Settings s, CancellationToken token)
      throws ImagingException {
    List<Img> thumbs = WallpaperWorkspace.thumbnails(preview, plan, ESTIMATE_SIDE);
    List<Long> out = new ArrayList<>();
    for (Item it : items) {
      token.throwIfCancelled();
      int i = plan.targets().indexOf(it.planned());
      Target t = it.planned().target();
      out.add(
          Exporter.estimateBytes(
              thumbs.get(i), exportSettings(it.format(), s), t.width(), t.height(), token));
    }
    return out;
  }

  /** Ajustes del exportador de la fase 4 para un formato de la tanda. */
  static ExportSettings exportSettings(FormatId f, Settings s) {
    ExportSettings e = ExportSettings.defaults(f).withMetadata(s.metadata());
    if (f == FormatId.JPEG) {
      e = e.withQuality(s.jpegQuality()).withJpeg(false, ExportSettings.Subsampling.S444, true);
    }
    return e;
  }

  /**
   * Escribe la tanda. Cada archivo es atómico; un fallo no detiene a los demás; la cancelación deja
   * solo los archivos ya terminados.
   *
   * @param source resultado FULL de la receta (incluidas la geometría y la mejora)
   * @param provenance marca de IA si la receta tiene una mejora lista
   */
  public static WallpaperResult run(
      Img source,
      ImageMetadata metadata,
      Optional<ProvenanceXmp.Provenance> provenance,
      WallpaperPlan plan,
      List<Item> items,
      Settings s,
      TileExecutor executor,
      CancellationToken token,
      WallpaperJob.Listener listener) {
    if (items.size() != plan.targets().size()) {
      throw new IllegalArgumentException("la disposición no corresponde al plan");
    }
    Map<PlannedTarget, Item> byTarget = new IdentityHashMap<>();
    for (Item i : items) {
      byTarget.put(i.planned(), i);
    }
    Exporter exporter = new Exporter();
    TargetSink sink =
        (t, image, format, tk) -> {
          Item item = byTarget.get(t);
          try {
            Files.createDirectories(item.folder());
          } catch (IOException e) {
            throw new IOException("no se pudo crear la carpeta " + item.folder().getFileName(), e);
          }
          Exporter.Result r =
              exporter.export(
                  image,
                  metadata,
                  exportSettings(format, s),
                  provenance,
                  new Exporter.Request(
                      item.folder(),
                      item.fileName(),
                      Exporter.Collision.valueOf(s.collision().name()),
                      s.collision() == ExportTarget.Collision.OVERWRITE,
                      false,
                      Optional.empty()),
                  TileExecutor.sequential(),
                  tk,
                  Progress.NONE);
          return new TargetSink.Written(r.file(), r.bytes());
        };
    return WallpaperJob.run(
        source, plan, s.format(), s.background(), sink, executor, token, listener);
  }
}
