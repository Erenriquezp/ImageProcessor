package io.github.erenriquezp.rora.app.view;

import atlantafx.base.theme.Styles;
import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.viewmodel.WallpapersViewModel;
import io.github.erenriquezp.rora.app.viewmodel.WallpapersViewModel.Row;
import io.github.erenriquezp.rora.app.viewmodel.WallpapersViewModel.Thumb;
import io.github.erenriquezp.rora.wallpapers.plan.QualityBadge;
import io.github.erenriquezp.rora.wallpapers.plan.Target.FitMode;
import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset;
import io.github.erenriquezp.rora.wallpapers.render.FillSpec;
import java.util.List;
import java.util.Optional;
import javafx.beans.value.ObservableStringValue;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Herramienta Wallpapers: panel del cajón y tira de destinos. Solo cablea con el ViewModel. */
public final class WallpapersView {

  private WallpapersView() {}

  /** Panel del cajón: dispositivos, destino actual y exportar. */
  public static VBox panel(
      WallpapersViewModel<? extends Image> vm, Messages m, Subscriptions subs) {
    Label title = new Label(m.get("ui.wallpapers.title"));
    title.getStyleClass().add("panel-title");

    VBox body =
        new VBox(
            12,
            analysisRow(vm, m, subs),
            devices(vm, m, subs),
            customRow(vm, m),
            new javafx.scene.control.Separator(),
            none(vm, m),
            current(vm, m, subs),
            suggestion(vm),
            exportRow(vm, m));
    body.setPadding(new Insets(0, 0, 8, 0));
    ScrollPane scroll = new ScrollPane(body);
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.getStyleClass().add("context-scroll");
    VBox.setVgrow(scroll, Priority.ALWAYS);
    VBox box = new VBox(title, scroll);
    box.getStyleClass().addAll("drawer", "wallpapers-drawer");
    return box;
  }

  private static Node analysisRow(WallpapersViewModel<?> vm, Messages m, Subscriptions subs) {
    Label state = new Label();
    state.getStyleClass().add("hint");
    state.setWrapText(true);
    subs.add(
        vm.analysisProperty()
            .subscribe(
                a ->
                    state.setText(
                        switch (a) {
                          case ANALYZING -> m.get("ui.wallpapers.analyzing");
                          case NO_DETECTOR -> m.get("ui.wallpapers.noDetector");
                          default -> "";
                        })));
    state.visibleProperty().bind(state.textProperty().isNotEmpty());
    state.managedProperty().bind(state.visibleProperty());
    CheckBox detect = new CheckBox(m.get("ui.wallpapers.detect"));
    detect.selectedProperty().bindBidirectional(vm.detectFacesProperty());
    return new VBox(4, detect, state);
  }

  private static Node devices(
      WallpapersViewModel<? extends Image> vm, Messages m, Subscriptions subs) {
    TextField search = new TextField();
    search.setPromptText(m.get("ui.wallpapers.search"));
    search.setAccessibleText(m.get("ui.wallpapers.search"));
    search.textProperty().bindBidirectional(vm.queryProperty());
    ListView<Row> list = new ListView<>(vm.rows());
    list.getStyleClass().add("wallpapers-devices");
    list.setPrefHeight(260);
    list.setMinHeight(140);
    list.setCellFactory(
        lv ->
            new ListCell<>() {
              @Override
              protected void updateItem(Row row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().remove("wallpapers-group");
                if (empty || row == null) {
                  setGraphic(null);
                  setText(null);
                  return;
                }
                if (row.header()) {
                  setText(row.label());
                  setGraphic(null);
                  getStyleClass().add("wallpapers-group");
                  return;
                }
                setText(null);
                CheckBox box = new CheckBox(row.label());
                box.setSelected(row.selected());
                box.setOnAction(e -> vm.toggle(row.preset()));
                Label detail = new Label(row.detail());
                detail.getStyleClass().add("hint");
                Region gap = new Region();
                HBox.setHgrow(gap, Priority.ALWAYS);
                HBox line = new HBox(8, box, gap, detail);
                line.setAlignment(Pos.CENTER_LEFT);
                if (row.preset().origin() == DevicePreset.Origin.USER) {
                  Button del = new Button(null, Icons.icon("fas-trash-alt"));
                  del.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
                  del.setAccessibleText(m.get("ui.wallpapers.custom.delete"));
                  del.setOnAction(e -> vm.deleteCustom(row.preset()));
                  line.getChildren().add(del);
                }
                setGraphic(line);
              }
            });
    return new VBox(6, search, list);
  }

  private static Node customRow(WallpapersViewModel<?> vm, Messages m) {
    TextField name = new TextField();
    name.setPromptText(m.get("ui.wallpapers.custom.name"));
    TextField w = new TextField();
    w.setPromptText(m.get("ui.wallpapers.custom.width"));
    w.setPrefColumnCount(5);
    TextField h = new TextField();
    h.setPromptText(m.get("ui.wallpapers.custom.height"));
    h.setPrefColumnCount(5);
    Label error = new Label();
    error.getStyleClass().add("enhance-limit");
    error.setWrapText(true);
    error.visibleProperty().bind(error.textProperty().isNotEmpty());
    error.managedProperty().bind(error.visibleProperty());
    Button add = new Button(m.get("ui.wallpapers.custom.add"));
    add.setOnAction(
        e -> {
          Optional<String> problem;
          try {
            problem =
                vm.addCustom(
                    name.getText(),
                    Integer.parseInt(w.getText().strip()),
                    Integer.parseInt(h.getText().strip()));
          } catch (NumberFormatException ex) {
            problem = Optional.of(m.get("ui.wallpapers.custom.invalid"));
          }
          error.setText(problem.orElse(""));
          if (problem.isEmpty()) {
            name.clear();
            w.clear();
            h.clear();
          }
        });
    HBox sizes = new HBox(6, w, new Label("×"), h, add);
    sizes.setAlignment(Pos.CENTER_LEFT);
    return new VBox(6, name, sizes, error);
  }

  private static Node current(WallpapersViewModel<?> vm, Messages m, Subscriptions subs) {
    Label name = new Label();
    name.getStyleClass().add("control-label");
    name.textProperty().bind(vm.currentTitleProperty());
    Label info = new Label();
    info.getStyleClass().add("hint");
    info.textProperty().bind(vm.currentInfoProperty());
    VBox warnings = new VBox(2);
    vm.currentWarnings()
        .addListener(
            (ListChangeListener<String>)
                c -> warnings.getChildren().setAll(warningLabels(vm.currentWarnings())));

    ToggleGroup fitGroup = new ToggleGroup();
    HBox fit = new HBox();
    fit.getStyleClass().add("segmented");
    for (FitMode f : FitMode.values()) {
      ToggleButton b =
          new ToggleButton(
              switch (f) {
                case AUTO -> m.get("ui.wallpapers.fit.auto");
                case COVER -> m.get("ui.wallpapers.fit.cover");
                case CONTAIN -> m.get("ui.wallpapers.fit.contain");
              });
      b.setUserData(f);
      b.setToggleGroup(fitGroup);
      b.setOnAction(e -> vm.fitProperty().set(f));
      fit.getChildren().add(b);
    }
    subs.add(
        vm.fitProperty()
            .subscribe(
                f -> fitGroup.getToggles().forEach(t -> t.setSelected(t.getUserData() == f))));

    ComboBox<FillSpec.Kind> fill = new ComboBox<>();
    // El degradado no se ofrece: en las hojas ciegas se marcó como "no lo usaría" en 6 de 6.
    fill.getItems().setAll(FillSpec.Kind.BLUR, FillSpec.Kind.MIRROR, FillSpec.Kind.SOLID);
    fill.setConverter(
        new StringConverter<>() {
          @Override
          public String toString(FillSpec.Kind k) {
            if (k == null) {
              return "";
            }
            return switch (k) {
              case BLUR, GRADIENT -> m.get("ui.wallpapers.fill.blur");
              case MIRROR -> m.get("ui.wallpapers.fill.mirror");
              case SOLID -> m.get("ui.wallpapers.fill.solid");
            };
          }

          @Override
          public FillSpec.Kind fromString(String s) {
            return null;
          }
        });
    fill.valueProperty().bindBidirectional(vm.fillProperty());
    Slider strength = new Slider(0, 1, vm.strengthProperty().get());
    strength.valueProperty().bindBidirectional(vm.strengthProperty());
    strength.setAccessibleText(m.get("ui.wallpapers.strength"));
    VBox fillBox =
        new VBox(
            4,
            label(m.get("ui.wallpapers.fill")),
            fill,
            label(m.get("ui.wallpapers.strength")),
            strength);

    Button auto = new Button(m.get("ui.wallpapers.auto"));
    auto.setOnAction(e -> vm.auto());
    Button autoAll = new Button(m.get("ui.wallpapers.autoAll"));
    autoAll.setOnAction(e -> vm.autoAll());
    Button rotate = new Button(m.get("ui.wallpapers.rotate"), Icons.icon("fas-redo"));
    rotate.setOnAction(e -> vm.rotateCurrent());
    HBox actions = new HBox(6, auto, autoAll, rotate);

    GridPane anchors = new GridPane();
    anchors.setHgap(2);
    anchors.setVgap(2);
    for (int r = 0; r < 3; r++) {
      for (int c = 0; c < 3; c++) {
        Button a = new Button();
        a.getStyleClass().add("wallpapers-anchor");
        a.setMinSize(18, 18);
        a.setPrefSize(18, 18);
        int col = c;
        int row = r;
        a.setAccessibleText(m.format("ui.wallpapers.anchor", c + 1, r + 1));
        a.setOnAction(e -> vm.anchor(col, row));
        anchors.add(a, c, r);
      }
    }
    CheckBox zones = new CheckBox(m.get("ui.wallpapers.safeZone"));
    zones.selectedProperty().bindBidirectional(vm.safeZonesProperty());
    Label zoneNote = new Label(m.get("ui.wallpapers.safeZone.note"));
    zoneNote.getStyleClass().add("hint");
    zoneNote.setWrapText(true);
    HBox anchorRow = new HBox(10, anchors, new VBox(4, zones, zoneNote));

    VBox box =
        new VBox(
            8,
            name,
            info,
            warnings,
            label(m.get("ui.wallpapers.fit")),
            fit,
            fillBox,
            actions,
            anchorRow);
    box.visibleProperty().bind(vm.currentTitleProperty().isNotEmpty());
    box.managedProperty().bind(box.visibleProperty());
    return box;
  }

  private static Node none(WallpapersViewModel<?> vm, Messages m) {
    Label l = new Label(m.get("ui.wallpapers.none"));
    l.getStyleClass().add("hint");
    l.setWrapText(true);
    l.visibleProperty().bind(vm.currentTitleProperty().isEmpty());
    l.managedProperty().bind(l.visibleProperty());
    return l;
  }

  private static List<Label> warningLabels(List<String> texts) {
    return texts.stream()
        .map(
            t -> {
              Label l = new Label(t, Icons.icon("fas-exclamation-triangle"));
              l.getStyleClass().add("enhance-limit");
              l.setWrapText(true);
              return l;
            })
        .toList();
  }

  private static Label label(String text) {
    Label l = new Label(text);
    l.getStyleClass().add("control-label");
    return l;
  }

  private static Node suggestion(WallpapersViewModel<?> vm) {
    Label text = text(vm.suggestionProperty());
    Button run = new Button();
    run.textProperty().bind(vm.suggestionActionProperty());
    run.setOnAction(e -> vm.enhanceFirst());
    run.setMaxWidth(Double.MAX_VALUE);
    VBox box = new VBox(6, text, run);
    box.getStyleClass().add("wallpapers-suggestion");
    box.visibleProperty().bind(vm.suggestionProperty().isNotEmpty());
    box.managedProperty().bind(box.visibleProperty());
    return box;
  }

  private static Label text(ObservableStringValue value) {
    Label l = new Label();
    l.setWrapText(true);
    l.textProperty().bind(value);
    return l;
  }

  private static Node exportRow(WallpapersViewModel<?> vm, Messages m) {
    Button export = new Button();
    export.getStyleClass().add(Styles.ACCENT);
    export.setMaxWidth(Double.MAX_VALUE);
    export.textProperty().bind(vm.exportLabelProperty());
    export.disableProperty().bind(vm.canExportProperty().not());
    export.setOnAction(e -> vm.showExport());
    export.visibleProperty().bind(vm.exportingProperty().not());
    export.managedProperty().bind(export.visibleProperty());

    ProgressBar bar = new ProgressBar();
    bar.setMaxWidth(Double.MAX_VALUE);
    bar.progressProperty().bind(vm.progressProperty());
    Label detail = new Label();
    detail.getStyleClass().add("hint");
    detail.textProperty().bind(vm.progressTextProperty());
    detail.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(detail, Priority.ALWAYS);
    Button cancel = new Button(m.get("ui.action.cancel"));
    cancel.setOnAction(e -> vm.cancelExport());
    HBox line = new HBox(8, detail, cancel);
    line.setAlignment(Pos.CENTER_LEFT);
    VBox progress = new VBox(6, bar, line);
    progress.visibleProperty().bind(vm.exportingProperty());
    progress.managedProperty().bind(progress.visibleProperty());
    return new VBox(6, export, progress);
  }

  /** Tira inferior con todos los destinos elegidos. */
  public static Node strip(
      WallpapersViewModel<? extends Image> vm, Messages m, Subscriptions subs) {
    HBox items = new HBox(10);
    items.setPadding(new Insets(8, 12, 8, 12));
    items.setAlignment(Pos.CENTER_LEFT);
    Runnable refresh =
        () -> items.getChildren().setAll(vm.thumbs().stream().map(t -> thumb(vm, m, t)).toList());
    vm.thumbs().addListener((ListChangeListener<Thumb<? extends Image>>) c -> refresh.run());
    ScrollPane scroll = new ScrollPane(items);
    scroll.setFitToHeight(true);
    scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.getStyleClass().add("wallpapers-strip");
    scroll.setPrefHeight(150);
    scroll.setMinHeight(150);
    return scroll;
  }

  private static Node thumb(
      WallpapersViewModel<? extends Image> vm, Messages m, Thumb<? extends Image> t) {
    ImageView view = new ImageView(t.image());
    view.setPreserveRatio(true);
    view.setFitHeight(96);
    view.setFitWidth(120);
    StackPane frame = new StackPane(view);
    frame.setPrefSize(124, 100);
    frame.getStyleClass().add("wallpapers-thumb");
    if (t.current()) {
      frame.getStyleClass().add("current");
    }
    Label badge = new Label(badgeText(m, t.badge()));
    badge
        .getStyleClass()
        .addAll("wallpapers-badge", "badge-" + t.badge().name().toLowerCase(java.util.Locale.ROOT));
    if (t.filled()) {
      badge.setText(badge.getText() + " · " + m.get("ui.wallpapers.badge.filled"));
    }
    if (t.warning()) {
      badge.setGraphic(Icons.icon("fas-exclamation-triangle"));
    }
    Label name = new Label(t.label());
    name.getStyleClass().add("hint");
    name.setMaxWidth(124);
    VBox box = new VBox(4, frame, name, badge);
    box.setAlignment(Pos.TOP_CENTER);
    box.setOnMouseClicked(e -> vm.select(t.id()));
    box.setAccessibleText(t.label());
    return box;
  }

  private static String badgeText(Messages m, QualityBadge b) {
    return switch (b) {
      case EXCELLENT -> m.get("ui.wallpapers.badge.excellent");
      case ACCEPTABLE -> m.get("ui.wallpapers.badge.acceptable");
      case BLURRY -> m.get("ui.wallpapers.badge.blurry");
    };
  }
}
