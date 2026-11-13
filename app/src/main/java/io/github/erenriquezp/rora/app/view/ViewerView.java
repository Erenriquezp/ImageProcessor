package io.github.erenriquezp.rora.app.view;

import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.model.CropModel;
import io.github.erenriquezp.rora.app.model.Curtain;
import io.github.erenriquezp.rora.app.model.FrameOverlayState;
import io.github.erenriquezp.rora.app.model.ViewTransform;
import io.github.erenriquezp.rora.app.session.EditSession;
import io.github.erenriquezp.rora.app.session.FramePool;
import io.github.erenriquezp.rora.app.viewmodel.FrameEditor;
import io.github.erenriquezp.rora.app.viewmodel.HistogramViewModel;
import io.github.erenriquezp.rora.app.viewmodel.ViewerViewModel;
import io.github.erenriquezp.rora.app.viewmodel.ViewerViewModel.CompareMode;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Visor único: zoom y pan compartidos, cortina arrastrable, lado a lado y recorte. Mover el ratón
 * solo cambia transformaciones y recortes de nodos; nunca crea imágenes.
 */
public final class ViewerView extends StackPane {

  private static final double SIDE_GAP = 8;

  private final ViewerViewModel<WritableImage> vm;
  private final java.util.List<? extends FrameEditor> editors;
  private final FramePool<WritableImage> afterPool;
  private final FramePool<WritableImage> beforePool;
  private final FramePool<WritableImage> overlayPool;

  private final Pane stage = new Pane();
  private final ImageView afterView = new ImageView();
  private final ImageView beforeView = new ImageView();
  private final ImageView overlayView = new ImageView();
  private final Region afterChecker = checker();
  private final Region beforeChecker = checker();
  private final Translate afterTranslate = new Translate();
  private final Translate beforeTranslate = new Translate();
  private final Scale afterScale = new Scale(1, 1, 0, 0);
  private final Scale beforeScale = new Scale(1, 1, 0, 0);
  private final Pane afterLayer = new Pane();
  private final Pane beforeLayer = new Pane();
  private final Rectangle afterClip = new Rectangle();
  private final Rectangle beforeClip = new Rectangle();

  private final Line curtainLine = new Line();
  private final StackPane curtainHandle = new StackPane(new FontIcon("fas-arrows-alt-h"));
  private final Label beforeLabel;
  private final Label afterLabel;
  private final FrameOverlay cropLayer = new FrameOverlay();
  private final VBox dropZone = new VBox();
  private final VBox clipLegend = new VBox();

  private boolean draggingCurtain;
  private CropModel.Handle draggingHandle;
  private double lastX;
  private double lastY;
  private double pressX;
  private double pressY;

  public ViewerView(
      ViewerViewModel<WritableImage> vm,
      HistogramViewModel<WritableImage> histogram,
      java.util.List<? extends FrameEditor> editors,
      FramePool<WritableImage> afterPool,
      FramePool<WritableImage> beforePool,
      FramePool<WritableImage> overlayPool,
      Messages m,
      Runnable onOpen,
      Subscriptions subs) {
    this.vm = vm;
    this.editors = editors;
    this.afterPool = afterPool;
    this.beforePool = beforePool;
    this.overlayPool = overlayPool;
    getStyleClass().add("viewer-stage");
    setMinSize(0, 0);

    for (ImageView v : new ImageView[] {afterView, beforeView, overlayView}) {
      v.setPreserveRatio(false);
      v.setMouseTransparent(true);
    }
    Group after = new Group(afterView, overlayView);
    after.getTransforms().addAll(afterTranslate, afterScale);
    Group before = new Group(beforeView);
    before.getTransforms().addAll(beforeTranslate, beforeScale);
    afterLayer.getChildren().addAll(afterChecker, after);
    beforeLayer.getChildren().addAll(beforeChecker, before);
    afterLayer.setClip(afterClip);
    beforeLayer.setClip(beforeClip);
    afterLayer.setMouseTransparent(true);
    beforeLayer.setMouseTransparent(true);

    curtainLine.getStyleClass().add("curtain-line");
    curtainLine.setMouseTransparent(true);
    curtainHandle.getStyleClass().add("curtain-handle");
    curtainHandle.setMouseTransparent(true);
    beforeLabel = badge(m.get("ui.compare.before"));
    afterLabel = badge(m.get("ui.compare.after"));

    stage
        .getChildren()
        .addAll(
            afterLayer, beforeLayer, curtainLine, curtainHandle, beforeLabel, afterLabel, cropLayer);
    stage.setFocusTraversable(true);
    stage.setAccessibleText(m.get("ui.compare.curtain"));

    buildDropZone(m, onOpen);
    clipLegend.getStyleClass().add("clip-legend");
    clipLegend.getChildren().addAll(
        new Label(m.get("ui.histogram.legend.highlights")),
        new Label(m.get("ui.histogram.legend.shadows")));
    clipLegend.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    clipLegend.setMouseTransparent(true);
    StackPane.setAlignment(clipLegend, Pos.BOTTOM_LEFT);
    StackPane.setMargin(clipLegend, new javafx.geometry.Insets(12));
    getChildren().addAll(stage, dropZone, clipLegend);

    stage.widthProperty().addListener((o, a, b) -> pushViewport());
    stage.heightProperty().addListener((o, a, b) -> pushViewport());
    sceneProperty()
        .addListener(
            (o, a, s) -> {
              if (s != null) {
                pushViewport();
              }
            });

    subs.add(vm.frameProperty().subscribe(this::showFrame));
    subs.add(vm.beforeFrameProperty().subscribe(this::showBefore));
    subs.add(vm.transformProperty().subscribe(t -> layoutView()));
    subs.add(vm.curtainProperty().subscribe(c -> layoutView()));
    subs.add(vm.effectiveModeBinding().subscribe(mode -> onModeChanged()));
    subs.add(
        histogram
            .overlayProperty()
            .subscribe(
                img -> {
                  overlayView.setImage(img);
                  if (img != null) {
                    overlayPool.displayed(img);
                  }
                  layoutView();
                }));
    subs.add(histogram.overlayEnabledProperty().subscribe(on -> layoutView()));
    for (FrameEditor e : editors) {
      subs.add(e.overlayProperty().subscribe(c -> drawCrop()));
    }

    stage.addEventHandler(ScrollEvent.SCROLL, this::onScroll);
    stage.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onPressed);
    stage.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onDragged);
    stage.addEventHandler(MouseEvent.MOUSE_RELEASED, this::onReleased);
    stage.addEventHandler(MouseEvent.MOUSE_MOVED, this::onMoved);
    stage.addEventHandler(MouseEvent.MOUSE_EXITED, e -> vm.clearHover());
    stage.addEventHandler(KeyEvent.KEY_PRESSED, this::onKey);
  }

  public Node dropZone() {
    return dropZone;
  }

  private static Label badge(String text) {
    Label l = new Label(text);
    l.getStyleClass().add("curtain-label");
    l.setMouseTransparent(true);
    return l;
  }

  private void buildDropZone(Messages m, Runnable onOpen) {
    FontIcon icon = new FontIcon("fas-image");
    icon.getStyleClass().add("drop-icon");
    Label title = new Label(m.get("ui.empty.title"));
    title.getStyleClass().add("empty-title");
    Label formats = new Label(m.get("ui.empty.formats"));
    formats.getStyleClass().add("empty-text");
    Button open = new Button(m.get("ui.empty.action"));
    open.getStyleClass().add(atlantafx.base.theme.Styles.ACCENT);
    open.setOnAction(e -> onOpen.run());
    Label shortcut = new Label(m.get("ui.empty.shortcut"));
    shortcut.getStyleClass().add("empty-text");
    dropZone.getChildren().addAll(icon, title, formats, open, shortcut);
    dropZone.getStyleClass().add("drop-zone");
    dropZone.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
  }

  private static Region checker() {
    int s = 8;
    int tile = s * 2;
    WritableImage img = new WritableImage(tile, tile);
    PixelWriter pw = img.getPixelWriter();
    Color light = Color.rgb(66, 66, 66);
    Color dark = Color.rgb(50, 50, 50);
    for (int y = 0; y < tile; y++) {
      for (int x = 0; x < tile; x++) {
        pw.setColor(x, y, (x < s) ^ (y < s) ? light : dark);
      }
    }
    Region r = new Region();
    r.setBackground(
        new Background(
            new BackgroundImage(
                img,
                BackgroundRepeat.REPEAT,
                BackgroundRepeat.REPEAT,
                BackgroundPosition.DEFAULT,
                BackgroundSize.DEFAULT)));
    r.setManaged(false);
    r.setVisible(false);
    return r;
  }

  private boolean sideBySide() {
    return vm.effectiveModeBinding().get() == CompareMode.SIDE_BY_SIDE;
  }

  private double offsetAfter() {
    return sideBySide() ? (stage.getWidth() + SIDE_GAP) / 2 : 0;
  }

  private void pushViewport() {
    double outputScale =
        getScene() != null && getScene().getWindow() != null
            ? getScene().getWindow().getOutputScaleX()
            : 1;
    double width = stage.getWidth();
    vm.setViewport(sideBySide() ? (width - SIDE_GAP) / 2 : width, stage.getHeight(), outputScale);
    layoutView();
  }

  private void onModeChanged() {
    pushViewport();
    layoutView();
  }

  private void showFrame(EditSession.Frame<WritableImage> f) {
    afterView.setImage(f == null ? null : f.image());
    dropZone.setVisible(f == null);
    dropZone.setManaged(f == null);
    if (f != null) {
      afterPool.displayed(f.image());
      afterView.setFitWidth(f.fullWidth());
      afterView.setFitHeight(f.fullHeight());
      overlayView.setFitWidth(f.fullWidth());
      overlayView.setFitHeight(f.fullHeight());
    }
    layoutView();
  }

  private void showBefore(EditSession.Frame<WritableImage> f) {
    beforeView.setImage(f == null ? null : f.image());
    if (f != null) {
      beforePool.displayed(f.image());
      beforeView.setFitWidth(f.fullWidth());
      beforeView.setFitHeight(f.fullHeight());
    }
    layoutView();
  }

  private void placeChecker(Region r, ViewTransform t, double offset, boolean show) {
    r.setVisible(show);
    if (show) {
      r.resizeRelocate(
          t.tx() + offset, t.ty(), t.imageWidth() * t.scale(), t.imageHeight() * t.scale());
    }
  }

  private void layoutView() {
    ViewTransform t = vm.transformProperty().get();
    CompareMode mode = vm.effectiveModeBinding().get();
    double w = stage.getWidth();
    double h = stage.getHeight();
    boolean hasFrame = t != null && vm.frameProperty().get() != null;
    boolean showAfter = hasFrame && mode != CompareMode.ORIGINAL;
    boolean showBefore = hasFrame && mode != CompareMode.RESULT;

    afterLayer.setVisible(showAfter);
    beforeLayer.setVisible(showBefore);
    curtainLine.setVisible(false);
    curtainHandle.setVisible(false);
    beforeLabel.setVisible(false);
    afterLabel.setVisible(false);
    clipLegend.setVisible(
        hasFrame && overlayView.getImage() != null && mode != CompareMode.ORIGINAL);
    overlayView.setVisible(overlayView.getImage() != null);
    if (!hasFrame) {
      return;
    }

    double offset = offsetAfter();
    apply(t, afterTranslate, afterScale, offset);
    apply(t, beforeTranslate, beforeScale, 0);
    placeChecker(afterChecker, t, offset, showAfter);
    placeChecker(beforeChecker, t, 0, showBefore);
    EditSession.Frame<WritableImage> frame = vm.frameProperty().get();
    afterView.setSmooth(frame.approximate() || !t.showsRealPixels());
    beforeView.setSmooth(!t.showsRealPixels());

    switch (mode) {
      case CURTAIN -> {
        double x = vm.curtainProperty().get().screenX(t);
        clip(afterClip, 0, w, h);
        clip(beforeClip, 0, x, h);
        placeCurtain(t, x, h);
      }
      case SIDE_BY_SIDE -> {
        double half = (w - SIDE_GAP) / 2;
        clip(beforeClip, 0, half, h);
        clip(afterClip, offset, half, h);
        double badgeY = Math.max(0, t.ty()) + 10;
        double badgeX = Math.max(0, t.tx()) + 10;
        placeBadge(beforeLabel, badgeX, badgeY);
        placeBadge(afterLabel, offset + badgeX, badgeY);
      }
      default -> {
        clip(afterClip, 0, w, h);
        clip(beforeClip, 0, w, h);
      }
    }
    drawCrop();
  }

  private static void apply(ViewTransform t, Translate tr, Scale s, double offset) {
    tr.setX(t.tx() + offset);
    tr.setY(t.ty());
    s.setX(t.scale());
    s.setY(t.scale());
  }

  private static void clip(Rectangle r, double x, double w, double h) {
    r.setX(x);
    r.setY(0);
    r.setWidth(Math.max(0, w));
    r.setHeight(Math.max(0, h));
  }

  private void placeCurtain(ViewTransform t, double x, double h) {
    double top = Math.max(0, t.ty());
    double bottom = Math.min(h, t.ty() + t.imageHeight() * t.scale());
    curtainLine.setStartX(x);
    curtainLine.setEndX(x);
    curtainLine.setStartY(top);
    curtainLine.setEndY(bottom);
    curtainLine.setVisible(true);
    curtainHandle.setVisible(true);
    curtainHandle.relocate(x - 16, (top + bottom) / 2 - 16);
    placeBadge(beforeLabel, Math.max(8, x - beforeLabel.prefWidth(-1) - 12), top + 10);
    placeBadge(afterLabel, x + 12, top + 10);
  }

  private static void placeBadge(Label l, double x, double y) {
    l.setVisible(true);
    l.relocate(x, y);
  }

  /** Herramienta de encuadre abierta (la primera con estado), o {@code null}. */
  private FrameEditor activeEditor() {
    for (FrameEditor e : editors) {
      if (e.overlayProperty().get() != null) {
        return e;
      }
    }
    return null;
  }

  private FrameOverlayState activeOverlay() {
    FrameEditor e = activeEditor();
    return e == null ? null : e.overlayProperty().get();
  }

  private void drawCrop() {
    cropLayer.draw(activeOverlay(), vm.transformProperty().get());
  }

  private CropModel.Handle hitCropHandle(double x, double y) {
    return FrameOverlay.hit(activeOverlay(), vm.transformProperty().get(), x, y);
  }

  private double localX(double x) {
    return sideBySide() && x > offsetAfter() ? x - offsetAfter() : x;
  }

  private void onScroll(ScrollEvent e) {
    vm.zoomAt(localX(e.getX()), e.getY(), e.getDeltaY() > 0);
    e.consume();
  }

  private void onPressed(MouseEvent e) {
    stage.requestFocus();
    lastX = e.getX();
    lastY = e.getY();
    pressX = e.getX();
    pressY = e.getY();
    ViewTransform t = vm.transformProperty().get();
    if (t == null) {
      return;
    }
    draggingHandle = hitCropHandle(e.getX(), e.getY());
    draggingCurtain =
        draggingHandle == null
            && vm.effectiveModeBinding().get() == CompareMode.CURTAIN
            && vm.curtainProperty().get().isGrabbed(e.getX(), t);
    if (draggingCurtain) {
      vm.curtainProperty().set(Curtain.atScreenX(e.getX(), t));
    }
  }

  private void onDragged(MouseEvent e) {
    ViewTransform t = vm.transformProperty().get();
    if (t == null) {
      return;
    }
    if (draggingHandle != null) {
      dragCrop(e, t);
    } else if (draggingCurtain) {
      vm.curtainProperty().set(Curtain.atScreenX(e.getX(), t));
    } else {
      vm.panBy(e.getX() - lastX, e.getY() - lastY);
    }
    lastX = e.getX();
    lastY = e.getY();
  }

  private void dragCrop(MouseEvent e, ViewTransform t) {
    FrameEditor editor = activeEditor();
    if (editor == null) {
      return;
    }
    CropModel c = editor.overlayProperty().get().frame();
    if (draggingHandle == CropModel.Handle.BODY) {
      editor.setFrame(
          c.movedBy(
              (e.getX() - lastX) / (t.imageWidth() * t.scale()),
              (e.getY() - lastY) / (t.imageHeight() * t.scale())));
    } else {
      ViewTransform.Point p = t.screenToImage(e.getX(), e.getY());
      editor.setFrame(c.dragged(draggingHandle, p.x() / t.imageWidth(), p.y() / t.imageHeight()));
    }
  }

  private void onReleased(MouseEvent e) {
    boolean still = Math.hypot(e.getX() - pressX, e.getY() - pressY) < 3;
    if (still && !draggingCurtain && draggingHandle == null) {
      vm.pick(localX(e.getX()), e.getY());
    }
    if (draggingHandle != null) {
      FrameEditor editor = activeEditor();
      if (editor != null) {
        editor.frameGestureEnded();
      }
    }
    draggingCurtain = false;
    draggingHandle = null;
  }

  private void onMoved(MouseEvent e) {
    vm.hover(localX(e.getX()), e.getY());
    stage.setCursor(cursorAt(e.getX(), e.getY()));
  }

  private Cursor cursorAt(double x, double y) {
    ViewTransform t = vm.transformProperty().get();
    if (t == null) {
      return Cursor.DEFAULT;
    }
    CropModel.Handle handle = hitCropHandle(x, y);
    if (handle != null) {
      return switch (handle) {
        case NW -> Cursor.NW_RESIZE;
        case NE -> Cursor.NE_RESIZE;
        case SE -> Cursor.SE_RESIZE;
        case SW -> Cursor.SW_RESIZE;
        case N, S -> Cursor.V_RESIZE;
        case E, W -> Cursor.H_RESIZE;
        case BODY -> Cursor.MOVE;
      };
    }
    boolean onCurtain =
        vm.effectiveModeBinding().get() == CompareMode.CURTAIN
            && vm.curtainProperty().get().isGrabbed(x, t);
    return onCurtain ? Cursor.H_RESIZE : Cursor.DEFAULT;
  }

  private void onKey(KeyEvent e) {
    if (vm.effectiveModeBinding().get() != CompareMode.CURTAIN) {
      return;
    }
    switch (e.getCode()) {
      case LEFT -> {
        vm.curtainProperty().set(vm.curtainProperty().get().nudged(-1, e.isShiftDown()));
        e.consume();
      }
      case RIGHT -> {
        vm.curtainProperty().set(vm.curtainProperty().get().nudged(1, e.isShiftDown()));
        e.consume();
      }
      default -> {}
    }
  }
}
