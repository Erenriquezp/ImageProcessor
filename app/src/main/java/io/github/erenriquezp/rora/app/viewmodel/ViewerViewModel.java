package io.github.erenriquezp.rora.app.viewmodel;

import io.github.erenriquezp.rora.app.model.Curtain;
import io.github.erenriquezp.rora.app.model.ViewTransform;
import io.github.erenriquezp.rora.app.session.EditSession;
import io.github.erenriquezp.rora.app.session.Scheduler;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.OpSpec;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

/** Estado del visor: imágenes, zoom y pan únicos, comparación y cuentagotas. */
public final class ViewerViewModel<F> {

  /** Cómo se compara con la imagen original. */
  public enum CompareMode {
    RESULT,
    ORIGINAL,
    CURTAIN,
    SIDE_BY_SIDE
  }

  /** Píxel bajo el cursor. */
  public record PixelReadout(int x, int y, int argb, boolean approximate) {}

  private static final long VIEWPORT_DEBOUNCE_NANOS = 150_000_000L;
  private static final double ZOOM_STEP = 1.25;

  private final Env<F> env;
  private final Function<Img, F> beforeConverter;
  private final Consumer<String> onFailure;
  private final ObjectProperty<EditSession.Frame<F>> frame = new SimpleObjectProperty<>();
  private final ObjectProperty<EditSession.Frame<F>> beforeFrame = new SimpleObjectProperty<>();
  private final ObjectProperty<ViewTransform> transform = new SimpleObjectProperty<>();
  private final ObjectProperty<Curtain> curtain = new SimpleObjectProperty<>(Curtain.CENTER);
  private final ObjectProperty<CompareMode> compareMode =
      new SimpleObjectProperty<>(CompareMode.RESULT);
  private final BooleanProperty holdOriginal = new SimpleBooleanProperty(false);
  private final ObjectProperty<PixelReadout> readout = new SimpleObjectProperty<>();
  private final ObjectBinding<CompareMode> effectiveMode;
  private double viewportWidth = 1;
  private double viewportHeight = 1;
  private double dpi = 1;
  private Scheduler.Cancellable pendingViewport;
  private String beforeKey = "";

  ViewerViewModel(Env<F> env, Function<Img, F> beforeConverter, Consumer<String> onFailure) {
    this.env = env;
    this.beforeConverter = beforeConverter;
    this.onFailure = onFailure;
    this.effectiveMode =
        Bindings.createObjectBinding(
            () -> holdOriginal.get() ? CompareMode.ORIGINAL : compareMode.get(),
            holdOriginal,
            compareMode);
    effectiveMode.addListener((obs, was, now) -> requestBeforeIfNeeded());
  }

  public ObjectProperty<EditSession.Frame<F>> frameProperty() {
    return frame;
  }

  public ObjectProperty<EditSession.Frame<F>> beforeFrameProperty() {
    return beforeFrame;
  }

  public ObjectProperty<ViewTransform> transformProperty() {
    return transform;
  }

  public ObjectProperty<Curtain> curtainProperty() {
    return curtain;
  }

  public ObjectProperty<CompareMode> compareModeProperty() {
    return compareMode;
  }

  public BooleanProperty holdOriginalProperty() {
    return holdOriginal;
  }

  public ObjectBinding<CompareMode> effectiveModeBinding() {
    return effectiveMode;
  }

  public ObjectProperty<PixelReadout> readoutProperty() {
    return readout;
  }

  /** Tamaño del área de imagen: con lado a lado, la mitad del visor. */
  public void setViewport(double width, double height, double outputScale) {
    viewportWidth = Math.max(1, width);
    viewportHeight = Math.max(1, height);
    dpi = outputScale > 0 ? outputScale : 1;
    ViewTransform t = transform.get();
    if (t != null) {
      transform.set(t.withViewport(viewportWidth, viewportHeight, dpi));
    }
    pushViewportLater();
  }

  public void zoomAt(double x, double y, boolean in) {
    update(t -> t.zoomAt(x, y, in ? ZOOM_STEP : 1 / ZOOM_STEP));
  }

  public void zoomIn() {
    update(t -> t.zoomAt(viewportWidth / 2, viewportHeight / 2, ZOOM_STEP));
  }

  public void zoomOut() {
    update(t -> t.zoomAt(viewportWidth / 2, viewportHeight / 2, 1 / ZOOM_STEP));
  }

  public void fit() {
    update(ViewTransform::fit);
  }

  public void actualSize() {
    update(ViewTransform::actualSize);
  }

  public void panBy(double dx, double dy) {
    ViewTransform t = transform.get();
    if (t != null) {
      transform.set(t.panBy(dx, dy));
    }
  }

  private void update(java.util.function.UnaryOperator<ViewTransform> f) {
    ViewTransform t = transform.get();
    if (t != null) {
      transform.set(f.apply(t));
      pushViewportLater();
    }
  }

  /** Lleva el zoom a la sesión agrupando los cambios; decide el tamaño del proxy. */
  private void pushViewportLater() {
    if (pendingViewport != null) {
      pendingViewport.cancel();
    }
    pendingViewport =
        env.scheduler()
            .schedule(
                () -> env.publisher().execute(this::pushViewport), VIEWPORT_DEBOUNCE_NANOS);
  }

  void pushViewport() {
    ViewTransform t = transform.get();
    double zoom = t == null ? 1.0 : t.zoomOverFit();
    env.session().setViewport((int) viewportWidth, (int) viewportHeight, dpi, zoom);
    requestBeforeIfNeeded();
  }

  void frameReady(EditSession.Frame<F> f) {
    frame.set(f);
    ViewTransform t = transform.get();
    transform.set(
        t == null
            ? ViewTransform.fitted(
                f.fullWidth(), f.fullHeight(), viewportWidth, viewportHeight, dpi)
            : t.withImage(f.fullWidth(), f.fullHeight()));
    requestBeforeIfNeeded();
  }

  private void requestBeforeIfNeeded() {
    EditSession.Frame<F> current = frame.get();
    if (current == null
        || effectiveMode.get() == CompareMode.RESULT
        || env.session().source().isEmpty()) {
      return;
    }
    boolean fullResolution = !current.approximate() && env.session().proxySize().isPresent();
    StringBuilder key =
        new StringBuilder()
            .append(env.session().source().get().id())
            .append(':')
            .append(current.pixels().width())
            .append(':')
            .append(current.fullWidth())
            .append(':')
            .append(fullResolution);
    for (OpSpec s : env.session().recipe().steps()) {
      if (s.enabled() && s.opId().startsWith("transform.")) {
        key.append('|').append(s.canonical());
      }
    }
    if (key.toString().equals(beforeKey)) {
      return;
    }
    beforeKey = key.toString();
    env.session()
        .before(
            fullResolution,
            beforeConverter,
            beforeFrame::set,
            failure -> {
              beforeKey = "";
              onFailure.accept(failure.message());
            });
  }

  /** Puntero sobre el visor: valor del píxel mostrado, aproximado si es el proxy. */
  public void hover(double sx, double sy) {
    EditSession.Frame<F> f = frame.get();
    ViewTransform t = transform.get();
    if (f == null || t == null || !t.containsScreenPoint(sx, sy)) {
      readout.set(null);
      return;
    }
    ViewTransform.Point p = t.screenToImage(sx, sy);
    Img px = f.pixels();
    int ix = clampIndex(p.x() / f.fullWidth() * px.width(), px.width());
    int iy = clampIndex(p.y() / f.fullHeight() * px.height(), px.height());
    readout.set(
        new PixelReadout(
            clampIndex(p.x(), f.fullWidth()),
            clampIndex(p.y(), f.fullHeight()),
            px.argb(ix, iy),
            f.approximate()));
  }

  public void clearHover() {
    readout.set(null);
  }

  /** Clic: valor exacto del píxel, de la caché o con un render completo. */
  public void pick(double sx, double sy) {
    EditSession.Frame<F> f = frame.get();
    ViewTransform t = transform.get();
    if (f == null || t == null || !t.containsScreenPoint(sx, sy)) {
      return;
    }
    ViewTransform.Point p = t.screenToImage(sx, sy);
    int x = clampIndex(p.x(), f.fullWidth());
    int y = clampIndex(p.y(), f.fullHeight());
    env.session()
        .exactPixel(
            x,
            y,
            argb -> readout.set(new PixelReadout(x, y, argb, false)),
            failure -> onFailure.accept(failure.message()));
  }

  void clear() {
    frame.set(null);
    beforeFrame.set(null);
    transform.set(null);
    readout.set(null);
    beforeKey = "";
    curtain.set(Curtain.CENTER);
  }

  private static int clampIndex(double v, int size) {
    return Math.max(0, Math.min(size - 1, (int) v));
  }
}
