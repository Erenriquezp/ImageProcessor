package io.github.erenriquezp.rora.app.session;

import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy;
import io.github.erenriquezp.rora.core.op.EditRecipe;
import io.github.erenriquezp.rora.core.op.ExposureStats;
import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.RecipeHistory;
import io.github.erenriquezp.rora.core.op.RenderLevel;
import io.github.erenriquezp.rora.core.op.Renderer;
import io.github.erenriquezp.rora.core.op.ResultCache;
import io.github.erenriquezp.rora.service.LegacySteps;
import io.github.erenriquezp.rora.service.SourceFormat;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * Sesión de edición sin JavaFX: imagen fuente, receta confirmada con su historial, paso pendiente
 * (lo que muestran los sliders), proxy de preview y peticiones de render. Sustituye al estado que
 * {@code ImageProcessorApp} tenía repartido ({@code originalImage}, {@code committedImage}, {@code
 * processedImage}, pilas de undo/redo de imágenes y {@code previewGen}).
 *
 * <p>Hilos: todos los métodos públicos se llaman desde un único hilo (el FX en la app); los oyentes
 * reciben las notificaciones en el hilo del publicador (también el FX). Los renders corren en el
 * {@link RenderCoordinator}; la conversión al tipo de imagen mostrada ({@code F}) se hace en el
 * worker.
 *
 * @param <F> imagen lista para mostrar (una {@code WritableImage} en la app; {@code Img} en tests)
 */
public final class EditSession<F> implements AutoCloseable {

  /**
   * Imagen para el visor.
   *
   * @param image imagen preparada en el worker
   * @param pixels píxeles mostrados (para el cuentagotas)
   * @param approximate es el proxy (preview), no el resultado exacto
   * @param fullWidth ancho de la imagen completa
   * @param fullHeight alto de la imagen completa
   */
  public record Frame<F>(F image, Img pixels, boolean approximate, int fullWidth, int fullHeight) {}

  /** Notificaciones de la sesión, en el hilo del publicador. */
  public interface Listener<F> {
    default void frameReady(Frame<F> frame) {}

    default void historyChanged(int depth, boolean canUndo, boolean canRedo) {}

    default void failed(RenderFailure failure) {}

    default void progress(double fraction) {}
  }

  private final OpRegistry registry;
  private final Renderer renderer;
  private final RenderCoordinator coordinator;
  private final FilterSpecs specs;
  private final Function<Img, F> toFrame;
  private final ProxyPolicy policy;
  private final List<Listener<F>> listeners = new CopyOnWriteArrayList<>();

  /** Fuente actual, visible para el {@code NativeSource} desde los workers. */
  private volatile SessionSource current;

  // Estado del hilo propietario.
  private final RecipeHistory history = new RecipeHistory(EditRecipe.EMPTY);
  private OpSpec pending;
  private Optional<ProxyPolicy.Size> proxy = Optional.empty();
  private boolean fullNeeded;
  private int vw = 1280;
  private int vh = 800;
  private double dpi = 1.0;
  private double zoom = 1.0;
  private long version;
  private RenderLane.Ticket zoomTicket;

  /**
   * @param coordinatorFactory crea el coordinador a partir del renderer (real en la app, manual en
   *     tests)
   */
  public EditSession(
      Function<Img, F> toFrame,
      ProxyPolicy policy,
      long cacheBytes,
      boolean fusion,
      Function<Renderer, RenderCoordinator> coordinatorFactory) {
    this.toFrame = Objects.requireNonNull(toFrame, "toFrame");
    this.policy = Objects.requireNonNull(policy, "policy");
    SourceFormat.NativeSource ns =
        new SourceFormat.NativeSource() {
          @Override
          public Optional<BufferedImage> nativeFor(Img img) {
            SessionSource s = current;
            return s != null && s.image() == img ? s.nativeImage() : Optional.empty();
          }

          @Override
          public Optional<BufferedImage> template() {
            SessionSource s = current;
            return s == null ? Optional.empty() : s.nativeImage();
          }
        };
    this.registry = LegacySteps.registryWithLegacy(ns);
    this.renderer = new Renderer(registry, new ResultCache(cacheBytes), fusion);
    this.coordinator = Objects.requireNonNull(coordinatorFactory.apply(renderer), "coordinator");
    this.specs = new FilterSpecs(registry);
  }

  /** Sesión real: coordinador con hilos propios, caché por defecto ({@code -Drora.cache.mb}). */
  public static <F> EditSession<F> create(
      java.util.concurrent.Executor publisher, Function<Img, F> toFrame, boolean fusion) {
    long mb = Long.getLong("rora.cache.mb", -1L);
    long bytes = mb > 0 ? mb << 20 : ResultCache.defaultBudget();
    return new EditSession<>(
        toFrame, ProxyPolicy.DEFAULT, bytes, fusion, r -> RenderCoordinator.create(r, publisher));
  }

  public void addListener(Listener<F> l) {
    listeners.add(Objects.requireNonNull(l, "listener"));
  }

  public OpRegistry registry() {
    return registry;
  }

  public FilterSpecs specs() {
    return specs;
  }

  public Renderer renderer() {
    return renderer;
  }

  // ── Imagen ────────────────────────────────────────────────────────────────

  /** Carga una imagen nueva: vacía historial, pendiente y la caché de la anterior. */
  public void load(SessionSource source) {
    Objects.requireNonNull(source, "source");
    SessionSource old = current;
    current = source;
    if (old != null) {
      renderer.cache().invalidateSource(old.id());
    }
    history.reset(EditRecipe.EMPTY);
    pending = null;
    proxy = Optional.empty();
    decideProxy();
    fireHistory();
    requestPreview();
  }

  public boolean hasImage() {
    return current != null;
  }

  public Optional<SessionSource> source() {
    return Optional.ofNullable(current);
  }

  // ── Receta ────────────────────────────────────────────────────────────────

  public EditRecipe recipe() {
    return history.current();
  }

  public Optional<OpSpec> pending() {
    return Optional.ofNullable(pending);
  }

  /** Cambia el paso pendiente (lo que muestran los sliders) y pide un preview. */
  public void setPending(Optional<OpSpec> spec) {
    OpSpec next = spec.orElse(null);
    if (Objects.equals(next, pending)) {
      return;
    }
    pending = next;
    requestPreview();
  }

  /**
   * "Aplicar filtro": añade {@code committed} a la receta (una entrada de historial) y vacía el
   * pendiente. Devuelve el número de pasos que se pueden deshacer.
   */
  public int apply(OpSpec committed) {
    Objects.requireNonNull(committed, "committed");
    history.record(history.current().add(committed), null);
    pending = null;
    fireHistory();
    requestPreview();
    return history.undoDepth();
  }

  public boolean undo() {
    if (!history.canUndo()) {
      return false;
    }
    history.undo();
    pending = null;
    fireHistory();
    requestPreview();
    return true;
  }

  public boolean redo() {
    if (!history.canRedo()) {
      return false;
    }
    history.redo();
    pending = null;
    fireHistory();
    requestPreview();
    return true;
  }

  /** "Restablecer": receta vacía e historial vacío (como hoy). */
  public void reset() {
    history.reset(EditRecipe.EMPTY);
    pending = null;
    fireHistory();
    requestPreview();
  }

  public int undoDepth() {
    return history.undoDepth();
  }

  public boolean canUndo() {
    return history.canUndo();
  }

  public boolean canRedo() {
    return history.canRedo();
  }

  // ── Visor y proxy ─────────────────────────────────────────────────────────

  /** Tamaño del visor (píxeles lógicos), escala DPI y zoom (≥ 1); recalcula el proxy si toca. */
  public void setViewport(int width, int height, double dpiScale, double zoomLevel) {
    vw = Math.max(1, width);
    vh = Math.max(1, height);
    dpi = dpiScale > 0 ? dpiScale : 1.0;
    zoom = Math.max(1.0, zoomLevel);
    if (current == null) {
      return;
    }
    Optional<ProxyPolicy.Size> before = proxy;
    boolean neededBefore = fullNeeded;
    decideProxy();
    if (!proxy.equals(before) || fullNeeded != neededBefore) {
      requestPreview();
    }
  }

  public Optional<ProxyPolicy.Size> proxySize() {
    return proxy;
  }

  private void decideProxy() {
    SessionSource s = current;
    if (s == null) {
      return;
    }
    ProxyPolicy.Decision d =
        policy.decide(new ProxyPolicy.Size(s.width(), s.height()), vw, vh, dpi, zoom, proxy);
    proxy = d.proxy();
    fullNeeded = d.fullNeeded();
  }

  // ── Peticiones ────────────────────────────────────────────────────────────

  /** Receta efectiva (confirmada + {@code pendingStep}) con los formatos de origen rellenos. */
  SourceFormats.Effective effective(OpSpec pendingStep, List<OpSpec> tail) {
    SessionSource s = Objects.requireNonNull(current, "Sin imagen");
    List<OpSpec> steps = new ArrayList<>(history.current().steps());
    if (pendingStep != null) {
      steps.add(pendingStep);
    }
    steps.addAll(tail);
    return new SourceFormats(registry, s.format(), s.nativeImage()).apply(steps);
  }

  private Renderer.Request request(OpSpec pendingStep, List<OpSpec> tail, RenderLevel level) {
    SourceFormats.Effective eff = effective(pendingStep, tail);
    int head = history.current().activeSteps().size();
    return new Renderer.Request(current.asRenderSource(), eff.steps(), head, level);
  }

  /** Pide el preview del estado actual (proxy si lo hay; si no, la imagen completa). */
  public void requestPreview() {
    if (current == null) {
      return;
    }
    long v = ++version;
    if (zoomTicket != null) {
      zoomTicket.cancel();
      zoomTicket = null;
    }
    boolean approximate = proxy.isPresent();
    RenderLevel level =
        proxy
            .<RenderLevel>map(p -> new RenderLevel.Preview(p.width(), p.height()))
            .orElse(RenderLevel.FULL);
    SessionSource s = current;
    coordinator.preview(
        request(pending, List.of(), level),
        new FrameListener(v, approximate, s, approximate && fullNeeded));
  }

  /** Oyente que convierte en el worker y publica el frame si sigue siendo el estado actual. */
  private final class FrameListener implements RenderLane.Listener {
    final long v;
    final boolean approximate;
    final SessionSource s;
    final boolean thenFull;

    FrameListener(long v, boolean approximate, SessionSource s, boolean thenFull) {
      this.v = v;
      this.approximate = approximate;
      this.s = s;
      this.thenFull = thenFull;
    }

    @Override
    public Object prepare(Renderer.Result r) {
      return toFrame.apply(r.image());
    }

    @Override
    public void onDone(Renderer.Result r) {
      onDone(r, toFrame.apply(r.image()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onDone(Renderer.Result r, Object prepared) {
      if (v != version || s != current) {
        return;
      }
      Frame<F> f = new Frame<>((F) prepared, r.image(), approximate, s.width(), s.height());
      for (Listener<F> l : listeners) {
        l.frameReady(f);
      }
      if (thenFull) {
        // Zoom mayor que el proxy: tras el preview, la imagen completa (nunca especulativo).
        zoomTicket =
            coordinator.full(
                request(pending, List.of(), RenderLevel.FULL),
                new FrameListener(v, false, s, false));
      }
    }

    @Override
    public void onFailed(RenderFailure failure) {
      if (v == version) {
        fireFailed(failure);
      }
    }

    @Override
    public void onProgress(double fraction) {
      if (v == version) {
        for (Listener<F> l : listeners) {
          l.progress(fraction);
        }
      }
    }
  }

  /**
   * Render FULL interactivo (último gana en el carril FULL) de la receta confirmada + {@code
   * pendingStep} + {@code tail}; {@code inWorker} se ejecuta en el worker con el resultado.
   */
  public <T> RenderLane.Ticket full(
      OpSpec pendingStep,
      List<OpSpec> tail,
      Function<Renderer.Result, T> inWorker,
      Consumer<T> onDone,
      Consumer<RenderFailure> onFailed) {
    return coordinator.full(
        request(pendingStep, tail, RenderLevel.FULL),
        new WorkListener<>(inWorker, onDone, onFailed));
  }

  /**
   * Exportación: FULL protegido de la receta efectiva actual y, en el worker, el {@code
   * BufferedImage} del mismo tipo que guardaba la app (formato final de la cadena).
   */
  public RenderLane.Ticket export(
      Consumer<BufferedImage> onImage, Consumer<RenderFailure> onFailed) {
    SourceFormats.Effective eff = effective(pending, List.of());
    SessionSource s = current;
    Renderer.Request req =
        new Renderer.Request(
            s.asRenderSource(),
            eff.steps(),
            history.current().activeSteps().size(),
            RenderLevel.FULL);
    SourceFormat.NativeSource ns = s.nativeSource();
    return coordinator.export(
        req,
        new WorkListener<>(
            r -> SourceFormat.materialize(r.image(), eff.finalFormat(), ns), onImage, onFailed));
  }

  /**
   * Diagnóstico de exposición exacto del estado con {@code pendingWithoutMarks} como pendiente
   * (FULL + {@code analysis.exposure}).
   */
  public RenderLane.Ticket exposure(
      OpSpec pendingWithoutMarks,
      Consumer<ExposureStats> onStats,
      Consumer<RenderFailure> onFailed) {
    OpSpec analysis = OpSpec.defaults(registry.require("analysis.exposure"));
    return full(
        pendingWithoutMarks, List.of(analysis), r -> (ExposureStats) r.aux(), onStats, onFailed);
  }

  /** Color exacto del píxel {@code (x, y)} de la imagen completa (de la caché o con un FULL). */
  public Optional<RenderLane.Ticket> exactPixel(
      int x, int y, IntConsumer onArgb, Consumer<RenderFailure> onFailed) {
    Renderer.Request req = request(pending, List.of(), RenderLevel.FULL);
    Optional<Img> cached = renderer.cache().get(renderer.keyOf(req));
    if (req.steps().isEmpty()) {
      cached = Optional.of(current.image());
    }
    if (cached.isPresent()) {
      onArgb.accept(cached.get().argb(x, y));
      return Optional.empty();
    }
    return Optional.of(
        coordinator.full(
            req, new WorkListener<>(r -> r.image().argb(x, y), onArgb::accept, onFailed)));
  }

  /** Oyente genérico: trabajo en el worker, resultado en el publicador. */
  private static final class WorkListener<T> implements RenderLane.Listener {
    final Function<Renderer.Result, T> inWorker;
    final Consumer<T> onDone;
    final Consumer<RenderFailure> onFailed;

    WorkListener(
        Function<Renderer.Result, T> inWorker, Consumer<T> onDone, Consumer<RenderFailure> f) {
      this.inWorker = inWorker;
      this.onDone = onDone;
      this.onFailed = f;
    }

    @Override
    public Object prepare(Renderer.Result r) {
      return inWorker.apply(r);
    }

    @Override
    public void onDone(Renderer.Result r) {
      onDone(r, inWorker.apply(r));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onDone(Renderer.Result r, Object prepared) {
      onDone.accept((T) prepared);
    }

    @Override
    public void onFailed(RenderFailure failure) {
      onFailed.accept(failure);
    }
  }

  private void fireHistory() {
    for (Listener<F> l : listeners) {
      l.historyChanged(history.undoDepth(), history.canUndo(), history.canRedo());
    }
  }

  private void fireFailed(RenderFailure f) {
    for (Listener<F> l : listeners) {
      l.failed(f);
    }
  }

  public boolean isIdle() {
    return coordinator.isIdle();
  }

  @Override
  public void close() {
    coordinator.close();
  }
}
