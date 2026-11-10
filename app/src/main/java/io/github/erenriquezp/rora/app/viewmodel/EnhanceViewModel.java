package io.github.erenriquezp.rora.app.viewmodel;

import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.model.ControlSpec;
import io.github.erenriquezp.rora.app.model.RecipeEdits;
import io.github.erenriquezp.rora.app.session.ai.AiJobService;
import io.github.erenriquezp.rora.app.session.ai.AiLimits;
import io.github.erenriquezp.rora.app.session.ai.AiResolver;
import io.github.erenriquezp.rora.core.image.ProxyPolicy.Size;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;
import io.github.erenriquezp.rora.core.op.EditRecipe;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.Params;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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

/**
 * Panel Mejorar. El motor solo arranca desde {@link #start()} o desde la pregunta al exportar;
 * abrir el panel o mover sus controles no lo ejecuta nunca.
 */
public final class EnhanceViewModel<F> implements ControlState.Sink {

  public static final String X2 = "X2";
  public static final String X4 = "X4";

  private static final String SCALE = "scale";
  private static final String INTENSITY = "intensity";
  private static final int MEGA = 1_000_000;

  private final Env<F> env;
  private final Messages m;
  private final GestureTracker gesture;
  private final Consumer<Prompt> prompt;
  private final BiConsumer<String, Exception> problem;
  private final Operation op;
  private final ControlState intensity;

  private final BooleanProperty available = new SimpleBooleanProperty(false);
  private final ObjectProperty<String> scale = new SimpleObjectProperty<>(X2);
  private final ObjectProperty<AiJobService.State> state =
      new SimpleObjectProperty<>(AiJobService.State.NOT_RUN);
  private final BooleanProperty running = new SimpleBooleanProperty(false);
  private final BooleanProperty canStart = new SimpleBooleanProperty(false);
  private final BooleanProperty x4Allowed = new SimpleBooleanProperty(true);
  private final StringProperty dimensions = new SimpleStringProperty("");
  private final StringProperty limit = new SimpleStringProperty("");
  private final StringProperty estimate = new SimpleStringProperty("");
  private final StringProperty warning = new SimpleStringProperty("");
  private final StringProperty notice = new SimpleStringProperty("");
  private final StringProperty action = new SimpleStringProperty("");
  private final DoubleProperty progress = new SimpleDoubleProperty(-1);
  private final StringProperty progressText = new SimpleStringProperty("");
  private final IntegerProperty threads = new SimpleIntegerProperty(1);

  private double estimatedSeconds;
  private Runnable whenReady;
  private Runnable whenAbandoned;

  EnhanceViewModel(
      Env<F> env,
      GestureTracker gesture,
      Consumer<Prompt> prompt,
      BiConsumer<String, Exception> problem) {
    this.env = env;
    this.m = env.messages();
    this.gesture = gesture;
    this.prompt = prompt;
    this.problem = problem;
    this.op = env.session().registry().require(RecipeEdits.ENHANCE);
    ControlSpec.Slider base =
        (ControlSpec.Slider)
            ControlSpec.from(op.schema(), m, Set.of(SCALE, "target", "artifact")).get(0);
    this.intensity =
        new ControlState(
            new ControlSpec.Slider(
                base.key(),
                base.label(),
                base.min(),
                base.max(),
                base.step(),
                base.decimals(),
                base.integer(),
                100.0),
            100.0,
            this);
    env.session().ai().ifPresent(ai -> threads.set(ai.threads()));
    threads.addListener(
        (o, was, now) -> env.session().ai().ifPresent(ai -> ai.setThreads(now.intValue())));
    refresh();
  }

  // ── Órdenes ───────────────────────────────────────────────────────────────

  /** Elige la escala. Con el paso ya en la receta, lo actualiza; nunca ejecuta el motor. */
  public void setScale(String value) {
    if (value.equals(scale.get()) || running.get()) {
      return;
    }
    scale.set(value);
    if (env.session().enhanceStep().isPresent()) {
      env.session().applyRecipe(withStep(env.session().recipe()), null);
    }
    refresh();
  }

  /** Otra imagen: lo elegido para la anterior no se arrastra. */
  void resetChoices() {
    scale.set(X2);
    intensity.sync(100.0);
  }

  /** Botón Mejorar. Si se prevé una espera larga, pregunta antes. */
  public void start() {
    if (!canStart.get()) {
      return;
    }
    if (AiLimits.band(estimatedSeconds) != AiLimits.Band.CONFIRM) {
      launch();
      return;
    }
    prompt.accept(
        new Prompt(
            m.get("ui.enhance.confirm.title"),
            m.format("ui.enhance.confirm.body", timeText(estimatedSeconds)),
            List.of(
                new Prompt.Choice(
                    m.get("ui.enhance.action.run"),
                    true,
                    () -> {
                      prompt.accept(null);
                      launch();
                    }),
                new Prompt.Choice(m.get("ui.action.cancel"), false, () -> prompt.accept(null)))));
  }

  public void cancel() {
    env.session().ai().ifPresent(AiJobService::cancel);
  }

  private boolean launch() {
    if (env.session().enhanceStep().isEmpty()) {
      env.session().applyRecipe(withStep(env.session().recipe()), null);
    }
    boolean started = env.session().startEnhance();
    refresh();
    return started;
  }

  private EditRecipe withStep(EditRecipe recipe) {
    Params params =
        op.schema().defaults().toBuilder()
            .set(SCALE, scale.get())
            .set(INTENSITY, ((Number) intensity.value()).doubleValue())
            .build();
    return RecipeEdits.putEnhance(recipe, OpSpec.of(op, params));
  }

  // ── Exportación ───────────────────────────────────────────────────────────

  /** Hay un paso activo cuyo resultado aún no se puede usar. */
  boolean needsDecision() {
    return env.session().enhanceStep().isPresent() && state.get() != AiJobService.State.READY;
  }

  /** Hay un paso activo, listo o no. */
  boolean hasStep() {
    return env.session().enhanceStep().isPresent();
  }

  /** La salida contendrá de verdad píxeles generados por el modelo. */
  boolean applied() {
    return state.get() == AiJobService.State.READY
        && ((Number) intensity.value()).doubleValue() > 0;
  }

  /** Se puede mejorar ahora (o ya se está haciendo) para esta exportación. */
  boolean canRunForExport() {
    return running.get() || canStart.get();
  }

  /** Texto de la opción de mejorar antes de exportar. */
  String exportChoiceLabel() {
    return running.get()
        ? m.get("ui.enhance.export.wait")
        : m.format("ui.enhance.export.run", timeText(estimatedSeconds));
  }

  /** Mejora (o espera a la mejora en curso) y avisa al terminar. */
  void runThen(Runnable ready, Runnable abandoned) {
    whenReady = ready;
    whenAbandoned = abandoned;
    if (!running.get() && !launch()) {
      settle();
    }
  }

  private void settle() {
    Runnable next = state.get() == AiJobService.State.READY ? whenReady : whenAbandoned;
    whenReady = null;
    whenAbandoned = null;
    if (next != null) {
      next.run();
    }
  }

  // ── Eventos ───────────────────────────────────────────────────────────────

  void stateChanged() {
    refresh();
    if (whenReady != null && !running.get()) {
      settle();
    }
    if (!running.get()) {
      progress.set(-1);
      progressText.set("");
    }
  }

  void progress(AiJobService.Progress p) {
    progress.set(p.total() == 0 ? -1 : (double) p.done() / p.total());
    boolean withEta =
        p.etaSeconds().isPresent() && AiLimits.band(estimatedSeconds) != AiLimits.Band.SHORT;
    progressText.set(
        withEta
            ? m.format(
                "ui.enhance.progress.eta",
                p.done(),
                p.total(),
                timeText(p.etaSeconds().getAsDouble()))
            : m.format("ui.enhance.progress", p.done(), p.total()));
  }

  void failed(AiFailure failure) {
    problem.accept(m.get("ui.enhance.failed"), failure);
  }

  void sync(EditRecipe recipe) {
    int i = RecipeEdits.indexOf(recipe, RecipeEdits.ENHANCE);
    if (i >= 0) {
      Params p = recipe.steps().get(i).params().withSchema(op.schema());
      scale.set(p.getEnum(SCALE));
      intensity.sync(p.getDouble(INTENSITY));
    }
    refresh();
  }

  @Override
  public void changed(ControlState control, boolean keyboard) {
    Optional<AiResolver.Located> step = env.session().enhanceStep();
    if (step.isPresent()) {
      EditRecipe next = withStep(env.session().recipe());
      env.session().setEditingIndex(RecipeEdits.indexOf(next, RecipeEdits.ENHANCE));
      env.session().applyRecipe(next, gesture.key(keyboard));
    }
    refresh();
  }

  @Override
  public void gestureEnded() {
    gesture.end();
  }

  /** Recalcula lo que se muestra a partir de la sesión. */
  void refresh() {
    Optional<AiJobService> service = env.session().ai();
    available.set(service.isPresent());
    if (service.isEmpty() || !env.session().hasImage()) {
      canStart.set(false);
      running.set(false);
      state.set(AiJobService.State.NOT_RUN);
      dimensions.set("");
      limit.set("");
      estimate.set("");
      warning.set("");
      notice.set("");
      action.set(m.get("ui.enhance.action.run"));
      return;
    }
    AiJobService ai = service.get();
    AiJobService.State now =
        env.session().enhanceStep().map(l -> ai.state(l.key())).orElse(AiJobService.State.NOT_RUN);
    state.set(now);
    running.set(ai.isRunning());
    Size in = env.session().sizeBeforeEnhance();
    int factor = X4.equals(scale.get()) ? 4 : 2;
    boolean full = ((Number) intensity.value()).doubleValue() >= 100;
    AiLimits.Verdict selected = ai.verdict(in.width(), in.height(), factor, full);
    AiLimits.Verdict x4 = ai.verdict(in.width(), in.height(), 4, full);
    x4Allowed.set(x4 == AiLimits.Verdict.OK);
    dimensions.set(
        m.format(
            "ui.enhance.size",
            in.width(),
            in.height(),
            in.width() * factor,
            in.height() * factor,
            String.format(
                m.locale(),
                "%.1f",
                (double) AiLimits.outputPixels(in.width(), in.height(), factor) / MEGA)));
    limit.set(
        selected != AiLimits.Verdict.OK
            ? limitText(selected, factor)
            : x4 != AiLimits.Verdict.OK ? limitText(x4, 4) : "");
    estimatedSeconds = ai.estimateSeconds(in.width(), in.height());
    estimate.set(m.format("ui.enhance.estimate", timeText(estimatedSeconds)));
    warning.set(
        AiLimits.band(estimatedSeconds) == AiLimits.Band.SHORT
                || AiLimits.band(estimatedSeconds) == AiLimits.Band.NORMAL
            ? ""
            : m.get("ui.enhance.slow"));
    notice.set(
        switch (now) {
          case STALE -> m.get("ui.enhance.stale");
          case READY -> m.get("ui.enhance.ready");
          default -> "";
        });
    action.set(
        now == AiJobService.State.STALE
            ? m.get("ui.enhance.action.again")
            : m.get("ui.enhance.action.run"));
    canStart.set(
        !running.get() && now != AiJobService.State.READY && selected == AiLimits.Verdict.OK);
  }

  private String limitText(AiLimits.Verdict verdict, int factor) {
    return switch (verdict) {
      case OUTPUT_TOO_LARGE ->
          m.format("ui.enhance.limit.output", factor, AiLimits.MAX_OUTPUT_PIXELS / MEGA);
      case NOT_ENOUGH_HEAP -> m.format("ui.enhance.limit.heap", factor);
      case NOT_ENOUGH_PHYSICAL -> m.format("ui.enhance.limit.physical", factor);
      case OK -> "";
    };
  }

  private String timeText(double seconds) {
    return seconds < 60
        ? m.format("ui.enhance.time.seconds", Math.max(1, Math.round(seconds)))
        : m.format("ui.enhance.time.minutes", (long) Math.ceil(seconds / 60));
  }

  // ── Propiedades ───────────────────────────────────────────────────────────

  public ControlState intensity() {
    return intensity;
  }

  public ReadOnlyBooleanProperty availableProperty() {
    return available;
  }

  public ReadOnlyObjectProperty<String> scaleProperty() {
    return scale;
  }

  public ReadOnlyObjectProperty<AiJobService.State> stateProperty() {
    return state;
  }

  public ReadOnlyBooleanProperty runningProperty() {
    return running;
  }

  public ReadOnlyBooleanProperty canStartProperty() {
    return canStart;
  }

  public ReadOnlyBooleanProperty x4AllowedProperty() {
    return x4Allowed;
  }

  public ReadOnlyStringProperty dimensionsProperty() {
    return dimensions;
  }

  public ReadOnlyStringProperty limitProperty() {
    return limit;
  }

  public ReadOnlyStringProperty estimateProperty() {
    return estimate;
  }

  public ReadOnlyStringProperty warningProperty() {
    return warning;
  }

  public ReadOnlyStringProperty noticeProperty() {
    return notice;
  }

  public ReadOnlyStringProperty actionProperty() {
    return action;
  }

  public ReadOnlyDoubleProperty progressProperty() {
    return progress;
  }

  public ReadOnlyStringProperty progressTextProperty() {
    return progressText;
  }

  public IntegerProperty threadsProperty() {
    return threads;
  }
}
