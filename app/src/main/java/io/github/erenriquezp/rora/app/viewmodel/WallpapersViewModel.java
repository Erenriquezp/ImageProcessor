package io.github.erenriquezp.rora.app.viewmodel;

import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.model.CropModel;
import io.github.erenriquezp.rora.app.model.FrameOverlayState;
import io.github.erenriquezp.rora.app.session.ExportTarget;
import io.github.erenriquezp.rora.app.session.wallpaper.WallpaperExport;
import io.github.erenriquezp.rora.app.session.wallpaper.WallpaperWorkspace;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.CancelledException;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy.Size;
import io.github.erenriquezp.rora.core.io.ExportSettings.MetadataPolicy;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import io.github.erenriquezp.rora.core.io.ProvenanceXmp;
import io.github.erenriquezp.rora.wallpapers.geom.FrameRect;
import io.github.erenriquezp.rora.wallpapers.plan.OutputFormat;
import io.github.erenriquezp.rora.wallpapers.plan.PlannedTarget;
import io.github.erenriquezp.rora.wallpapers.plan.PlannedTarget.Warning;
import io.github.erenriquezp.rora.wallpapers.plan.Planner;
import io.github.erenriquezp.rora.wallpapers.plan.QualityBadge;
import io.github.erenriquezp.rora.wallpapers.plan.Target;
import io.github.erenriquezp.rora.wallpapers.plan.Target.FitMode;
import io.github.erenriquezp.rora.wallpapers.plan.TargetNames;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperPlan;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperResult;
import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset;
import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset.Group;
import io.github.erenriquezp.rora.wallpapers.preset.PresetCatalog;
import io.github.erenriquezp.rora.wallpapers.preset.PresetProblem;
import io.github.erenriquezp.rora.wallpapers.preset.SafeZone;
import io.github.erenriquezp.rora.wallpapers.preset.UserPresetStore;
import io.github.erenriquezp.rora.wallpapers.render.FillSpec;
import io.github.erenriquezp.rora.wallpapers.subject.Subject;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Herramienta Wallpapers. Elige dispositivos, encuadra cada uno sobre el visor y exporta todos de
 * una vez. Solo prepara el plan (barato); el análisis, las miniaturas y la exportación van a la
 * capa de sesión. Nunca ejecuta la IA: la sugiere.
 */
public final class WallpapersViewModel<F> implements FrameEditor {

  /** Lo que la herramienta necesita del resto del estudio. */
  public interface Host {
    void prompt(Prompt p);

    /** Píxeles del proxy actual (para las miniaturas). */
    Optional<Img> previewPixels();

    String sourceBaseName();

    ImageMetadata metadata();

    /** Marca de IA si la salida lleva píxeles del modelo. */
    Optional<ProvenanceXmp.Provenance> provenance();

    void reveal(Path file);

    void notice(String message);

    void error(String message, String details);

    LocalDateTime now();
  }

  /** Una fila de la lista: cabecera de grupo ({@code preset == null}) o dispositivo. */
  public record Row(String label, String detail, DevicePreset preset, boolean selected) {
    public boolean header() {
      return preset == null;
    }
  }

  /** Miniatura de un destino en la tira. */
  public record Thumb<F>(
      String id,
      String label,
      F image,
      QualityBadge badge,
      boolean filled,
      boolean warning,
      boolean current) {}

  /** Estado del análisis de rostros. */
  public enum AnalysisState {
    IDLE,
    ANALYZING,
    READY,
    NO_DETECTOR
  }

  /** Pantalla del sistema en píxeles físicos. */
  public record Screen(int width, int height) {}

  /** Informe final de una exportación. */
  public record Report(String summary, List<String> failures, Optional<Path> folder) {}

  static final String KEY_TARGETS = "wallpapers.targets";
  static final String KEY_FIT = "wallpapers.fit";
  static final String KEY_FILL = "wallpapers.fill";
  static final String KEY_STRENGTH = "wallpapers.strength";
  static final String KEY_FORMAT = "wallpapers.format";
  static final String KEY_QUALITY = "wallpapers.quality";
  static final String KEY_FOLDER = "wallpapers.folder";
  static final String KEY_FOLDER_TEMPLATE = "wallpapers.folderTemplate";
  static final String KEY_FILE_TEMPLATE = "wallpapers.fileTemplate";
  static final String KEY_METADATA = "wallpapers.metadata";
  static final String KEY_DETECT = "wallpapers.detect";

  /** Selección inicial (A8), además de las pantallas. */
  static final List<String> DEFAULT_TARGETS =
      List.of("pc-4k", "pc-qhd", "phone-1080-2400", "phone-1179-2556", "tablet-1668-2388");

  static final int THUMB_SIDE = 160;

  private final Env<F> env;
  private final Messages m;
  private final Host host;
  private final EnhanceViewModel<F> enhance;
  private final WallpaperWorkspace workspace;
  private final Function<Img, F> converter;
  private ExportPrefs prefs = ExportPrefs.NONE;
  private final UserPresetStore store;

  private final BooleanProperty active = new SimpleBooleanProperty(false);
  private final StringProperty query = new SimpleStringProperty("");
  private final ObservableList<Row> rows = FXCollections.observableArrayList();
  private final ObservableList<Thumb<F>> thumbs = FXCollections.observableArrayList();
  private final ObjectProperty<FrameOverlayState> overlay = new SimpleObjectProperty<>();
  private final ObjectProperty<WallpaperPlan> plan = new SimpleObjectProperty<>();
  private final StringProperty currentId = new SimpleStringProperty("");
  private final ObjectProperty<FitMode> fit = new SimpleObjectProperty<>(FitMode.AUTO);
  private final ObjectProperty<FillSpec.Kind> fill = new SimpleObjectProperty<>(FillSpec.Kind.BLUR);
  private final DoubleProperty strength = new SimpleDoubleProperty(FillSpec.DEFAULT_STRENGTH);
  private final StringProperty currentTitle = new SimpleStringProperty("");
  private final StringProperty currentInfo = new SimpleStringProperty("");
  private final ObservableList<String> currentWarnings = FXCollections.observableArrayList();
  private final BooleanProperty safeZones = new SimpleBooleanProperty(true);
  private final BooleanProperty detectFaces = new SimpleBooleanProperty(true);
  private final ObjectProperty<AnalysisState> analysis =
      new SimpleObjectProperty<>(AnalysisState.IDLE);
  private final StringProperty suggestion = new SimpleStringProperty("");
  private final StringProperty suggestionAction = new SimpleStringProperty("");
  private final StringProperty exportLabel = new SimpleStringProperty("");
  private final BooleanProperty canExport = new SimpleBooleanProperty(false);
  private final ObservableList<String> catalogProblems = FXCollections.observableArrayList();

  private final BooleanProperty dialogOpen = new SimpleBooleanProperty(false);
  private final StringProperty folder = new SimpleStringProperty("");
  private final StringProperty folderTemplate =
      new SimpleStringProperty(TargetNames.DEFAULT_FOLDER);
  private final StringProperty fileTemplate = new SimpleStringProperty(TargetNames.DEFAULT_FILE);
  private final ObjectProperty<OutputFormat> format = new SimpleObjectProperty<>(OutputFormat.AUTO);
  private final IntegerProperty quality = new SimpleIntegerProperty(WallpaperExport.JPEG_QUALITY);
  private final ObjectProperty<MetadataPolicy> metadata =
      new SimpleObjectProperty<>(MetadataPolicy.REMOVE_ALL);
  private final StringProperty namePreview = new SimpleStringProperty("");
  private final ObservableList<String> estimates = FXCollections.observableArrayList();
  private final StringProperty estimateTotal = new SimpleStringProperty("");

  private final BooleanProperty exporting = new SimpleBooleanProperty(false);
  private final DoubleProperty progress = new SimpleDoubleProperty(-1);
  private final StringProperty progressText = new SimpleStringProperty("");
  private final ObjectProperty<Report> report = new SimpleObjectProperty<>();

  private List<DevicePreset> screens = List.of();
  private List<DevicePreset> userPresets = List.of();
  private PresetCatalog catalog =
      PresetCatalog.of(List.of(), PresetCatalog.builtIns(), List.of(), List.of());

  /** Destinos elegidos, en orden de selección, por id. */
  private final Map<String, Target> targets = new LinkedHashMap<>();

  private WallpaperWorkspace.Analysis lastAnalysis;
  private double lastAspect = Double.NaN;
  private long thumbGeneration;
  private long estimateGeneration;
  private CancellationToken estimateToken = CancellationToken.create();
  private CancellationToken exportToken = CancellationToken.create();
  private boolean restored;

  /** Mientras se copian al panel los valores del destino actual, los cambios no son del usuario. */
  private boolean syncing;

  WallpapersViewModel(
      Env<F> env,
      Host host,
      EnhanceViewModel<F> enhance,
      WallpaperWorkspace workspace,
      Function<Img, F> converter,
      UserPresetStore store) {
    this.env = env;
    this.m = env.messages();
    this.host = host;
    this.enhance = enhance;
    this.workspace = workspace;
    this.converter = converter;
    this.store = store;
    query.addListener((o, a, b) -> rebuildRows());
    fit.addListener((o, a, b) -> updateCurrent(t -> t.withFit(b)));
    fill.addListener(
        (o, a, b) -> updateCurrent(t -> t.withFill(new FillSpec(b, strength.get(), 0xFF000000))));
    strength.addListener(
        (o, a, b) ->
            updateCurrent(
                t ->
                    t.withFill(
                        new FillSpec(t.fill().kind(), b.doubleValue(), t.fill().solidArgb()))));
    safeZones.addListener((o, a, b) -> refreshOverlay());
    detectFaces.addListener(
        (o, a, b) -> {
          prefs.put(KEY_DETECT, Boolean.toString(b));
          if (active.get()) {
            analyze();
          }
        });
    for (StringProperty p : List.of(folderTemplate, fileTemplate)) {
      p.addListener((o, a, b) -> refreshNamePreview());
    }
    // El peso estimado depende del formato y de la calidad JPEG.
    format.addListener((o, a, b) -> refreshEstimateIfOpen());
    quality.addListener((o, a, b) -> refreshEstimateIfOpen());
    restorePrefs();
    updateExportState();
  }

  /** Dónde se recuerdan la selección y los ajustes (lo aporta la ventana). */
  public void usePrefs(ExportPrefs p) {
    prefs = p;
    restorePrefs();
  }

  // ── Ciclo de vida ─────────────────────────────────────────────────────────

  /** Se abre la herramienta: abrirla es la orden de analizar (A1), nunca de mejorar. */
  void activate() {
    if (!env.session().hasImage()) {
      return;
    }
    active.set(true);
    loadUserPresets();
    if (!restored) {
      restored = true;
      restoreTargets();
    }
    analyze();
  }

  void deactivate() {
    active.set(false);
    overlay.set(null);
  }

  /** Otra imagen: se olvidan los encuadres manuales y el análisis. */
  void imageChanged() {
    lastAnalysis = null;
    lastAspect = currentAspect();
    for (Map.Entry<String, Target> e : targets.entrySet()) {
      e.setValue(e.getValue().withManualFrame(Optional.empty()));
    }
    if (active.get()) {
      analyze();
    } else {
      replan();
    }
  }

  private double currentAspect() {
    if (!env.session().hasImage()) {
      return Double.NaN;
    }
    Size s = env.session().outputSize();
    return (double) s.width() / s.height();
  }

  /** La receta cambió: nuevo plan y, si sigue abierta, nuevo análisis (en caché si no cambió). */
  void recipeChanged() {
    Size s = env.session().hasImage() ? env.session().outputSize() : null;
    if (s != null) {
      double aspect = (double) s.width() / s.height();
      if (!Double.isNaN(lastAspect) && Math.abs(aspect / lastAspect - 1) > 1e-6) {
        // A5: la proporción de la fuente cambió; los encuadres manuales ya no significan lo mismo.
        boolean any = false;
        for (Map.Entry<String, Target> e : targets.entrySet()) {
          any |= e.getValue().manualFrame().isPresent();
          e.setValue(e.getValue().withManualFrame(Optional.empty()));
        }
        if (any) {
          host.notice(m.get("ui.wallpapers.warn.manualReset"));
        }
      }
      lastAspect = aspect;
    }
    if (active.get()) {
      analyze();
    } else {
      replan();
    }
  }

  /** Hay un proxy nuevo: las miniaturas se rehacen. */
  void previewChanged() {
    if (active.get()) {
      refreshThumbs();
    }
  }

  private void analyze() {
    if (!env.session().hasImage()) {
      return;
    }
    analysis.set(AnalysisState.ANALYZING);
    replan();
    workspace.analyze(
        env.session(),
        detectFaces.get(),
        env.background(),
        env.publisher(),
        a -> {
          lastAnalysis = a;
          analysis.set(
              a == null || a.detectorFailed() ? AnalysisState.NO_DETECTOR : AnalysisState.READY);
          replan();
        });
  }

  // ── Lista de dispositivos ─────────────────────────────────────────────────

  /** Pantallas del sistema ("Mi pantalla"), en píxeles físicos. No se guardan (A6). */
  public void setScreens(List<Screen> list) {
    List<DevicePreset> out = new ArrayList<>();
    int n = 0;
    for (Screen s : list) {
      n++;
      if (s.width() < DevicePreset.MIN_SIDE
          || s.height() < DevicePreset.MIN_SIDE
          || s.width() > DevicePreset.MAX_SIDE
          || s.height() > DevicePreset.MAX_SIDE) {
        continue;
      }
      out.add(
          new DevicePreset(
              "screen-" + n,
              m.format("ui.wallpapers.screenName", n),
              Group.SCREEN,
              s.width(),
              s.height(),
              DevicePreset.Orientation.of(s.width(), s.height()),
              DevicePreset.Status.STANDARD,
              "",
              Optional.empty(),
              List.of(),
              0,
              DevicePreset.Origin.SCREEN));
    }
    screens = out;
    rebuildCatalog(List.of());
  }

  private void loadUserPresets() {
    env.io()
        .execute(
            () -> {
              UserPresetStore.Loaded l = store.load();
              env.publisher()
                  .execute(
                      () -> {
                        userPresets = l.presets();
                        rebuildCatalog(l.problems());
                      });
            });
  }

  private void rebuildCatalog(List<PresetProblem> problems) {
    catalog = PresetCatalog.of(screens, PresetCatalog.builtIns(), userPresets, problems);
    catalogProblems.setAll(catalog.problems().stream().map(PresetProblem::toString).toList());
    rebuildRows();
  }

  private Map<Group, String> groupNames() {
    Map<Group, String> g = new EnumMap<>(Group.class);
    for (Group x : Group.values()) {
      g.put(x, groupName(x));
    }
    return g;
  }

  String groupName(Group g) {
    return switch (g) {
      case SCREEN -> m.get("ui.wallpapers.group.screen");
      case PC_16_9 -> m.get("ui.wallpapers.group.desktop");
      case PC_16_10 -> m.get("ui.wallpapers.group.desktopTall");
      case ULTRAWIDE -> m.get("ui.wallpapers.group.ultrawide");
      case LAPTOP_3_2 -> m.get("ui.wallpapers.group.laptop");
      case PHONE -> m.get("ui.wallpapers.group.phone");
      case TABLET -> m.get("ui.wallpapers.group.tablet");
      case CUSTOM -> m.get("ui.wallpapers.group.custom");
    };
  }

  private void rebuildRows() {
    List<Row> out = new ArrayList<>();
    Group last = null;
    for (DevicePreset p : catalog.search(query.get(), groupNames())) {
      if (p.group() != last) {
        last = p.group();
        out.add(new Row(groupName(last), "", null, false));
      }
      String detail = m.format("ui.wallpapers.size", p.width(), p.height());
      if (p.status() == DevicePreset.Status.APPROXIMATE) {
        detail = detail + " · " + m.get("ui.wallpapers.approximate");
      }
      out.add(new Row(p.name(), detail, p, isSelected(p.id())));
    }
    rows.setAll(out);
  }

  private boolean isSelected(String presetId) {
    return targets.containsKey(presetId) || targets.containsKey(presetId + "@rot");
  }

  /** Marca o desmarca un dispositivo. */
  public void toggle(DevicePreset p) {
    if (isSelected(p.id())) {
      targets.remove(p.id());
      targets.remove(p.id() + "@rot");
      if (currentId.get().startsWith(p.id())) {
        currentId.set(targets.isEmpty() ? "" : targets.keySet().iterator().next());
      }
    } else {
      Target t = Target.of(p).withFit(fit.get()).withFill(defaultFill());
      targets.put(t.id(), t);
      currentId.set(t.id());
    }
    saveTargets();
    rebuildRows();
    replan();
  }

  private FillSpec defaultFill() {
    return new FillSpec(fill.get(), strength.get(), 0xFF000000);
  }

  /** Destino actual (el que se encuadra en el visor). */
  public void select(String targetId) {
    if (targets.containsKey(targetId)) {
      currentId.set(targetId);
      syncCurrentControls();
      refreshOverlay();
      refreshThumbsSelection();
    }
  }

  private Optional<Target> current() {
    return Optional.ofNullable(targets.get(currentId.get()));
  }

  private Optional<PlannedTarget> currentPlanned() {
    WallpaperPlan p = plan.get();
    if (p == null) {
      return Optional.empty();
    }
    return p.targets().stream().filter(t -> t.target().id().equals(currentId.get())).findFirst();
  }

  private void updateCurrent(Function<Target, Target> change) {
    if (syncing) {
      return;
    }
    current()
        .ifPresent(
            t -> {
              Target next = change.apply(t);
              if (!next.equals(t)) {
                targets.put(t.id(), next);
                replan();
              }
            });
  }

  // ── Encuadre ──────────────────────────────────────────────────────────────

  @Override
  public ReadOnlyObjectProperty<FrameOverlayState> overlayProperty() {
    return overlay;
  }

  /** Arrastre en el visor: el destino pasa a encuadre manual (con su proporción). */
  @Override
  public void setFrame(CropModel frame) {
    Optional<PlannedTarget> p = currentPlanned();
    if (p.isEmpty() || p.get().filled()) {
      return;
    }
    FrameRect n =
        new FrameRect(
            frame.left(), frame.top(), frame.right() - frame.left(), frame.bottom() - frame.top());
    updateCurrent(t -> t.withManualFrame(Optional.of(n)));
  }

  /** Vuelve al encuadre automático el destino actual. */
  public void auto() {
    updateCurrent(t -> t.withManualFrame(Optional.empty()).withFit(FitMode.AUTO));
    fit.set(FitMode.AUTO);
  }

  /** Vuelve a automático en todos los destinos. */
  public void autoAll() {
    for (Map.Entry<String, Target> e : targets.entrySet()) {
      e.setValue(e.getValue().withManualFrame(Optional.empty()).withFit(FitMode.AUTO));
    }
    fit.set(FitMode.AUTO);
    replan();
  }

  /** Coloca el encuadre actual en uno de los 9 anclajes ({@code col, fila} en 0..2). */
  public void anchor(int col, int row) {
    Optional<PlannedTarget> p = currentPlanned();
    WallpaperPlan pl = plan.get();
    if (p.isEmpty() || p.get().filled() || pl == null) {
      return;
    }
    FrameRect f = p.get().frame();
    double x = (pl.sourceWidth() - f.w()) * col / 2.0;
    double y = (pl.sourceHeight() - f.h()) * row / 2.0;
    FrameRect n = new FrameRect(x, y, f.w(), f.h()).normalized(pl.sourceWidth(), pl.sourceHeight());
    updateCurrent(t -> t.withManualFrame(Optional.of(n)));
  }

  /** Gira el destino actual 90° (vertical ↔ horizontal). */
  public void rotateCurrent() {
    current()
        .ifPresent(
            t -> {
              Target r = t.rotated();
              Map<String, Target> copy = new LinkedHashMap<>();
              for (Map.Entry<String, Target> e : targets.entrySet()) {
                copy.put(
                    e.getKey().equals(t.id()) ? r.id() : e.getKey(),
                    e.getKey().equals(t.id()) ? r : e.getValue());
              }
              targets.clear();
              targets.putAll(copy);
              currentId.set(r.id());
              saveTargets();
              replan();
            });
  }

  // ── Plan ──────────────────────────────────────────────────────────────────

  private void replan() {
    if (!env.session().hasImage() || targets.isEmpty()) {
      plan.set(null);
      thumbs.clear();
      overlay.set(null);
      updateExportState();
      syncCurrentControls();
      return;
    }
    Size s = env.session().outputSize();
    List<Subject> subjects =
        lastAnalysis != null
                && lastAnalysis.width() == s.width()
                && lastAnalysis.height() == s.height()
            ? lastAnalysis.subjects()
            : List.of();
    plan.set(Planner.plan(s.width(), s.height(), subjects, List.copyOf(targets.values())));
    if (!targets.containsKey(currentId.get())) {
      currentId.set(targets.keySet().iterator().next());
    }
    syncCurrentControls();
    refreshOverlay();
    updateSuggestion();
    updateExportState();
    refreshThumbs();
  }

  private void syncCurrentControls() {
    Optional<PlannedTarget> p = currentPlanned();
    currentWarnings.clear();
    if (p.isEmpty()) {
      currentTitle.set("");
      currentInfo.set("");
      return;
    }
    PlannedTarget t = p.get();
    currentTitle.set(t.target().name());
    syncing = true;
    try {
      fit.set(t.target().fit());
      fill.set(t.target().fill().kind());
      strength.set(t.target().fill().strength());
    } finally {
      syncing = false;
    }
    currentInfo.set(
        m.format("ui.wallpapers.size", t.target().width(), t.target().height())
            + " · "
            + badgeText(t.badge())
            + " · "
            + m.format("ui.wallpapers.scale", scaleText(t.effectiveScale())));
    for (Warning w : t.warnings()) {
      currentWarnings.add(warningText(w, t));
    }
    if (t.filled() && t.target().fill().kind() == FillSpec.Kind.MIRROR) {
      currentWarnings.add(m.get("ui.wallpapers.warn.mirror"));
    }
  }

  String badgeText(QualityBadge b) {
    return switch (b) {
      case EXCELLENT -> m.get("ui.wallpapers.badge.excellent");
      case ACCEPTABLE -> m.get("ui.wallpapers.badge.acceptable");
      case BLURRY -> m.get("ui.wallpapers.badge.blurry");
    };
  }

  private String scaleText(double s) {
    return String.format(m.locale(), "%.2f", s);
  }

  private String warningText(Warning w, PlannedTarget t) {
    return switch (w) {
      case SUBJECT_CUT -> m.get("ui.wallpapers.warn.subjectCut");
      case BLURRY -> m.format("ui.wallpapers.warn.blurry", scaleText(t.effectiveScale()));
      case FILLED -> m.get("ui.wallpapers.warn.filled");
      case APPROXIMATE_PRESET -> m.get("ui.wallpapers.warn.approximate");
      case SAFE_ZONE_OVERLAP -> m.get("ui.wallpapers.warn.safeZone");
      case MANUAL_RESET -> m.get("ui.wallpapers.warn.manualReset");
    };
  }

  private void refreshOverlay() {
    Optional<PlannedTarget> p = currentPlanned();
    WallpaperPlan pl = plan.get();
    if (!active.get() || p.isEmpty() || pl == null) {
      overlay.set(null);
      return;
    }
    PlannedTarget t = p.get();
    double sw = pl.sourceWidth();
    double sh = pl.sourceHeight();
    FrameRect f = t.frame();
    double imageAspect = sw / sh;
    CropModel frame =
        new CropModel(
            f.x() / sw,
            f.y() / sh,
            f.right() / sw,
            f.bottom() / sh,
            t.filled() ? 0 : (double) t.target().width() / t.target().height(),
            imageAspect);
    List<FrameOverlayState.Zone> zones = new ArrayList<>();
    if (safeZones.get() && !t.filled()) {
      for (SafeZone z : t.target().safeZones()) {
        zones.add(new FrameOverlayState.Zone(zoneName(z.kind()), z.x(), z.y(), z.w(), z.h()));
      }
    }
    overlay.set(new FrameOverlayState(frame, zones, !t.filled(), !t.filled()));
  }

  String zoneName(SafeZone.Kind k) {
    return switch (k) {
      case CLOCK -> m.get("ui.wallpapers.zone.clock");
      case DOCK -> m.get("ui.wallpapers.zone.dock");
      case MENU_BAR -> m.get("ui.wallpapers.zone.menuBar");
      case NOTCH -> m.get("ui.wallpapers.zone.notch");
      case WIDGETS -> m.get("ui.wallpapers.zone.widgets");
    };
  }

  private void updateSuggestion() {
    WallpaperPlan pl = plan.get();
    boolean ready = enhance.applied();
    if (pl == null
        || pl.requiredEnhanceScale().isEmpty()
        || ready
        || !enhance.availableProperty().get()) {
      suggestion.set("");
      suggestionAction.set("");
      return;
    }
    double worst = pl.targets().stream().mapToDouble(PlannedTarget::effectiveScale).max().orElse(0);
    int k = pl.requiredEnhanceScale().getAsInt();
    suggestion.set(m.format("ui.wallpapers.suggest", scaleText(worst)));
    String time =
        env.session()
            .ai()
            .map(
                ai -> {
                  Size in = env.session().sizeBeforeEnhance();
                  double sec = ai.estimateSeconds(in.width(), in.height());
                  return sec < 60
                      ? m.format("ui.enhance.time.seconds", Math.max(1, Math.round(sec)))
                      : m.format("ui.enhance.time.minutes", (long) Math.ceil(sec / 60));
                })
            .orElse("");
    suggestionAction.set(m.format("ui.wallpapers.suggest.action", k, time));
  }

  /**
   * "Mejorar primero": la misma orden que el botón Mejorar, con la escala que pide el peor destino.
   * Es una acción explícita del usuario.
   */
  public void enhanceFirst() {
    WallpaperPlan pl = plan.get();
    if (pl == null || pl.requiredEnhanceScale().isEmpty()) {
      return;
    }
    enhance.setScale(
        pl.requiredEnhanceScale().getAsInt() == 4 ? EnhanceViewModel.X4 : EnhanceViewModel.X2);
    enhance.start();
  }

  /** La IA cambió de estado (lista, en curso...). */
  void enhanceChanged() {
    updateSuggestion();
  }

  private void updateExportState() {
    int n = targets.size();
    exportLabel.set(m.format("ui.wallpapers.export", n));
    canExport.set(n > 0 && env.session().hasImage() && !exporting.get());
  }

  // ── Miniaturas ────────────────────────────────────────────────────────────

  private void refreshThumbs() {
    WallpaperPlan pl = plan.get();
    Optional<Img> preview = host.previewPixels();
    if (!active.get() || pl == null || preview.isEmpty()) {
      return;
    }
    long gen = ++thumbGeneration;
    Img px = preview.get();
    env.background()
        .execute(
            () -> {
              List<Img> imgs = WallpaperWorkspace.thumbnails(px, pl, THUMB_SIDE);
              List<F> converted = imgs.stream().map(converter).toList();
              env.publisher()
                  .execute(
                      () -> {
                        if (gen != thumbGeneration || plan.get() != pl) {
                          return;
                        }
                        List<Thumb<F>> out = new ArrayList<>();
                        for (int i = 0; i < pl.targets().size(); i++) {
                          out.add(thumb(pl.targets().get(i), converted.get(i)));
                        }
                        thumbs.setAll(out);
                      });
            });
  }

  private Thumb<F> thumb(PlannedTarget t, F image) {
    boolean warn =
        t.warnings().contains(Warning.SUBJECT_CUT)
            || t.warnings().contains(Warning.BLURRY)
            || t.warnings().contains(Warning.SAFE_ZONE_OVERLAP);
    return new Thumb<>(
        t.target().id(),
        t.target().name(),
        image,
        t.badge(),
        t.filled(),
        warn,
        t.target().id().equals(currentId.get()));
  }

  private void refreshThumbsSelection() {
    List<Thumb<F>> out = new ArrayList<>();
    for (Thumb<F> t : thumbs) {
      out.add(
          new Thumb<>(
              t.id(),
              t.label(),
              t.image(),
              t.badge(),
              t.filled(),
              t.warning(),
              t.id().equals(currentId.get())));
    }
    thumbs.setAll(out);
  }

  // ── Presets propios ───────────────────────────────────────────────────────

  /** Añade un tamaño personalizado y lo selecciona. Devuelve el error a mostrar, o vacío. */
  public Optional<String> addCustom(String name, int width, int height) {
    if (name == null || name.isBlank()) {
      return Optional.of(m.get("ui.wallpapers.custom.invalid"));
    }
    Set<String> ids = new HashSet<>();
    catalog.all().forEach(p -> ids.add(p.id()));
    DevicePreset p;
    try {
      p =
          new DevicePreset(
              UserPresetStore.newId(name, ids),
              name.strip(),
              Group.CUSTOM,
              width,
              height,
              DevicePreset.Orientation.of(width, height),
              DevicePreset.Status.USER,
              "",
              Optional.empty(),
              List.of(),
              0,
              DevicePreset.Origin.USER);
    } catch (IllegalArgumentException e) {
      return Optional.of(m.get("ui.wallpapers.custom.invalid"));
    }
    List<DevicePreset> next = new ArrayList<>(userPresets);
    next.add(p);
    saveUserPresets(next);
    toggle(p);
    return Optional.empty();
  }

  /** Borra un preset propio (y lo quita de la selección). */
  public void deleteCustom(DevicePreset p) {
    if (p.origin() != DevicePreset.Origin.USER) {
      return;
    }
    if (isSelected(p.id())) {
      toggle(p);
    }
    List<DevicePreset> next = new ArrayList<>(userPresets);
    next.removeIf(x -> x.id().equals(p.id()));
    saveUserPresets(next);
  }

  private void saveUserPresets(List<DevicePreset> next) {
    userPresets = List.copyOf(next);
    rebuildCatalog(List.of());
    LocalDate today = host.now().toLocalDate();
    env.io()
        .execute(
            () -> {
              try {
                store.save(next, today);
              } catch (Exception e) {
                env.publisher()
                    .execute(
                        () -> host.error(m.get("ui.wallpapers.custom.saveFailed"), e.getMessage()));
              }
            });
  }

  // ── Exportación ───────────────────────────────────────────────────────────

  public void showExport() {
    if (!canExport.get()) {
      return;
    }
    refreshNamePreview();
    dialogOpen.set(true);
    refreshEstimate();
  }

  public void closeExport() {
    dialogOpen.set(false);
    cancelEstimate();
    estimates.clear();
    estimateTotal.set("");
  }

  /** Peso aproximado de cada archivo ("PC 4K UHD: ≈ 3,4 MB"), en el orden de la tanda. */
  public ObservableList<String> estimates() {
    return estimates;
  }

  /** Total aproximado de la tanda, o el aviso de que se está calculando. */
  public ReadOnlyStringProperty estimateTotalProperty() {
    return estimateTotal;
  }

  private void refreshEstimateIfOpen() {
    if (dialogOpen.get()) {
      refreshEstimate();
    }
  }

  private void cancelEstimate() {
    estimateGeneration++;
    estimateToken.cancel();
  }

  /**
   * Estima el peso de cada destino sobre la vista reducida, fuera del hilo de la interfaz; una
   * estimación nueva cancela la anterior y solo se publica la última.
   */
  private void refreshEstimate() {
    cancelEstimate();
    long gen = estimateGeneration;
    WallpaperPlan pl = plan.get();
    Optional<Img> preview = host.previewPixels();
    estimates.clear();
    if (pl == null || pl.targets().isEmpty() || preview.isEmpty() || folder.get().isBlank()) {
      estimateTotal.set("");
      return;
    }
    WallpaperExport.Settings s;
    List<WallpaperExport.Item> items;
    try {
      s = settings(ExportTarget.Collision.NUMBER);
      items = WallpaperExport.layout(pl, s, batch(), this::groupName);
    } catch (RuntimeException e) {
      estimateTotal.set("");
      return;
    }
    CancellationToken token = CancellationToken.create();
    estimateToken = token;
    estimateTotal.set(m.get("ui.wallpapers.dialog.estimating"));
    Img px = preview.get();
    env.background()
        .execute(
            () -> {
              List<Long> sizes;
              try {
                sizes = WallpaperExport.estimate(px, pl, items, s, token);
              } catch (CancelledException e) {
                sizes = null;
              } catch (Exception e) {
                System.getLogger("rora.wallpapers")
                    .log(System.Logger.Level.WARNING, "No se pudo estimar el tamaño", e);
                sizes = null;
              }
              List<Long> result = sizes;
              env.publisher()
                  .execute(
                      () -> {
                        if (gen != estimateGeneration) {
                          return;
                        }
                        if (result == null) {
                          estimateTotal.set("");
                          return;
                        }
                        long total = 0;
                        List<String> lines = new ArrayList<>();
                        for (int i = 0; i < result.size(); i++) {
                          total += result.get(i);
                          lines.add(
                              m.format(
                                  "ui.wallpapers.dialog.estimateRow",
                                  items.get(i).planned().target().name(),
                                  humanSize(result.get(i))));
                        }
                        estimates.setAll(lines);
                        estimateTotal.set(
                            m.format("ui.wallpapers.dialog.estimate", humanSize(total)));
                      });
            });
  }

  String humanSize(long bytes) {
    Locale l = m.locale();
    if (bytes >= 1024L * 1024) {
      return String.format(l, "%.1f MB", bytes / (1024.0 * 1024));
    }
    return String.format(l, "%d KB", Math.max(1, bytes / 1024));
  }

  private WallpaperExport.Settings settings(ExportTarget.Collision collision) {
    return new WallpaperExport.Settings(
        Path.of(folder.get()),
        folderTemplate.get(),
        fileTemplate.get(),
        format.get(),
        quality.get(),
        metadata.get(),
        collision,
        0xFFFFFFFF);
  }

  private TargetNames.Batch batch() {
    return new TargetNames.Batch(host.sourceBaseName(), host.now());
  }

  private void refreshNamePreview() {
    WallpaperPlan pl = plan.get();
    if (pl == null || pl.targets().isEmpty() || folder.get().isBlank()) {
      namePreview.set("");
      return;
    }
    try {
      List<WallpaperExport.Item> items =
          WallpaperExport.layout(
              pl, settings(ExportTarget.Collision.NUMBER), batch(), this::groupName);
      Path root = Path.of(folder.get());
      namePreview.set(root.relativize(items.getFirst().file()).toString());
    } catch (RuntimeException e) {
      namePreview.set("");
    }
  }

  /** Botón Exportar del diálogo. */
  public void confirmExport() {
    WallpaperPlan pl = plan.get();
    if (pl == null || folder.get().isBlank()) {
      return;
    }
    dialogOpen.set(false);
    cancelEstimate();
    savePrefs();
    Runnable proceed = () -> checkCollisions(pl);
    if (enhance.needsDecision()) {
      askEnhance(proceed);
    } else {
      proceed.run();
    }
  }

  private void askEnhance(Runnable proceed) {
    boolean canRun = enhance.canRunForExport();
    List<Prompt.Choice> choices = new ArrayList<>();
    if (canRun) {
      choices.add(
          new Prompt.Choice(
              enhance.exportChoiceLabel(),
              true,
              () -> {
                host.prompt(null);
                enhance.runThen(proceed, () -> host.notice(m.get("ui.enhance.export.abandoned")));
              }));
    }
    choices.add(
        new Prompt.Choice(
            m.get("ui.enhance.export.classic"),
            !canRun,
            () -> {
              host.prompt(null);
              proceed.run();
            }));
    choices.add(new Prompt.Choice(m.get("ui.action.cancel"), false, () -> host.prompt(null)));
    host.prompt(
        new Prompt(m.get("ui.enhance.export.title"), m.get("ui.enhance.export.body"), choices));
  }

  private void checkCollisions(WallpaperPlan pl) {
    List<WallpaperExport.Item> items =
        WallpaperExport.layout(
            pl, settings(ExportTarget.Collision.NUMBER), batch(), this::groupName);
    env.io()
        .execute(
            () -> {
              List<Path> existing = WallpaperExport.existing(items);
              env.publisher()
                  .execute(
                      () -> {
                        if (existing.isEmpty()) {
                          start(pl, ExportTarget.Collision.NUMBER);
                        } else {
                          askCollision(pl, existing.size());
                        }
                      });
            });
  }

  private void askCollision(WallpaperPlan pl, int count) {
    host.prompt(
        new Prompt(
            m.get("ui.wallpapers.collision.title"),
            m.format("ui.wallpapers.collision.body", count),
            List.of(
                new Prompt.Choice(
                    m.get("ui.collision.number"),
                    true,
                    () -> {
                      host.prompt(null);
                      start(pl, ExportTarget.Collision.NUMBER);
                    }),
                new Prompt.Choice(
                    m.get("ui.collision.overwrite"),
                    false,
                    () -> {
                      host.prompt(null);
                      start(pl, ExportTarget.Collision.OVERWRITE);
                    }),
                new Prompt.Choice(m.get("ui.action.cancel"), false, () -> host.prompt(null)))));
  }

  private void start(WallpaperPlan pl, ExportTarget.Collision collision) {
    WallpaperExport.Settings s = settings(collision);
    List<WallpaperExport.Item> items = WallpaperExport.layout(pl, s, batch(), this::groupName);
    exportToken = CancellationToken.create();
    exporting.set(true);
    updateExportState();
    progress.set(0);
    progressText.set("");
    report.set(null);
    workspace.export(
        env.session(),
        pl,
        items,
        s,
        host.metadata(),
        host.provenance(),
        exportToken,
        env.io(),
        env.publisher(),
        new WallpaperWorkspace.ExportListener() {
          @Override
          public void started(int index, int total, String name) {
            progressText.set(m.format("ui.wallpapers.progress", index + 1, total, name));
          }

          @Override
          public void progress(double fraction) {
            progress.set(fraction);
          }

          @Override
          public void done(WallpaperResult result) {
            finished(result, s.root());
          }

          @Override
          public void failed(String message) {
            exporting.set(false);
            progress.set(-1);
            updateExportState();
            host.error(m.get("ui.wallpapers.failed"), message);
          }
        });
  }

  /** Cancela la exportación en curso: lo ya escrito se queda; lo demás no deja restos. */
  public void cancelExport() {
    exportToken.cancel();
  }

  private void finished(WallpaperResult r, Path root) {
    exporting.set(false);
    progress.set(-1);
    progressText.set("");
    updateExportState();
    if (r.written() > 0) {
      env.session().markExported();
    }
    List<String> failures = new ArrayList<>();
    Optional<Path> anyFolder = Optional.empty();
    for (WallpaperResult.Outcome o : r.outcomes()) {
      if (o instanceof WallpaperResult.Failed f) {
        failures.add(m.format("ui.wallpapers.report.failed", f.target().name(), f.reason()));
      } else if (o instanceof WallpaperResult.Written w && anyFolder.isEmpty()) {
        anyFolder = Optional.of(w.file());
      }
    }
    report.set(
        new Report(
            m.format("ui.wallpapers.report.summary", r.written(), r.failed(), r.cancelled()),
            failures,
            anyFolder.isPresent() ? anyFolder : Optional.of(root)));
  }

  public void closeReport() {
    report.set(null);
  }

  public void revealReport() {
    Report r = report.get();
    if (r != null) {
      r.folder().ifPresent(host::reveal);
    }
  }

  // ── Preferencias ──────────────────────────────────────────────────────────

  private void restorePrefs() {
    fit.set(parse(FitMode.class, prefs.get(KEY_FIT, ""), FitMode.AUTO));
    FillSpec.Kind k = parse(FillSpec.Kind.class, prefs.get(KEY_FILL, ""), FillSpec.Kind.BLUR);
    fill.set(k == FillSpec.Kind.GRADIENT ? FillSpec.Kind.BLUR : k);
    try {
      strength.set(Math.max(0, Math.min(1, Double.parseDouble(prefs.get(KEY_STRENGTH, "")))));
    } catch (NumberFormatException e) {
      strength.set(FillSpec.DEFAULT_STRENGTH);
    }
    format.set(parse(OutputFormat.class, prefs.get(KEY_FORMAT, ""), OutputFormat.AUTO));
    try {
      quality.set(Math.max(1, Math.min(100, Integer.parseInt(prefs.get(KEY_QUALITY, "")))));
    } catch (NumberFormatException e) {
      quality.set(WallpaperExport.JPEG_QUALITY);
    }
    metadata.set(
        parse(MetadataPolicy.class, prefs.get(KEY_METADATA, ""), MetadataPolicy.REMOVE_ALL));
    folder.set(prefs.get(KEY_FOLDER, ""));
    folderTemplate.set(prefs.get(KEY_FOLDER_TEMPLATE, TargetNames.DEFAULT_FOLDER));
    fileTemplate.set(prefs.get(KEY_FILE_TEMPLATE, TargetNames.DEFAULT_FILE));
    detectFaces.set(!"false".equals(prefs.get(KEY_DETECT, "true")));
  }

  private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
    try {
      return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return fallback;
    }
  }

  private void restoreTargets() {
    String saved = prefs.get(KEY_TARGETS, "");
    List<String> ids = new ArrayList<>();
    if (saved.isBlank()) {
      screens.forEach(s -> ids.add(s.id()));
      ids.addAll(DEFAULT_TARGETS);
    } else {
      ids.addAll(List.of(saved.split(",")));
    }
    for (String raw : ids) {
      boolean rotated = raw.endsWith("@rot");
      String id = rotated ? raw.substring(0, raw.length() - 4) : raw;
      catalog
          .byId(id)
          .ifPresent(
              p -> {
                Target t = Target.of(p).withFit(fit.get()).withFill(defaultFill());
                t = rotated ? t.rotated() : t;
                targets.put(t.id(), t);
              });
    }
    if (!targets.isEmpty()) {
      currentId.set(targets.keySet().iterator().next());
    }
    rebuildRows();
  }

  private void saveTargets() {
    prefs.put(KEY_TARGETS, String.join(",", targets.keySet()));
  }

  private void savePrefs() {
    prefs.put(KEY_FIT, fit.get().name());
    prefs.put(KEY_FILL, fill.get().name());
    prefs.put(KEY_STRENGTH, Double.toString(strength.get()));
    prefs.put(KEY_FORMAT, format.get().name());
    prefs.put(KEY_QUALITY, Integer.toString(quality.get()));
    prefs.put(KEY_METADATA, metadata.get().name());
    prefs.put(KEY_FOLDER, folder.get());
    prefs.put(KEY_FOLDER_TEMPLATE, folderTemplate.get());
    prefs.put(KEY_FILE_TEMPLATE, fileTemplate.get());
  }

  /** Carpeta por defecto si el usuario no eligió ninguna (la del último archivo abierto). */
  void suggestFolder(String path) {
    if (folder.get().isBlank() && path != null) {
      folder.set(path);
    }
  }

  void close() {
    exportToken.cancel();
    workspace.close();
  }

  // ── Propiedades ───────────────────────────────────────────────────────────

  public ReadOnlyBooleanProperty activeProperty() {
    return active;
  }

  public StringProperty queryProperty() {
    return query;
  }

  public ObservableList<Row> rows() {
    return rows;
  }

  public ObservableList<Thumb<F>> thumbs() {
    return thumbs;
  }

  public ReadOnlyObjectProperty<WallpaperPlan> planProperty() {
    return plan;
  }

  public ReadOnlyStringProperty currentIdProperty() {
    return currentId;
  }

  public ObjectProperty<FitMode> fitProperty() {
    return fit;
  }

  public ObjectProperty<FillSpec.Kind> fillProperty() {
    return fill;
  }

  public DoubleProperty strengthProperty() {
    return strength;
  }

  public ReadOnlyStringProperty currentTitleProperty() {
    return currentTitle;
  }

  public ReadOnlyStringProperty currentInfoProperty() {
    return currentInfo;
  }

  public ObservableList<String> currentWarnings() {
    return currentWarnings;
  }

  public BooleanProperty safeZonesProperty() {
    return safeZones;
  }

  public BooleanProperty detectFacesProperty() {
    return detectFaces;
  }

  public ReadOnlyObjectProperty<AnalysisState> analysisProperty() {
    return analysis;
  }

  public ReadOnlyStringProperty suggestionProperty() {
    return suggestion;
  }

  public ReadOnlyStringProperty suggestionActionProperty() {
    return suggestionAction;
  }

  public ReadOnlyStringProperty exportLabelProperty() {
    return exportLabel;
  }

  public ReadOnlyBooleanProperty canExportProperty() {
    return canExport;
  }

  public ObservableList<String> catalogProblems() {
    return catalogProblems;
  }

  public ReadOnlyBooleanProperty dialogOpenProperty() {
    return dialogOpen;
  }

  public StringProperty folderProperty() {
    return folder;
  }

  public StringProperty folderTemplateProperty() {
    return folderTemplate;
  }

  public StringProperty fileTemplateProperty() {
    return fileTemplate;
  }

  public ObjectProperty<OutputFormat> formatProperty() {
    return format;
  }

  public IntegerProperty qualityProperty() {
    return quality;
  }

  public ObjectProperty<MetadataPolicy> metadataProperty() {
    return metadata;
  }

  public ReadOnlyStringProperty namePreviewProperty() {
    return namePreview;
  }

  public ReadOnlyBooleanProperty exportingProperty() {
    return exporting;
  }

  public ReadOnlyDoubleProperty progressProperty() {
    return progress;
  }

  public ReadOnlyStringProperty progressTextProperty() {
    return progressText;
  }

  public ReadOnlyObjectProperty<Report> reportProperty() {
    return report;
  }

  public Messages messages() {
    return m;
  }
}
