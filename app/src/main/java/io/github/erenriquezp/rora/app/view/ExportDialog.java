package io.github.erenriquezp.rora.app.view;

import atlantafx.base.theme.Styles;
import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.session.ExportTarget.Collision;
import io.github.erenriquezp.rora.app.viewmodel.ExportDialogViewModel;
import io.github.erenriquezp.rora.app.viewmodel.ExportDialogViewModel.Preset;
import io.github.erenriquezp.rora.core.io.ExportSettings.MetadataPolicy;
import io.github.erenriquezp.rora.core.io.ExportSettings.ProfilePolicy;
import io.github.erenriquezp.rora.core.io.ExportSettings.Subsampling;
import io.github.erenriquezp.rora.core.io.ExportSettings.TiffCompression;
import io.github.erenriquezp.rora.core.io.FormatId;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import javafx.util.StringConverter;

/** Contenido del diálogo de exportación: solo cablea controles con su ViewModel. */
public final class ExportDialog {

  private static final double LABEL_WIDTH = 170;

  private final ExportDialogViewModel<?> vm;
  private final Messages m;
  private final Subscriptions subs = new Subscriptions();
  private final VBox rows = new VBox(10);

  public ExportDialog(ExportDialogViewModel<?> vm, Messages m) {
    this.vm = vm;
    this.m = m;
  }

  /** Construye la vista; {@code owner} da la ventana del selector de carpeta. */
  public Parent build(java.util.function.Supplier<Window> owner) {
    rows.getStyleClass().add("export-grid");

    row("ui.export.preset", presets());
    row(
        "ui.export.format",
        combo(
            vm.formatProperty(),
            List.of(FormatId.JPEG, FormatId.PNG, FormatId.TIFF),
            this::formatLabel));

    Node[] quality = row("ui.export.quality", quality());
    Node[] subsampling =
        row(
            "ui.export.subsampling",
            combo(
                vm.subsamplingProperty(),
                List.of(Subsampling.S420, Subsampling.S444),
                this::subsamplingLabel));
    Node[] options = row("", checks());
    Node[] background = row("ui.export.background", background());
    Node[] deflate = row("ui.export.deflate", deflate());
    Node[] tiff =
        row(
            "ui.export.tiff",
            combo(vm.tiffProperty(), List.of(TiffCompression.values()), this::tiffLabel));
    subs.add(
        vm.formatProperty()
            .subscribe(
                f -> {
                  show(f == FormatId.JPEG, quality, subsampling, options, background);
                  show(f == FormatId.PNG, deflate);
                  show(f == FormatId.TIFF, tiff);
                }));

    row("ui.export.limitSide", limit(vm.limitSideProperty(), vm.sideProperty(), 16384, "px"));
    Node[] weight =
        row(
            "ui.export.limitWeight",
            limit(vm.limitWeightProperty(), vm.weightKbProperty(), 1_000_000, "KB"));
    subs.add(vm.formatProperty().subscribe(f -> show(f == FormatId.JPEG, weight)));

    row(
        "ui.export.metadata",
        combo(
            vm.metadataProperty(),
            List.of(MetadataPolicy.REMOVE_LOCATION, MetadataPolicy.REMOVE_ALL, MetadataPolicy.KEEP),
            this::metadataLabel));
    row(
        "ui.export.profile",
        combo(vm.profileProperty(), List.of(ProfilePolicy.values()), this::profileLabel));
    row("ui.export.folder", folder(owner));
    TextField template = new TextField();
    template.textProperty().bindBidirectional(vm.templateProperty());
    row("ui.export.name", template);
    row(
        "ui.export.collision",
        combo(vm.collisionProperty(), List.of(Collision.values()), this::collisionLabel));

    Label previewCaption = new Label(m.get("ui.export.preview"));
    previewCaption.getStyleClass().add("export-label");
    previewCaption.setMinWidth(Region.USE_PREF_SIZE);
    Label preview = new Label();
    preview.getStyleClass().add("export-preview");
    preview.setWrapText(true);
    preview.textProperty().bind(vm.previewNameProperty());
    HBox previewLine = new HBox(8, previewCaption, preview);
    previewLine.setAlignment(Pos.CENTER_LEFT);

    Label estimate = new Label();
    estimate.getStyleClass().add("export-estimate");
    estimate.textProperty().bind(vm.estimateProperty());
    Label error = new Label();
    error.getStyleClass().add("export-error");
    error.setWrapText(true);
    error.textProperty().bind(vm.errorProperty());
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    Button cancel = new Button(m.get("ui.action.cancel"));
    cancel.setCancelButton(true);
    cancel.setOnAction(e -> vm.close());
    Button export = new Button(m.get("ui.action.export"));
    export.getStyleClass().add(Styles.ACCENT);
    export.setDefaultButton(true);
    export.disableProperty().bind(vm.canExportProperty().not());
    export.setOnAction(e -> vm.accept());
    HBox buttons = new HBox(10, estimate, error, spacer, cancel, export);
    buttons.setAlignment(Pos.CENTER_LEFT);
    VBox footer = new VBox(10, previewLine, buttons);
    footer.getStyleClass().add("export-buttons");

    ScrollPane scroll = new ScrollPane(rows);
    scroll.setFitToWidth(true);
    scroll.getStyleClass().add("export-scroll");
    VBox.setVgrow(scroll, Priority.ALWAYS);
    VBox root = new VBox(scroll, footer);
    root.getStyleClass().add("export-dialog");
    return root;
  }

  private Node[] row(String key, Node control) {
    Label label = new Label(key.isEmpty() ? "" : m.get(key));
    label.getStyleClass().add("export-label");
    label.setMinWidth(LABEL_WIDTH);
    label.setPrefWidth(LABEL_WIDTH);
    label.setAlignment(Pos.CENTER_RIGHT);
    HBox.setHgrow(control, Priority.ALWAYS);
    HBox line = new HBox(12, label, control);
    line.setAlignment(Pos.CENTER_LEFT);
    rows.getChildren().add(line);
    return new Node[] {line};
  }

  @SafeVarargs
  private static void show(boolean visible, Node[]... rows) {
    for (Node[] r : rows) {
      for (Node n : r) {
        n.setVisible(visible);
        n.setManaged(visible);
      }
    }
  }

  private Node presets() {
    Button web = presetButton("ui.export.preset.web", Preset.WEB);
    Button max = presetButton("ui.export.preset.max", Preset.MAX_QUALITY);
    Button original = presetButton("ui.export.preset.original", Preset.ORIGINAL);
    return new HBox(6, web, max, original);
  }

  private Button presetButton(String key, Preset preset) {
    Button b = new Button(m.get(key));
    b.setOnAction(e -> vm.applyPreset(preset));
    return b;
  }

  private <T> ComboBox<T> combo(ObjectProperty<T> value, List<T> items, Function<T, String> text) {
    ComboBox<T> c = new ComboBox<>();
    c.getItems().setAll(items);
    c.setConverter(
        new StringConverter<>() {
          @Override
          public String toString(T item) {
            return item == null ? "" : text.apply(item);
          }

          @Override
          public T fromString(String s) {
            return null;
          }
        });
    c.valueProperty().bindBidirectional(value);
    c.setMaxWidth(Double.MAX_VALUE);
    return c;
  }

  private Node quality() {
    return sliderWithValue(vm.qualityProperty(), 1, 100, false);
  }

  private Node sliderWithValue(IntegerProperty value, int min, int max, boolean ticks) {
    Slider s = new Slider(min, max, value.get());
    s.setBlockIncrement(ticks ? 1 : 5);
    if (ticks) {
      s.setMajorTickUnit(1);
      s.setMinorTickCount(0);
      s.setSnapToTicks(true);
    }
    s.valueProperty().bindBidirectional(value);
    HBox.setHgrow(s, Priority.ALWAYS);
    Label text = new Label();
    text.setMinWidth(30);
    text.textProperty().bind(Bindings.format(Locale.ROOT, "%d", value));
    HBox box = new HBox(10, s, text);
    box.setAlignment(Pos.CENTER_LEFT);
    return box;
  }

  private Node checks() {
    CheckBox progressive = new CheckBox(m.get("ui.export.progressive"));
    progressive.selectedProperty().bindBidirectional(vm.progressiveProperty());
    CheckBox huffman = new CheckBox(m.get("ui.export.huffman"));
    huffman.selectedProperty().bindBidirectional(vm.huffmanProperty());
    return new HBox(16, progressive, huffman);
  }

  private Node background() {
    int rgb = vm.backgroundProperty().get();
    ColorPicker picker = new ColorPicker(Color.rgb(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255));
    picker.setOnAction(
        e -> {
          Color c = picker.getValue();
          vm.backgroundProperty()
              .set(
                  (int) Math.round(c.getRed() * 255) << 16
                      | (int) Math.round(c.getGreen() * 255) << 8
                      | (int) Math.round(c.getBlue() * 255));
        });
    return picker;
  }

  private Node deflate() {
    return sliderWithValue(vm.deflateProperty(), 0, 9, true);
  }

  private Node limit(BooleanProperty on, IntegerProperty value, int max, String unit) {
    CheckBox check = new CheckBox();
    check.selectedProperty().bindBidirectional(on);
    Spinner<Integer> spinner = new Spinner<>(1, max, Math.max(1, value.get()), 1);
    spinner.setEditable(true);
    spinner.setPrefWidth(130);
    spinner.disableProperty().bind(on.not());
    spinner.valueProperty().addListener((o, a, b) -> value.set(b));
    subs.add(
        value.subscribe(
            v -> {
              int current = v.intValue();
              if (current >= 1 && spinner.getValue() != current) {
                spinner.getValueFactory().setValue(current);
              }
            }));
    HBox box = new HBox(8, check, spinner, new Label(unit));
    box.setAlignment(Pos.CENTER_LEFT);
    return box;
  }

  private Node folder(java.util.function.Supplier<Window> owner) {
    TextField path = new TextField();
    path.textProperty().bindBidirectional(vm.folderProperty());
    HBox.setHgrow(path, Priority.ALWAYS);
    Button change = new Button(m.get("ui.export.change"));
    change.setOnAction(
        e -> {
          DirectoryChooser chooser = new DirectoryChooser();
          chooser.setTitle(m.get("ui.export.folder"));
          File current = new File(vm.folderProperty().get());
          if (current.isDirectory()) {
            chooser.setInitialDirectory(current);
          }
          File chosen = chooser.showDialog(owner.get());
          if (chosen != null) {
            vm.folderProperty().set(chosen.getPath());
          }
        });
    HBox box = new HBox(8, path, change);
    box.setPadding(Insets.EMPTY);
    return box;
  }

  private String formatLabel(FormatId f) {
    return switch (f) {
      case JPEG -> m.get("ui.export.format.jpeg");
      case PNG -> m.get("ui.export.format.png");
      default -> m.get("ui.export.format.tiff");
    };
  }

  private String subsamplingLabel(Subsampling s) {
    return s == Subsampling.S444
        ? m.get("ui.export.subsampling.full")
        : m.get("ui.export.subsampling.reduced");
  }

  private String tiffLabel(TiffCompression c) {
    return switch (c) {
      case NONE -> m.get("ui.export.tiff.none");
      case LZW -> m.get("ui.export.tiff.lzw");
      case DEFLATE -> m.get("ui.export.tiff.deflate");
      case PACKBITS -> m.get("ui.export.tiff.packbits");
    };
  }

  private String metadataLabel(MetadataPolicy p) {
    return switch (p) {
      case KEEP -> m.get("ui.export.metadata.keep");
      case REMOVE_LOCATION -> m.get("ui.export.metadata.location");
      case REMOVE_ALL -> m.get("ui.export.metadata.all");
    };
  }

  private String profileLabel(ProfilePolicy p) {
    return p == ProfilePolicy.EMBED_SRGB
        ? m.get("ui.export.profile.embed")
        : m.get("ui.export.profile.untagged");
  }

  private String collisionLabel(Collision c) {
    return switch (c) {
      case ASK -> m.get("ui.export.collision.ask");
      case NUMBER -> m.get("ui.export.collision.number");
      case OVERWRITE -> m.get("ui.export.collision.overwrite");
    };
  }
}
