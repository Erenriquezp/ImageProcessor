package io.github.erenriquezp.rora.app.viewmodel;

import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.session.ExportTarget.Collision;
import io.github.erenriquezp.rora.app.session.Scheduler;
import io.github.erenriquezp.rora.app.session.StudioIo;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy.Size;
import io.github.erenriquezp.rora.core.io.ExportSettings;
import io.github.erenriquezp.rora.core.io.ExportSettings.MetadataPolicy;
import io.github.erenriquezp.rora.core.io.ExportSettings.ProfilePolicy;
import io.github.erenriquezp.rora.core.io.ExportSettings.Subsampling;
import io.github.erenriquezp.rora.core.io.ExportSettings.TiffCompression;
import io.github.erenriquezp.rora.core.io.FileNames;
import io.github.erenriquezp.rora.core.io.FormatId;
import io.github.erenriquezp.rora.core.io.ImagingException;
import io.github.erenriquezp.rora.core.io.NameTemplate;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/** Estado del diálogo de exportación: ajustes, nombre final y peso estimado. */
public final class ExportDialogViewModel<F> {

  /** Combinaciones habituales. */
  public enum Preset {
    WEB,
    MAX_QUALITY,
    ORIGINAL
  }

  /** Lo que el diálogo necesita de la sesión. */
  interface Host {
    Size outputSize();

    Optional<Img> previewPixels();

    String sourceBaseName();

    void export(ExportSettings settings, Path folder, String fileName, Collision collision);
  }

  private static final long ESTIMATE_DELAY_NANOS = 150_000_000L;
  private static final long KB = 1024;
  private static final long MB = KB * KB;

  private final Env<F> env;
  private final Messages m;
  private final Host host;
  private Supplier<LocalDateTime> clock = LocalDateTime::now;
  private ExportPrefs prefs = ExportPrefs.NONE;

  private final BooleanProperty visible = new SimpleBooleanProperty(false);
  private final ObjectProperty<FormatId> format = new SimpleObjectProperty<>(FormatId.JPEG);
  private final IntegerProperty quality = new SimpleIntegerProperty(90);
  private final BooleanProperty progressive = new SimpleBooleanProperty(false);
  private final ObjectProperty<Subsampling> subsampling =
      new SimpleObjectProperty<>(Subsampling.S420);
  private final BooleanProperty huffman = new SimpleBooleanProperty(true);
  private final IntegerProperty deflate = new SimpleIntegerProperty(6);
  private final ObjectProperty<TiffCompression> tiff =
      new SimpleObjectProperty<>(TiffCompression.DEFLATE);
  private final IntegerProperty background = new SimpleIntegerProperty(0xFFFFFF);
  private final BooleanProperty limitSide = new SimpleBooleanProperty(false);
  private final IntegerProperty side = new SimpleIntegerProperty(2048);
  private final BooleanProperty limitWeight = new SimpleBooleanProperty(false);
  private final IntegerProperty weightKb = new SimpleIntegerProperty(500);
  private final ObjectProperty<MetadataPolicy> metadata =
      new SimpleObjectProperty<>(MetadataPolicy.REMOVE_LOCATION);
  private final ObjectProperty<ProfilePolicy> profile =
      new SimpleObjectProperty<>(ProfilePolicy.EMBED_SRGB);
  private final StringProperty folder = new SimpleStringProperty("");
  private final StringProperty template = new SimpleStringProperty("{nombre}_editada");
  private final ObjectProperty<Collision> collision = new SimpleObjectProperty<>(Collision.ASK);

  private final StringProperty previewName = new SimpleStringProperty("");
  private final StringProperty estimate = new SimpleStringProperty("");
  private final StringProperty error = new SimpleStringProperty("");
  private final BooleanProperty canExport = new SimpleBooleanProperty(false);

  private boolean applying;
  private long estimateGeneration;
  private CancellationToken estimateToken = CancellationToken.create();
  private Scheduler.Cancellable estimateTimer;

  ExportDialogViewModel(Env<F> env, Host host) {
    this.env = env;
    this.m = env.messages();
    this.host = host;
    Observable[] inputs = {
      format,
      quality,
      progressive,
      subsampling,
      huffman,
      deflate,
      tiff,
      background,
      limitSide,
      side,
      limitWeight,
      weightKb,
      metadata,
      profile,
      folder,
      template,
      collision
    };
    for (Observable o : inputs) {
      o.addListener(x -> changed());
    }
  }

  /** Solo para pruebas y capturas deterministas. */
  void setClock(Supplier<LocalDateTime> value) {
    clock = value;
  }

  /** Recupera los ajustes guardados. */
  public void usePrefs(ExportPrefs store) {
    prefs = store;
    applying = true;
    format.set(parse(FormatId.class, store.get("export.format", ""), FormatId.JPEG));
    if (!format.get().writable()) {
      format.set(FormatId.JPEG);
    }
    quality.set(parseInt(store.get("export.quality", ""), 90, 1, 100));
    progressive.set(Boolean.parseBoolean(store.get("export.progressive", "false")));
    subsampling.set(
        parse(Subsampling.class, store.get("export.subsampling", ""), Subsampling.S420));
    huffman.set(Boolean.parseBoolean(store.get("export.huffman", "true")));
    deflate.set(parseInt(store.get("export.deflate", ""), 6, 0, 9));
    tiff.set(parse(TiffCompression.class, store.get("export.tiff", ""), TiffCompression.DEFLATE));
    background.set(parseInt(store.get("export.background", ""), 0xFFFFFF, 0, 0xFFFFFF));
    limitSide.set(Boolean.parseBoolean(store.get("export.limitSide", "false")));
    side.set(parseInt(store.get("export.side", ""), 2048, 1, 16384));
    limitWeight.set(Boolean.parseBoolean(store.get("export.limitWeight", "false")));
    weightKb.set(parseInt(store.get("export.weight", ""), 500, 1, Integer.MAX_VALUE));
    metadata.set(
        parse(
            MetadataPolicy.class,
            store.get("export.metadata", ""),
            MetadataPolicy.REMOVE_LOCATION));
    profile.set(
        parse(ProfilePolicy.class, store.get("export.profile", ""), ProfilePolicy.EMBED_SRGB));
    folder.set(store.get("export.folder", ""));
    template.set(store.get("export.template", "{nombre}_editada"));
    collision.set(parse(Collision.class, store.get("export.collision", ""), Collision.ASK));
    applying = false;
  }

  private static <E extends Enum<E>> E parse(Class<E> type, String text, E fallback) {
    try {
      return Enum.valueOf(type, text);
    } catch (IllegalArgumentException e) {
      return fallback;
    }
  }

  private static int parseInt(String text, int fallback, int min, int max) {
    try {
      return Math.max(min, Math.min(max, Integer.parseInt(text)));
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private void savePrefs() {
    prefs.put("export.format", format.get().name());
    prefs.put("export.quality", Integer.toString(quality.get()));
    prefs.put("export.progressive", Boolean.toString(progressive.get()));
    prefs.put("export.subsampling", subsampling.get().name());
    prefs.put("export.huffman", Boolean.toString(huffman.get()));
    prefs.put("export.deflate", Integer.toString(deflate.get()));
    prefs.put("export.tiff", tiff.get().name());
    prefs.put("export.background", Integer.toString(background.get()));
    prefs.put("export.limitSide", Boolean.toString(limitSide.get()));
    prefs.put("export.side", Integer.toString(side.get()));
    prefs.put("export.limitWeight", Boolean.toString(limitWeight.get()));
    prefs.put("export.weight", Integer.toString(weightKb.get()));
    prefs.put("export.metadata", metadata.get().name());
    prefs.put("export.profile", profile.get().name());
    prefs.put("export.folder", folder.get());
    prefs.put("export.template", template.get());
    prefs.put("export.collision", collision.get().name());
  }

  /** Abre el diálogo, con {@code defaultFolder} si aún no hay carpeta elegida. */
  public void show(String defaultFolder) {
    if (folder.get().isBlank()) {
      folder.set(defaultFolder);
    }
    visible.set(true);
    changed();
  }

  public void close() {
    visible.set(false);
    cancelEstimate();
  }

  public void applyPreset(Preset preset) {
    applying = true;
    switch (preset) {
      case WEB -> {
        format.set(FormatId.JPEG);
        quality.set(80);
        progressive.set(true);
        subsampling.set(Subsampling.S420);
        huffman.set(true);
        limitSide.set(true);
        side.set(2048);
        limitWeight.set(false);
        metadata.set(MetadataPolicy.REMOVE_ALL);
        profile.set(ProfilePolicy.UNTAGGED);
      }
      case MAX_QUALITY -> {
        format.set(FormatId.JPEG);
        quality.set(100);
        progressive.set(false);
        subsampling.set(Subsampling.S444);
        huffman.set(true);
        limitSide.set(false);
        limitWeight.set(false);
        metadata.set(MetadataPolicy.REMOVE_LOCATION);
        profile.set(ProfilePolicy.EMBED_SRGB);
      }
      case ORIGINAL -> {
        format.set(FormatId.PNG);
        deflate.set(6);
        limitSide.set(false);
        limitWeight.set(false);
        metadata.set(MetadataPolicy.REMOVE_LOCATION);
        profile.set(ProfilePolicy.EMBED_SRGB);
      }
    }
    applying = false;
    changed();
  }

  /** Ajustes actuales; lanza {@code IllegalArgumentException} si no son válidos. */
  ExportSettings settings() {
    ExportSettings s =
        ExportSettings.defaults(format.get())
            .withJpeg(progressive.get(), subsampling.get(), huffman.get())
            .withDeflateLevel(deflate.get())
            .withTiff(tiff.get())
            .withProfile(profile.get())
            .withMetadata(metadata.get())
            .withBackground(background.get());
    if (format.get() == FormatId.JPEG) {
      s = s.withQuality(quality.get());
    }
    if (limitSide.get()) {
      s = s.withMaxLongSide(side.get());
    }
    if (limitWeight.get() && format.get() == FormatId.JPEG) {
      s = s.withMaxBytes(weightKb.get() * KB);
    }
    return s;
  }

  /** Tamaño final: reduce al límite de lado, sin ampliar nunca. */
  Size finalSize() {
    Size out = host.outputSize();
    int longest = Math.max(out.width(), out.height());
    if (!limitSide.get() || side.get() <= 0 || longest <= side.get()) {
      return out;
    }
    double k = (double) side.get() / longest;
    return new Size(
        (int) Math.max(1, Math.round(out.width() * k)),
        (int) Math.max(1, Math.round(out.height() * k)));
  }

  private void changed() {
    if (applying || !visible.get()) {
      return;
    }
    String problem = validate();
    error.set(problem);
    canExport.set(problem.isEmpty());
    if (problem.isEmpty()) {
      Size size = finalSize();
      previewName.set(
          NameTemplate.fileName(template.get(), values(size))
              + "  ("
              + m.format("ui.status.size", size.width(), size.height())
              + ")");
      scheduleEstimate();
    } else {
      previewName.set("");
      estimate.set("");
      cancelEstimate();
    }
  }

  private NameTemplate.Values values(Size size) {
    return new NameTemplate.Values(
        FileNames.baseName(host.sourceBaseName()),
        clock.get(),
        1,
        size.width(),
        size.height(),
        format.get());
  }

  private String validate() {
    if (template.get().isBlank()) {
      return m.get("ui.export.error.template");
    }
    if (folder.get().isBlank()) {
      return m.get("ui.export.error.folder");
    }
    try {
      Path.of(folder.get());
      settings();
    } catch (InvalidPathException e) {
      return m.get("ui.export.error.folder");
    } catch (IllegalArgumentException e) {
      return m.get("ui.export.error.values");
    }
    return "";
  }

  private void cancelEstimate() {
    estimateGeneration++;
    estimateToken.cancel();
    if (estimateTimer != null) {
      estimateTimer.cancel();
      estimateTimer = null;
    }
  }

  private void scheduleEstimate() {
    cancelEstimate();
    Optional<Img> preview = host.previewPixels();
    if (preview.isEmpty()) {
      estimate.set("");
      return;
    }
    long g = estimateGeneration;
    CancellationToken token = CancellationToken.create();
    estimateToken = token;
    ExportSettings s = settings();
    Size size = finalSize();
    estimate.set(m.get("ui.export.estimating"));
    estimateTimer =
        env.scheduler()
            .schedule(
                () -> env.background().execute(() -> runEstimate(preview.get(), s, size, token, g)),
                ESTIMATE_DELAY_NANOS);
  }

  private void runEstimate(
      Img preview, ExportSettings s, Size size, CancellationToken token, long g) {
    try {
      long bytes = StudioIo.estimate(preview, s, size.width(), size.height(), token);
      long shown = s.maxBytes() > 0 ? Math.min(bytes, s.maxBytes()) : bytes;
      env.publisher()
          .execute(
              () -> {
                if (g == estimateGeneration) {
                  estimate.set(m.format("ui.export.estimate", humanSize(shown)));
                }
              });
    } catch (ImagingException e) {
      env.publisher()
          .execute(
              () -> {
                if (g == estimateGeneration) {
                  estimate.set("");
                }
              });
    }
  }

  String humanSize(long bytes) {
    Locale l = m.locale();
    if (bytes >= MB) {
      return String.format(l, "%.1f MB", (double) bytes / MB);
    }
    return String.format(l, "%d KB", Math.max(1, bytes / KB));
  }

  /** Lanza la exportación con los ajustes actuales. */
  public void accept() {
    changed();
    if (!canExport.get()) {
      return;
    }
    ExportSettings s = settings();
    Path dir = Path.of(folder.get());
    String name = NameTemplate.fileName(template.get(), values(finalSize()));
    Collision c = collision.get();
    savePrefs();
    close();
    host.export(s, dir, name, c);
  }

  public BooleanProperty visibleProperty() {
    return visible;
  }

  public ObjectProperty<FormatId> formatProperty() {
    return format;
  }

  public IntegerProperty qualityProperty() {
    return quality;
  }

  public BooleanProperty progressiveProperty() {
    return progressive;
  }

  public ObjectProperty<Subsampling> subsamplingProperty() {
    return subsampling;
  }

  public BooleanProperty huffmanProperty() {
    return huffman;
  }

  public IntegerProperty deflateProperty() {
    return deflate;
  }

  public ObjectProperty<TiffCompression> tiffProperty() {
    return tiff;
  }

  public IntegerProperty backgroundProperty() {
    return background;
  }

  public BooleanProperty limitSideProperty() {
    return limitSide;
  }

  public IntegerProperty sideProperty() {
    return side;
  }

  public BooleanProperty limitWeightProperty() {
    return limitWeight;
  }

  public IntegerProperty weightKbProperty() {
    return weightKb;
  }

  public ObjectProperty<MetadataPolicy> metadataProperty() {
    return metadata;
  }

  public ObjectProperty<ProfilePolicy> profileProperty() {
    return profile;
  }

  public StringProperty folderProperty() {
    return folder;
  }

  public StringProperty templateProperty() {
    return template;
  }

  public ObjectProperty<Collision> collisionProperty() {
    return collision;
  }

  public StringProperty previewNameProperty() {
    return previewName;
  }

  public StringProperty estimateProperty() {
    return estimate;
  }

  public StringProperty errorProperty() {
    return error;
  }

  public BooleanProperty canExportProperty() {
    return canExport;
  }
}
