package io.github.erenriquezp.rora.app.shell;

import atlantafx.base.theme.Styles;
import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.model.ViewTransform;
import io.github.erenriquezp.rora.app.session.FramePool;
import io.github.erenriquezp.rora.app.theme.Theme;
import io.github.erenriquezp.rora.app.ui.WindowBar;
import io.github.erenriquezp.rora.app.ui.WindowResizer;
import io.github.erenriquezp.rora.app.view.DrawerViews;
import io.github.erenriquezp.rora.app.view.EnhanceView;
import io.github.erenriquezp.rora.app.view.ExportDialog;
import io.github.erenriquezp.rora.app.view.InspectorView;
import io.github.erenriquezp.rora.app.view.IoProgressView;
import io.github.erenriquezp.rora.app.view.NotificationsView;
import io.github.erenriquezp.rora.app.view.Subscriptions;
import io.github.erenriquezp.rora.app.view.ViewerView;
import io.github.erenriquezp.rora.app.viewmodel.ExportPrefs;
import io.github.erenriquezp.rora.app.viewmodel.Prompt;
import io.github.erenriquezp.rora.app.viewmodel.StudioViewModel;
import io.github.erenriquezp.rora.app.viewmodel.StudioViewModel.Tool;
import io.github.erenriquezp.rora.app.viewmodel.ViewerViewModel.CompareMode;
import io.github.erenriquezp.rora.app.viewmodel.ViewerViewModel.PixelReadout;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

/** Ventana principal: barra superior, riel, cajón, visor, inspector y barra de estado. */
final class MainWindow {

  /** Por debajo de este ancho el cajón flota sobre el visor y el modo de comparar se pliega. */
  static final double COMPACT_WIDTH = 1280;

  static final double MIN_WIDTH = 1100;
  static final double MIN_HEIGHT = 700;

  private static final String KEY_INSPECTOR = "inspector.width";
  private static final String KEY_DIVIDER = "inspector.divider";
  private static final String KEY_FOLDER = "last.folder";
  private static final String KEY_COMPARE = "compare.mode";
  private static final String KEY_X = "window.x";
  private static final String KEY_Y = "window.y";
  private static final String KEY_W = "window.w";
  private static final String KEY_H = "window.h";
  private static final double INSPECTOR_MIN = 280;
  private static final double INSPECTOR_MAX = 420;

  private final StudioViewModel<WritableImage> vm;
  private final Messages m;
  private final Stage stage;
  private final LayoutStore layout;
  private final Runnable openLab;
  private final Subscriptions subs = new Subscriptions();
  private final BorderPane root = new BorderPane();
  private final StackPane drawerHost = new StackPane();
  private final HBox compareSegmented = new HBox();
  private final ComboBox<CompareMode> compareCombo = new ComboBox<>();
  private final Label fileName = new Label();
  private InspectorView inspector;
  private ViewerView viewer;
  private BorderPane workArea;
  private StackPane centerStack;
  private boolean drawerFloating;
  private Stage exportStage;
  private Stage promptStage;

  MainWindow(StudioViewModel<WritableImage> vm, Stage stage, LayoutStore layout, Runnable openLab) {
    this.openLab = openLab;
    this.vm = vm;
    this.m = vm.messages();
    this.stage = stage;
    this.layout = layout;
  }

  Scene build(
      FramePool<WritableImage> afterPool,
      FramePool<WritableImage> beforePool,
      FramePool<WritableImage> overlayPool) {
    viewer =
        new ViewerView(
            vm.viewer(),
            vm.histogram(),
            vm.crop(),
            afterPool,
            beforePool,
            overlayPool,
            m,
            this::chooseAndOpen,
            subs);

    inspector = new InspectorView(vm, subs);
    double width = layout.getDouble(KEY_INSPECTOR, 300);
    inspector.setPrefWidth(clampInspector(width));
    inspector.setMinWidth(INSPECTOR_MIN);
    inspector.setMaxWidth(INSPECTOR_MAX);
    inspector.split().setDividerPositions(layout.getDouble(KEY_DIVIDER, 0.68));

    VBox catalog = DrawerViews.catalog(vm.catalog(), m, subs);
    VBox crop = DrawerViews.crop(vm.crop(), m, subs);
    VBox enhance = EnhanceView.build(vm.enhance(), m, subs);
    drawerHost.getChildren().addAll(catalog, crop, enhance);
    drawerHost.setMaxWidth(Region.USE_PREF_SIZE);
    subs.add(
        vm.toolProperty()
            .subscribe(
                tool -> {
                  catalog.setVisible(tool == Tool.FILTERS);
                  crop.setVisible(tool == Tool.CROP);
                  enhance.setVisible(tool == Tool.ENHANCE);
                  updateDrawer();
                }));

    NotificationsView notices = new NotificationsView(vm.notifications(), m);
    Node progress = new IoProgressView(vm.ioProgress(), m);
    centerStack = new StackPane(viewer, notices, progress);
    StackPane.setAlignment(notices, Pos.TOP_RIGHT);
    StackPane.setAlignment(progress, Pos.BOTTOM_CENTER);
    centerStack.setMinSize(0, 0);

    workArea = new BorderPane();
    workArea.setCenter(centerStack);
    workArea.setLeft(toolRail());
    workArea.setRight(withResizeHandle(inspector));

    root.getStyleClass().add("studio-root");
    VBox top = new VBox(topBar());
    boolean customChrome = StudioApp.usesCustomChrome();
    WindowBar windowBar = null;
    if (customChrome) {
      windowBar = new WindowBar(stage, m.get("ui.app.title"));
      top.getChildren().add(0, windowBar.getView());
    }
    root.setTop(top);
    root.setCenter(workArea);
    root.setBottom(statusBar());

    Scene scene = new Scene(root, layout.getDouble(KEY_W, 1400), layout.getDouble(KEY_H, 860));
    if (customChrome) {
      WindowBar chrome = windowBar;
      WindowResizer.install(
          stage, scene, MIN_WIDTH, MIN_HEIGHT, () -> chrome.maximizedProperty().get());
    }
    stage.setMinWidth(MIN_WIDTH);
    stage.setMinHeight(MIN_HEIGHT);
    restoreWindowPosition();
    installDragAndDrop(scene);
    installShortcuts(scene);
    installDialogs();
    stage.setOnCloseRequest(
        e -> {
          if (!vm.requestClose(stage::close)) {
            e.consume();
          }
        });
    scene.widthProperty().addListener((o, a, b) -> onWidthChanged(b.doubleValue()));
    onWidthChanged(scene.getWidth());
    return scene;
  }

  private void restoreWindowPosition() {
    double x = layout.getDouble(KEY_X, Double.NaN);
    double y = layout.getDouble(KEY_Y, Double.NaN);
    boolean onScreen =
        !Double.isNaN(x) && !javafx.stage.Screen.getScreensForRectangle(x, y, 80, 40).isEmpty();
    if (onScreen) {
      stage.setX(x);
      stage.setY(y);
    }
  }

  private static double clampInspector(double w) {
    return Math.max(INSPECTOR_MIN, Math.min(INSPECTOR_MAX, w));
  }

  /** Inspector con un asa de 5 px a la izquierda para cambiar su anchura. */
  private Node withResizeHandle(Region panel) {
    Region handle = new Region();
    handle.setPrefWidth(5);
    handle.setMinWidth(5);
    handle.setCursor(javafx.scene.Cursor.H_RESIZE);
    handle.setOnMouseDragged(
        e -> panel.setPrefWidth(clampInspector(panel.getPrefWidth() - e.getX())));
    return new HBox(handle, panel);
  }

  private void updateDrawer() {
    Tool tool = vm.toolProperty().get();
    boolean open = tool != Tool.ADJUST;
    if (workArea == null) {
      return;
    }
    Node left = workArea.getLeft();
    if (!(left instanceof HBox rail)) {
      return;
    }
    centerStack.getChildren().remove(drawerHost);
    rail.getChildren().remove(drawerHost);
    if (open) {
      if (drawerFloating) {
        StackPane.setAlignment(drawerHost, Pos.CENTER_LEFT);
        centerStack.getChildren().add(1, drawerHost);
      } else {
        rail.getChildren().add(drawerHost);
      }
    }
  }

  private void onWidthChanged(double width) {
    boolean compact = width < COMPACT_WIDTH;
    if (compact != drawerFloating) {
      drawerFloating = compact;
      updateDrawer();
    }
    compareSegmented.setVisible(!compact);
    compareSegmented.setManaged(!compact);
    compareCombo.setVisible(compact);
    compareCombo.setManaged(compact);
    fileName.setVisible(!compact);
    fileName.setManaged(!compact);
  }

  private Node topBar() {
    Button open = textButton("ui.action.open", "fas-folder-open");
    open.setOnAction(e -> chooseAndOpen());
    Button export = textButton("ui.action.export", "fas-file-export");
    export.getStyleClass().add(Styles.ACCENT);
    export.setOnAction(e -> vm.showExport());
    export.disableProperty().bind(vm.hasImageProperty().not());

    Button undo = iconButton("fas-undo", "ui.action.undo");
    undo.setOnAction(e -> vm.undo());
    undo.disableProperty().bind(vm.canUndoProperty().not());
    Button redo = iconButton("fas-redo", "ui.action.redo");
    redo.setOnAction(e -> vm.redo());
    redo.disableProperty().bind(vm.canRedoProperty().not());
    Button reset = textButton("ui.action.reset", "fas-eraser");
    reset.setTooltip(new Tooltip(m.get("ui.action.reset.tooltip")));
    reset.setOnAction(e -> vm.reset());
    reset.disableProperty().bind(vm.hasImageProperty().not());

    fileName.getStyleClass().add("file-name");
    subs.add(vm.fileNameProperty().subscribe(fileName::setText));
    Region left = new Region();
    Region right = new Region();
    HBox.setHgrow(left, Priority.ALWAYS);
    HBox.setHgrow(right, Priority.ALWAYS);

    buildCompare();

    Button fit = new Button(m.get("ui.zoom.fit"));
    fit.setOnAction(e -> vm.viewer().fit());
    Button actual = new Button(m.get("ui.zoom.actual"));
    actual.setOnAction(e -> vm.viewer().actualSize());
    Button out = iconButton("fas-minus", "ui.zoom.out");
    out.setOnAction(e -> vm.viewer().zoomOut());
    Button in = iconButton("fas-plus", "ui.zoom.in");
    in.setOnAction(e -> vm.viewer().zoomIn());
    Label zoom = new Label();
    zoom.getStyleClass().add("zoom-text");
    subs.add(vm.viewer().transformProperty().subscribe(t -> zoom.setText(zoomText(t))));

    for (Node n : new Node[] {compareSegmented, compareCombo, fit, actual, out, in}) {
      n.disableProperty().bind(vm.hasImageProperty().not());
    }

    HBox bar =
        new HBox(
            open,
            export,
            separator(),
            undo,
            redo,
            reset,
            left,
            fileName,
            right,
            compareSegmented,
            compareCombo,
            separator(),
            fit,
            actual,
            out,
            zoom,
            in);
    bar.getStyleClass().add("top-bar");
    return bar;
  }

  private static String zoomText(ViewTransform t) {
    return t == null ? "" : Math.round(t.zoomPercent()) + " %";
  }

  private void buildCompare() {
    ToggleGroup group = new ToggleGroup();
    CompareMode[] modes = CompareMode.values();
    String[] keys = {
      "ui.compare.result", "ui.compare.original", "ui.compare.curtain", "ui.compare.sideBySide"
    };
    for (int i = 0; i < modes.length; i++) {
      ToggleButton b = new ToggleButton(m.get(keys[i]));
      b.setUserData(modes[i]);
      b.setToggleGroup(group);
      b.getStyleClass()
          .add(
              i == 0
                  ? Styles.LEFT_PILL
                  : i == modes.length - 1 ? Styles.RIGHT_PILL : Styles.CENTER_PILL);
      compareSegmented.getChildren().add(b);
    }
    group
        .selectedToggleProperty()
        .addListener(
            (o, was, now) -> {
              if (now == null) {
                group.selectToggle(was);
              } else {
                vm.viewer().compareModeProperty().set((CompareMode) now.getUserData());
              }
            });
    compareCombo.getItems().setAll(modes);
    compareCombo.setConverter(
        new javafx.util.StringConverter<>() {
          @Override
          public String toString(CompareMode mode) {
            return mode == null ? "" : m.get(keys[mode.ordinal()]);
          }

          @Override
          public CompareMode fromString(String s) {
            return null;
          }
        });
    compareCombo.valueProperty().bindBidirectional(vm.viewer().compareModeProperty());
    subs.add(
        vm.viewer()
            .compareModeProperty()
            .subscribe(
                mode -> {
                  group.getToggles().stream()
                      .filter(t -> t.getUserData() == mode)
                      .findFirst()
                      .ifPresent(group::selectToggle);
                  layout.putString(KEY_COMPARE, mode.name());
                }));
    try {
      vm.viewer()
          .compareModeProperty()
          .set(CompareMode.valueOf(layout.getString(KEY_COMPARE, CompareMode.RESULT.name())));
    } catch (IllegalArgumentException e) {
      vm.viewer().compareModeProperty().set(CompareMode.RESULT);
    }
  }

  private Node toolRail() {
    ToggleGroup group = new ToggleGroup();
    VBox rail =
        new VBox(
            toolButton(group, Tool.ADJUST, "ui.tool.adjust", "fas-sliders-h"),
            toolButton(group, Tool.FILTERS, "ui.tool.filters", "fas-magic"),
            toolButton(group, Tool.CROP, "ui.tool.crop", "fas-crop-alt"),
            toolButton(group, Tool.ENHANCE, "ui.tool.enhance", "fas-expand-arrows-alt"));
    rail.getStyleClass().add("tool-rail");
    if (openLab != null) {
      Button lab = new Button(m.get("ui.tool.lab"), new FontIcon("fas-flask"));
      lab.getStyleClass().add("tool-button");
      lab.setOnAction(e -> openLab.run());
      rail.getChildren().add(lab);
    }
    group
        .selectedToggleProperty()
        .addListener(
            (o, was, now) -> {
              if (now == null) {
                // Pulsar la herramienta activa cierra su cajón y vuelve a Ajustes.
                if (was.getUserData() == Tool.ADJUST) {
                  group.selectToggle(was);
                  vm.stack().clearSelection();
                } else {
                  vm.toolProperty().set(Tool.ADJUST);
                }
              } else {
                vm.toolProperty().set((Tool) now.getUserData());
              }
            });
    subs.add(
        vm.toolProperty()
            .subscribe(
                t ->
                    group.getToggles().stream()
                        .filter(b -> b.getUserData() == t)
                        .findFirst()
                        .ifPresent(group::selectToggle)));
    return new HBox(rail);
  }

  private ToggleButton toolButton(ToggleGroup group, Tool tool, String key, String icon) {
    ToggleButton b = new ToggleButton(m.get(key), new FontIcon(icon));
    b.setUserData(tool);
    b.setToggleGroup(group);
    b.getStyleClass().add("tool-button");
    if (tool == Tool.ENHANCE) {
      b.disableProperty().bind(vm.hasImageProperty().not().or(vm.enhance().availableProperty().not()));
    } else if (tool != Tool.ADJUST) {
      b.disableProperty().bind(vm.hasImageProperty().not());
    }
    return b;
  }

  private Node statusBar() {
    Label status = new Label();
    subs.add(vm.statusProperty().subscribe(status::setText));
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    Region swatch = new Region();
    swatch.getStyleClass().add("status-swatch");
    Label pixel = new Label();
    Consumer<PixelReadout> update =
        r -> {
          swatch.setVisible(r != null);
          pixel.setText(r == null ? "" : readoutText(r));
          if (r != null) {
            swatch.setStyle(
                String.format(Locale.ROOT, "-fx-background-color: #%06x;", r.argb() & 0xFFFFFF));
          }
        };
    subs.add(vm.viewer().readoutProperty().subscribe(update::accept));
    HBox readout = new HBox(6, swatch, pixel);
    readout.setAlignment(Pos.CENTER_LEFT);
    Label format = new Label();
    Label size = new Label();
    subs.add(vm.formatProperty().subscribe(format::setText));
    subs.add(vm.sizeProperty().subscribe(size::setText));
    HBox bar = new HBox(status, spacer, readout, format, size);
    bar.getStyleClass().add("status-bar");
    return bar;
  }

  private String readoutText(PixelReadout r) {
    return m.format(
        "ui.status.pixel",
        r.approximate() ? m.get("ui.status.approximate.prefix") : "",
        Integer.toString(r.x()),
        Integer.toString(r.y()),
        (r.argb() >> 16) & 0xFF,
        (r.argb() >> 8) & 0xFF,
        r.argb() & 0xFF);
  }

  private Button textButton(String key, String icon) {
    Button b = new Button(m.get(key), new FontIcon(icon));
    b.setGraphicTextGap(6);
    return b;
  }

  private Button iconButton(String icon, String tooltipKey) {
    Button b = new Button();
    b.setGraphic(new FontIcon(icon));
    b.getStyleClass().add(Styles.BUTTON_ICON);
    b.setTooltip(new Tooltip(m.get(tooltipKey)));
    b.setAccessibleText(m.get(tooltipKey));
    return b;
  }

  private static Separator separator() {
    Separator s = new Separator(Orientation.VERTICAL);
    s.setMaxHeight(24);
    return s;
  }

  void chooseAndOpen() {
    FileChooser chooser = new FileChooser();
    chooser.setTitle(m.get("ui.action.open"));
    chooser
        .getExtensionFilters()
        .add(
            new FileChooser.ExtensionFilter(
                m.get("ui.empty.formats"),
                "*.jpg",
                "*.jpeg",
                "*.png",
                "*.tif",
                "*.tiff",
                "*.webp",
                "*.bmp",
                "*.gif"));
    File folder = new File(layout.getString(KEY_FOLDER, ""));
    if (folder.isDirectory()) {
      chooser.setInitialDirectory(folder);
    }
    File file = chooser.showOpenDialog(stage);
    if (file != null) {
      layout.putString(KEY_FOLDER, file.getParent());
      vm.requestOpen(file);
    }
  }

  private void installDialogs() {
    vm.exportDialog()
        .usePrefs(
            new ExportPrefs() {
              @Override
              public String get(String key, String fallback) {
                return layout.getString(key, fallback);
              }

              @Override
              public void put(String key, String value) {
                layout.putString(key, value);
              }
            });
    vm.setRevealer(MainWindow::revealInFolder);
    subs.add(
        vm.exportDialog()
            .visibleProperty()
            .subscribe(
                visible -> {
                  if (visible) {
                    showExportDialog();
                  } else if (exportStage != null) {
                    exportStage.hide();
                  }
                }));
    subs.add(vm.promptProperty().subscribe(this::showPrompt));
  }

  private void showExportDialog() {
    if (exportStage == null) {
      exportStage = new Stage();
      exportStage.initOwner(stage);
      exportStage.initModality(Modality.WINDOW_MODAL);
      exportStage.setTitle(m.get("ui.export.title"));
      Scene s =
          new Scene(new ExportDialog(vm.exportDialog(), m).build(() -> exportStage), 640, 720);
      Theme.apply(s);
      exportStage.setScene(s);
      exportStage.setMinWidth(480);
      exportStage.setMinHeight(420);
      exportStage.setOnCloseRequest(e -> vm.exportDialog().close());
    }
    exportStage.show();
    exportStage.getScene().getRoot().requestFocus();
  }

  private void showPrompt(Prompt prompt) {
    if (promptStage != null) {
      promptStage.hide();
      promptStage = null;
    }
    if (prompt == null) {
      return;
    }
    Stage dialog = new Stage();
    dialog.initOwner(stage);
    dialog.initModality(Modality.WINDOW_MODAL);
    dialog.setTitle(prompt.title());
    Label body = new Label(prompt.body());
    body.setWrapText(true);
    body.getStyleClass().add("prompt-body");
    HBox buttons = new HBox(10);
    buttons.setAlignment(Pos.CENTER_RIGHT);
    for (Prompt.Choice c : prompt.choices()) {
      Button b = new Button(c.label());
      if (c.primary()) {
        b.getStyleClass().add(Styles.ACCENT);
        b.setDefaultButton(true);
      }
      b.setOnAction(e -> c.action().run());
      // Los textos largos ensanchan el diálogo en vez de cortarse.
      b.setMinWidth(Region.USE_PREF_SIZE);
      buttons.getChildren().add(b);
    }
    body.setPrefWidth(440);
    VBox box = new VBox(20, body, buttons);
    box.getStyleClass().add("prompt");
    box.setMinWidth(480);
    Scene s = new Scene(box);
    Theme.apply(s);
    dialog.setScene(s);
    dialog.setResizable(false);
    // Cerrar con la X equivale a la última opción, la de cancelar.
    dialog.setOnCloseRequest(
        e -> prompt.choices().get(prompt.choices().size() - 1).action().run());
    promptStage = dialog;
    dialog.show();
  }

  /** Muestra el archivo en su carpeta, fuera del hilo de la interfaz. */
  private static void revealInFolder(Path file) {
    Thread t =
        new Thread(
            () -> {
              try {
                if (StudioApp.usesCustomChrome()) {
                  new ProcessBuilder("explorer.exe", "/select,", file.toString()).start();
                } else if (Desktop.isDesktopSupported()) {
                  Desktop.getDesktop().open(file.getParent().toFile());
                }
              } catch (IOException | RuntimeException e) {
                System.getLogger("rora.app")
                    .log(System.Logger.Level.WARNING, "No se pudo mostrar la carpeta", e);
              }
            },
            "rora-reveal");
    t.setDaemon(true);
    t.start();
  }

  private void installDragAndDrop(Scene scene) {
    scene.setOnDragOver(
        e -> {
          if (e.getDragboard().hasFiles()) {
            e.acceptTransferModes(TransferMode.COPY);
            if (!viewer.dropZone().getStyleClass().contains("drop-zone-active")) {
              viewer.dropZone().getStyleClass().add("drop-zone-active");
            }
          }
          e.consume();
        });
    scene.setOnDragExited(e -> viewer.dropZone().getStyleClass().remove("drop-zone-active"));
    scene.setOnDragDropped(
        e -> {
          boolean done = e.getDragboard().hasFiles() && !e.getDragboard().getFiles().isEmpty();
          if (done) {
            File dropped = e.getDragboard().getFiles().get(0);
            // Tras terminar el arrastre: una pregunta modal dentro del evento lo dejaría a medias.
            Platform.runLater(() -> vm.requestOpen(dropped));
          }
          viewer.dropZone().getStyleClass().remove("drop-zone-active");
          e.setDropCompleted(done);
          e.consume();
        });
  }

  private void installShortcuts(Scene scene) {
    var a = scene.getAccelerators();
    a.put(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), this::chooseAndOpen);
    a.put(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), vm::showExport);
    a.put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), vm::undo);
    a.put(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN), vm::redo);
    a.put(
        new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
        vm::redo);

    scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
    scene.addEventFilter(
        KeyEvent.KEY_RELEASED,
        e -> {
          if (isHoldOriginal(e)) {
            vm.viewer().holdOriginalProperty().set(false);
          }
        });
  }

  private static boolean isHoldOriginal(KeyEvent e) {
    return e.getCode() == KeyCode.BACK_SLASH || e.getCode() == KeyCode.O;
  }

  private void onKeyPressed(KeyEvent e) {
    if (e.isShortcutDown()
        || e.isAltDown()
        || root.getScene().getFocusOwner() instanceof TextInputControl) {
      return;
    }
    if (vm.toolProperty().get() == Tool.CROP) {
      if (e.getCode() == KeyCode.ENTER) {
        vm.crop().commit();
        vm.toolProperty().set(Tool.ADJUST);
        e.consume();
        return;
      }
      if (e.getCode() == KeyCode.ESCAPE) {
        vm.toolProperty().set(Tool.ADJUST);
        e.consume();
        return;
      }
    }
    if (!vm.hasImageProperty().get()) {
      return;
    }
    switch (e.getCode()) {
      case F -> vm.viewer().fit();
      case DIGIT1, NUMPAD1 -> vm.viewer().actualSize();
      case PLUS, ADD, EQUALS -> vm.viewer().zoomIn();
      case MINUS, SUBTRACT -> vm.viewer().zoomOut();
      default -> {
        if (isHoldOriginal(e)) {
          vm.viewer().holdOriginalProperty().set(true);
          e.consume();
        }
        return;
      }
    }
    e.consume();
  }

  /** Guarda la disposición al cerrar. */
  void saveLayout() {
    layout.putDouble(KEY_INSPECTOR, inspector.getPrefWidth());
    if (!inspector.split().getDividers().isEmpty()) {
      layout.putDouble(KEY_DIVIDER, inspector.split().getDividers().get(0).getPosition());
    }
    layout.putDouble(KEY_X, stage.getX());
    layout.putDouble(KEY_Y, stage.getY());
    layout.putDouble(KEY_W, root.getScene().getWidth());
    layout.putDouble(KEY_H, root.getScene().getHeight());
  }

  void close() {
    subs.close();
  }
}
