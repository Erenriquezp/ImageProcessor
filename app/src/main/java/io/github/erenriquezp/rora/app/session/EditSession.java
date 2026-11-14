package io.github.erenriquezp.rora.app.session;

import io.github.erenriquezp.rora.app.imaging.SourceFormat;
import io.github.erenriquezp.rora.app.lab.LabBundle;
import io.github.erenriquezp.rora.app.lab.LabBundles;
import io.github.erenriquezp.rora.app.session.ai.AiJobService;
import io.github.erenriquezp.rora.app.session.ai.AiResolver;
import io.github.erenriquezp.rora.app.session.ai.ArtifactKey;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy;
import io.github.erenriquezp.rora.core.image.ProxyPolicy.Size;
import io.github.erenriquezp.rora.core.op.ArtifactLookup;
import io.github.erenriquezp.rora.core.op.EditRecipe;
import io.github.erenriquezp.rora.core.op.ExposureStats;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.RecipeHistory;
import io.github.erenriquezp.rora.core.op.RenderLevel;
import io.github.erenriquezp.rora.core.op.Renderer;
import io.github.erenriquezp.rora.core.op.ResultCache;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

  /** Propósitos del carril FULL: uno no sustituye a otro; prioridad al cuentagotas. */
  private static final String RESIZE_OP = "transform.resize";

  private static final RenderLane.Slot SLOT_PIXEL = new RenderLane.Slot("pixel", 3);

  private static final RenderLane.Slot SLOT_ZOOM = new RenderLane.Slot("zoom", 2);
  private static final RenderLane.Slot SLOT_CURRENT = new RenderLane.Slot("current", 1);
  private static final RenderLane.Slot SLOT_DIAGNOSTIC = new RenderLane.Slot("diagnostic", 1);

  private final AiJobService ai;
  private int shownArtifact;
  private final AiResolver aiResolver;
  private final OpRegistry registry;
  private final Renderer renderer;
  private final RenderCoordinator coordinator;
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
  private long stateVersion;
  private BufferedImage currentImage;
  private RenderLane.Ticket currentTicket;
  private RenderLane.Ticket pixelTicket;
  private final List<Waiter> currentWaiters = new ArrayList<>();
  private int editingIndex = -1;
  private Set<String> excludedOps = Set.of();
  private EditRecipe exported = EditRecipe.EMPTY;
  private boolean sourceExported;

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
    this(toFrame, policy, cacheBytes, fusion, coordinatorFactory, null);
  }

  /**
   * @param ai mejora con IA; {@code null} si esta sesión no la ofrece
   */
  public EditSession(
      Function<Img, F> toFrame,
      ProxyPolicy policy,
      long cacheBytes,
      boolean fusion,
      Function<Renderer, RenderCoordinator> coordinatorFactory,
      AiJobService ai) {
    this.ai = ai;
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
    List<Operation> ops = new ArrayList<>(OpRegistry.load().all());
    for (LabBundle bundle : LabBundles.discover()) {
      ops.addAll(bundle.operations(ns));
    }
    this.registry = OpRegistry.of(ops);
    this.renderer =
        new Renderer(
            registry,
            new ResultCache(cacheBytes),
            fusion,
            ai != null ? ai : ArtifactLookup.NONE);
    this.aiResolver = ai != null ? new AiResolver(renderer, ai) : null;
    if (ai != null) {
      ai.addListener(
          new AiJobService.Listener() {
            @Override
            public void stateChanged() {
              aiChanged();
            }
          });
    }
    this.coordinator = Objects.requireNonNull(coordinatorFactory.apply(renderer), "coordinator");
  }

  /** Sesión real: coordinador con hilos propios, caché por defecto ({@code -Drora.cache.mb}). */
  public static <F> EditSession<F> create(
      java.util.concurrent.Executor publisher, Function<Img, F> toFrame, boolean fusion) {
    long mb = Long.getLong("rora.cache.mb", -1L);
    long bytes = mb > 0 ? mb << 20 : ResultCache.defaultBudget();
    return new EditSession<>(
        toFrame,
        ProxyPolicy.DEFAULT,
        bytes,
        fusion,
        r -> RenderCoordinator.create(r, publisher),
        AiJobService.create(publisher, Scheduler.system()));
  }

  /** Servicio de mejora con IA, si esta sesión lo ofrece. */
  public Optional<AiJobService> ai() {
    return Optional.ofNullable(ai);
  }

  /** Paso de mejora activo de la receta actual, si lo hay. */
  public Optional<AiResolver.Located> enhanceStep() {
    if (aiResolver == null || current == null) {
      return Optional.empty();
    }
    return aiResolver.locate(current.asRenderSource(), baseSteps(pending, List.of()));
  }

  /** Tamaño de la imagen que entraría al motor: tras la geometría. */
  public Size sizeBeforeEnhance() {
    SessionSource s = Objects.requireNonNull(current, "Sin imagen");
    Size size = new Size(s.width(), s.height());
    for (OpSpec step : visibleSteps()) {
      if (step.opId().equals(AiResolver.ENHANCE)) {
        break;
      }
      if (step.enabled()) {
        Operation op = registry.require(step.opId());
        size = op.outputSize(size, step.params().withSchema(op.schema()));
      }
    }
    return size;
  }

  /**
   * Lanza la mejora del paso activo. Es lo único que ejecuta el motor, y solo responde a una orden
   * explícita.
   *
   * @return si se ha lanzado
   */
  public boolean startEnhance() {
    Optional<AiResolver.Located> found = enhanceStep();
    if (found.isEmpty()) {
      return false;
    }
    AiResolver.Located located = found.get();
    Renderer.SourceImage source = current.asRenderSource();
    List<OpSpec> prefix = located.prefix();
    OpSpec step = baseSteps(pending, List.of()).get(located.index());
    boolean fullIntensity = step.params().getDouble("intensity") >= 100;
    return ai.start(
        located.key(),
        fullIntensity,
        token ->
            renderer
                .render(
                    new Renderer.Request(source, prefix, prefix.size(), RenderLevel.FULL),
                    new OpContext(TileExecutor.sequential(), token, Progress.NONE))
                .image());
  }

  private List<OpSpec> baseSteps(OpSpec pendingStep, List<OpSpec> tail) {
    SessionSource s = Objects.requireNonNull(current, "Sin imagen");
    List<OpSpec> steps = visibleSteps();
    if (pendingStep != null) {
      steps.add(pendingStep);
    }
    steps.addAll(tail);
    return new SourceFormats(registry, s.format(), s.nativeImage()).apply(steps).steps();
  }

  private boolean hasActiveEnhance() {
    for (OpSpec s : visibleSteps()) {
      if (s.enabled() && s.opId().equals(AiResolver.ENHANCE)) {
        return true;
      }
    }
    return false;
  }

  /** Tras cambiar la receta: un trabajo sobre otra entrada deja de valer. */
  private void syncAi() {
    if (ai == null || current == null) {
      return;
    }
    Optional<ArtifactKey> key = enhanceStep().map(AiResolver.Located::key);
    ai.keyChanged(key);
    ai.retain(key.map(Set::of).orElse(Set.of()));
    shownArtifact = currentArtifact();
  }

  private int currentArtifact() {
    return enhanceStep().map(l -> ai.artifactFor(l.key())).orElse(0);
  }

  /** Ha llegado o desaparecido el resultado de la mejora: lo que se ve deja de ser actual. */
  private void aiChanged() {
    if (current != null && currentArtifact() != shownArtifact) {
      stateChanged();
      requestPreview();
    }
  }

  public void addListener(Listener<F> l) {
    listeners.add(Objects.requireNonNull(l, "listener"));
  }

  public void removeListener(Listener<F> l) {
    listeners.remove(l);
  }

  public OpRegistry registry() {
    return registry;
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
      if (ai != null) {
        ai.clear();
      }
    }
    history.reset(EditRecipe.EMPTY);
    pending = null;
    proxy = Optional.empty();
    exported = EditRecipe.EMPTY;
    sourceExported = false;
    decideProxy();
    stateChanged();
    fireHistory();
    requestPreview();
  }

  public boolean hasImage() {
    return current != null;
  }

  public Optional<SessionSource> source() {
    return Optional.ofNullable(current);
  }

  // ── Cambios sin exportar ──────────────────────────────────────────────────

  /** ¿Hay ajustes que no se han exportado? Volver al estado exportado con deshacer cuenta. */
  public boolean isModified() {
    if (current == null) {
      return false;
    }
    return !effectiveRecipe().equals(exported) || (current.origin().isEmpty() && !sourceExported);
  }

  /** El estado actual ya está en un archivo. */
  public void markExported() {
    exported = effectiveRecipe();
    sourceExported = true;
  }

  private EditRecipe effectiveRecipe() {
    return pending == null ? history.current() : history.current().add(pending);
  }

  // ── Receta ────────────────────────────────────────────────────────────────

  public EditRecipe recipe() {
    return history.current();
  }

  /** Ids de los pasos activos de la receta efectiva, para el informe; nunca sus parámetros. */
  public List<String> activeStepIds() {
    return effectiveRecipe().activeSteps().stream().map(OpSpec::opId).toList();
  }

  /** Bytes que ocupa ahora la caché de resultados. */
  public long cacheBytes() {
    return renderer.cache().weightBytes();
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
    stateChanged();
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
    stateChanged();
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
    stateChanged();
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
    stateChanged();
    fireHistory();
    requestPreview();
    return true;
  }

  /** "Restablecer": receta vacía e historial vacío (como hoy). */
  public void reset() {
    history.reset(EditRecipe.EMPTY);
    pending = null;
    stateChanged();
    fireHistory();
    requestPreview();
  }

  // ── Edición directa de la receta (UI nueva) ───────────────────────────────

  /**
   * Sustituye la receta. Las llamadas con la misma {@code gestureKey} no nula forman una sola
   * entrada de deshacer, hasta {@link #endGesture()}.
   */
  public void applyRecipe(EditRecipe next, Object gestureKey) {
    Objects.requireNonNull(next, "next");
    history.record(next, gestureKey);
    pending = null;
    recipeChanged();
  }

  /** Cierra el gesto en curso y deja de considerar editado ningún paso. */
  public void endGesture() {
    history.endCoalescing();
    editingIndex = -1;
  }

  /** Vacía la receta como una entrada más: se puede deshacer. */
  public void resetRecipe() {
    applyRecipe(EditRecipe.EMPTY, null);
  }

  /**
   * Paso que se está editando: los anteriores forman la cabeza cacheada, de modo que cada tick solo
   * recalcula desde él. {@code -1} si ninguno.
   */
  public void setEditingIndex(int index) {
    editingIndex = index;
  }

  /** Operaciones que no se aplican mientras dure una herramienta (p. ej. el recorte al editarlo). */
  public void setExcludedOps(Set<String> opIds) {
    excludedOps = Set.copyOf(opIds);
    if (current != null) {
      recipeChanged();
    }
  }

  private void recipeChanged() {
    decideProxy();
    stateChanged();
    fireHistory();
    requestPreview();
  }

  /** Pasos confirmados que cuentan ahora (sin los excluidos por una herramienta). */
  private List<OpSpec> visibleSteps() {
    List<OpSpec> steps = new ArrayList<>();
    for (OpSpec s : history.current().steps()) {
      if (!excludedOps.contains(s.opId())) {
        steps.add(s);
      }
    }
    return steps;
  }

  private int headLength() {
    int head = 0;
    List<OpSpec> all = history.current().steps();
    int limit = editingIndex >= 0 ? Math.min(editingIndex, all.size()) : all.size();
    for (int i = 0; i < limit; i++) {
      OpSpec s = all.get(i);
      if (s.enabled() && !excludedOps.contains(s.opId())) {
        head++;
      }
    }
    return head;
  }

  /** Tamaño de la imagen tras los pasos que cambian la geometría. */
  public Size outputSize() {
    SessionSource s = Objects.requireNonNull(current, "Sin imagen");
    return foldSize(new Size(s.width(), s.height()), null);
  }

  private Size foldSize(Size start, String skipOp) {
    Size size = start;
    for (OpSpec s : visibleSteps()) {
      if (s.enabled() && !s.opId().equals(skipOp)) {
        Operation op = registry.require(s.opId());
        size = op.outputSize(size, s.params().withSchema(op.schema()));
      }
    }
    return size;
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
    Size source = new Size(s.width(), s.height());
    // Un recorte muestra solo una parte de la fuente: hace falta más resolución de proxy para ella.
    // Reducir con transform.resize no la pide.
    double crop =
        Math.max(1.0, (double) source.longSide() / foldSize(source, RESIZE_OP).longSide());
    ProxyPolicy.Decision d = policy.decide(source, vw, vh, dpi, zoom * crop, proxy);
    proxy = d.proxy();
    fullNeeded = d.fullNeeded();
    if (proxy.isEmpty() && hasActiveEnhance()) {
      // Sin proxy, cada tick de un slider renderizaría la salida ampliada entera. La resolución
      // completa solo se pide con zoom: es cuando se ve el detalle.
      proxy = Optional.of(source);
      fullNeeded = zoom > 1.0;
    }
  }

  // ── Peticiones ────────────────────────────────────────────────────────────

  /** Receta efectiva (confirmada + {@code pendingStep}) con los formatos de origen rellenos. */
  SourceFormats.Effective effective(OpSpec pendingStep, List<OpSpec> tail) {
    SessionSource s = Objects.requireNonNull(current, "Sin imagen");
    List<OpSpec> steps = visibleSteps();
    if (pendingStep != null) {
      steps.add(pendingStep);
    }
    steps.addAll(tail);
    SourceFormats.Effective eff =
        new SourceFormats(registry, s.format(), s.nativeImage()).apply(steps);
    if (aiResolver == null) {
      return eff;
    }
    return new SourceFormats.Effective(
        aiResolver.apply(s.asRenderSource(), eff.steps()), eff.finalFormat());
  }

  private Renderer.Request request(OpSpec pendingStep, List<OpSpec> tail, RenderLevel level) {
    SourceFormats.Effective eff = effective(pendingStep, tail);
    return new Renderer.Request(current.asRenderSource(), eff.steps(), headLength(), level);
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
      Size out = outputSize();
      Frame<F> f = new Frame<>((F) prepared, r.image(), approximate, out.width(), out.height());
      for (Listener<F> l : listeners) {
        l.frameReady(f);
      }
      if (thenFull) {
        // Zoom mayor que el proxy: tras el preview, la imagen completa (nunca especulativo).
        zoomTicket =
            coordinator.full(
                request(pending, List.of(), RenderLevel.FULL),
                new FrameListener(v, false, s, false),
                SLOT_ZOOM);
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
   * Render FULL interactivo de la receta confirmada + {@code pendingStep} + {@code tail}; {@code
   * inWorker} se ejecuta en el worker con el resultado.
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
   * Imagen original para el panel izquierdo del modo comparar, en el carril auxiliar: el proxy si
   * lo hay (salvo {@code fullResolution}) y, para el cuentagotas, los píxeles de la fuente.
   */
  public RenderLane.Ticket original(
      boolean fullResolution,
      Function<Img, F> convert,
      Consumer<Frame<F>> onFrame,
      Consumer<RenderFailure> onFailed) {
    SessionSource s = Objects.requireNonNull(current, "Sin imagen");
    RenderLevel level =
        fullResolution || proxy.isEmpty()
            ? RenderLevel.FULL
            : new RenderLevel.Preview(proxy.get().width(), proxy.get().height());
    return coordinator.aux(
        new Renderer.Request(s.asRenderSource(), List.of(), 0, level),
        new WorkListener<>(
            r -> new Frame<>(convert.apply(r.image()), s.image(), false, s.width(), s.height()),
            onFrame,
            onFailed));
  }

  /**
   * Imagen "antes" del comparador: la fuente con solo los pasos de geometría, para que coincida con
   * el resultado. Usa el proxy si lo hay (salvo {@code fullResolution}).
   */
  public RenderLane.Ticket before(
      boolean fullResolution,
      Function<Img, F> convert,
      Consumer<Frame<F>> onFrame,
      Consumer<RenderFailure> onFailed) {
    SessionSource s = Objects.requireNonNull(current, "Sin imagen");
    List<OpSpec> geometry = new ArrayList<>();
    for (OpSpec step : visibleSteps()) {
      if (step.enabled() && step.opId().startsWith("transform.")) {
        geometry.add(step);
      }
    }
    boolean useProxy = !fullResolution && proxy.isPresent();
    RenderLevel level =
        useProxy
            ? new RenderLevel.Preview(proxy.get().width(), proxy.get().height())
            : RenderLevel.FULL;
    Size out = outputSize();
    return coordinator.aux(
        new Renderer.Request(s.asRenderSource(), geometry, geometry.size(), level),
        new WorkListener<>(
            r ->
                new Frame<>(
                    convert.apply(r.image()), r.image(), useProxy, out.width(), out.height()),
            onFrame,
            onFailed));
  }

  /**
   * La imagen actual a resolución completa (receta + pendiente, con sus marcas), del mismo tipo que
   * {@code processedImage} en la app: entrada del blending y del histograma. Se calcula una vez por
   * estado; si el estado cambia con peticiones en espera, se relanza para el estado nuevo.
   */
  public void withCurrentImage(Consumer<BufferedImage> onImage, Consumer<RenderFailure> onFailed) {
    if (currentImage != null) {
      onImage.accept(currentImage);
      return;
    }
    currentWaiters.add(new Waiter(onImage, onFailed));
    if (currentTicket == null) {
      requestCurrentImage();
    }
  }

  private record Waiter(Consumer<BufferedImage> onImage, Consumer<RenderFailure> onFailed) {}

  private void requestCurrentImage() {
    SourceFormats.Effective eff = effective(pending, List.of());
    SessionSource s = current;
    SourceFormat.NativeSource ns = s.nativeSource();
    Renderer.Request req =
        new Renderer.Request(
            s.asRenderSource(),
            eff.steps(),
            headLength(),
            RenderLevel.FULL);
    long state = stateVersion;
    currentTicket =
        coordinator.full(
            req,
            new WorkListener<BufferedImage>(
                r -> SourceFormat.materialize(r.image(), eff.finalFormat(), ns),
                img -> {
                  if (state != stateVersion) {
                    return;
                  }
                  currentTicket = null;
                  currentImage = img;
                  List<Waiter> ws = List.copyOf(currentWaiters);
                  currentWaiters.clear();
                  ws.forEach(w -> w.onImage().accept(img));
                },
                f -> {
                  if (state != stateVersion) {
                    return;
                  }
                  currentTicket = null;
                  List<Waiter> ws = List.copyOf(currentWaiters);
                  currentWaiters.clear();
                  ws.forEach(w -> w.onFailed().accept(f));
                }),
            SLOT_CURRENT);
  }

  /** Lo que depende de la receta efectiva deja de valer: se llama tras cada cambio de estado. */
  private void stateChanged() {
    syncAi();
    stateVersion++;
    currentImage = null;
    if (currentTicket != null) {
      currentTicket.cancel();
      currentTicket = null;
    }
    if (pixelTicket != null) {
      pixelTicket.cancel();
      pixelTicket = null;
    }
    if (!currentWaiters.isEmpty() && current != null) {
      requestCurrentImage();
    }
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
            headLength(),
            RenderLevel.FULL);
    SourceFormat.NativeSource ns = s.nativeSource();
    return coordinator.export(
        req,
        new WorkListener<>(
            r -> SourceFormat.materialize(r.image(), eff.finalFormat(), ns), onImage, onFailed));
  }

  /** Exportación: FULL protegido de la receta efectiva actual, como {@code Img}. */
  public RenderLane.Ticket exportImg(Consumer<Img> onImage, Consumer<RenderFailure> onFailed) {
    SessionSource s = current;
    Renderer.Request req =
        new Renderer.Request(
            s.asRenderSource(),
            effective(pending, List.of()).steps(),
            headLength(),
            RenderLevel.FULL);
    return coordinator.export(req, new WorkListener<>(Renderer.Result::image, onImage, onFailed));
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
    return coordinator.full(
        request(pendingWithoutMarks, List.of(analysis), RenderLevel.FULL),
        new WorkListener<ExposureStats>(r -> (ExposureStats) r.aux(), onStats, onFailed),
        SLOT_DIAGNOSTIC);
  }

  /**
   * Color exacto del píxel {@code (x, y)} de la imagen completa: de la caché si está; si no, con un
   * FULL prioritario. Un cambio de estado abandona la petición en curso.
   */
  public void exactPixel(int x, int y, IntConsumer onArgb, Consumer<RenderFailure> onFailed) {
    Renderer.Request req = request(pending, List.of(), RenderLevel.FULL);
    Optional<Img> cached =
        req.steps().isEmpty()
            ? Optional.of(current.image())
            : renderer.cache().get(renderer.keyOf(req));
    if (cached.isPresent()) {
      onArgb.accept(cached.get().argb(x, y));
      return;
    }
    if (pixelTicket != null) {
      pixelTicket.cancel();
    }
    pixelTicket =
        coordinator.full(
            req,
            new WorkListener<Integer>(
                r -> r.image().argb(x, y),
                v -> {
                  pixelTicket = null;
                  onArgb.accept(v);
                },
                onFailed),
            SLOT_PIXEL);
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

  /**
   * Tras un {@code OutOfMemoryError}: cancela la IA en curso y vacía la caché de resultados. Se
   * puede llamar desde cualquier hilo; la vista se vuelve a pedir sola en el siguiente cambio.
   */
  public void releaseMemory() {
    if (ai != null) {
      ai.cancel();
    }
    renderer.cache().clear();
  }

  @Override
  public void close() {
    coordinator.close();
    if (ai != null) {
      ai.close();
    }
  }
}
