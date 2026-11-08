package io.github.erenriquezp.rora.app.session;

import static io.github.erenriquezp.rora.app.session.ManualScheduler.ms;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.Renderer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link RenderLane} con tiempo, worker y publicador manuales: sin hilos ni esperas. */
class RenderLaneTest {

  private final OomOrFail failing = new OomOrFail();
  private final OpRegistry reg = TestOps.registry(new TestOps.FailingOp(failing));
  private ManualScheduler time;
  private ManualExecutor worker;
  private ManualExecutor publisher;
  private Renderer renderer;
  private RenderLane lane;

  /** Error configurable para {@code test.fail}. */
  static final class OomOrFail implements java.util.function.Supplier<Throwable> {
    Throwable next = new IllegalStateException("roto");

    @Override
    public Throwable get() {
      return next;
    }
  }

  /** Oyente que registra lo recibido. */
  static final class Recorder implements RenderLane.Listener {
    final List<Renderer.Result> done = new ArrayList<>();
    final List<RenderFailure> failed = new ArrayList<>();
    final List<Double> progress = new ArrayList<>();

    @Override
    public void onDone(Renderer.Result r) {
      done.add(r);
    }

    @Override
    public void onFailed(RenderFailure f) {
      failed.add(f);
    }

    @Override
    public void onProgress(double fraction) {
      progress.add(fraction);
    }
  }

  @BeforeEach
  void setUp() {
    time = new ManualScheduler();
    worker = new ManualExecutor();
    publisher = new ManualExecutor();
    renderer = TestOps.renderer(reg);
    lane =
        new RenderLane(
            "test", renderer, TileExecutor.sequential(), worker, time, publisher, ms(30));
  }

  private Renderer.Request bright(int v) {
    return TestOps.request(List.of(TestOps.brightness(reg, v)));
  }

  private int brightnessOf(Renderer.Result r) {
    return r.key().step().params().getInt("value");
  }

  @Test
  void latestWinsOnlyTheLastRequestIsRenderedAndPublished() {
    Recorder rec = new Recorder();
    for (int v = 1; v <= 10; v++) {
      lane.submit(bright(v), rec, false);
    }
    // El 1 arrancó (carril libre); 2..9 se sustituyeron en la cola; el 1 quedó cancelado.
    assertThat(worker.size()).isEqualTo(1);
    worker.runAll();
    publisher.runAll();
    assertThat(rec.done).isEmpty();
    // El 10 espera al debounce desde el arranque anterior.
    time.advance(ms(29));
    assertThat(worker.size()).isZero();
    time.advance(ms(1));
    assertThat(worker.size()).isEqualTo(1);
    worker.runAll();
    publisher.runAll();
    assertThat(rec.done).hasSize(1);
    assertThat(brightnessOf(rec.done.get(0))).isEqualTo(10);
    assertThat(rec.failed).isEmpty();
    assertThat(lane.isIdle()).isTrue();
  }

  @Test
  void debounceStartsImmediatelyWhenIdleAndLongEnoughSinceLastStart() {
    Recorder rec = new Recorder();
    lane.submit(bright(1), rec, false);
    assertThat(worker.size()).as("arranque inmediato").isEqualTo(1);
    worker.runAll();
    publisher.runAll();
    time.advance(ms(10));
    lane.submit(bright(2), rec, false);
    assertThat(worker.size()).as("dentro del debounce").isZero();
    time.advance(ms(19));
    assertThat(worker.size()).isZero();
    time.advance(ms(1));
    assertThat(worker.size()).isEqualTo(1);
    worker.runAll();
    publisher.runAll();
    time.advance(ms(100));
    lane.submit(bright(3), rec, false);
    assertThat(worker.size()).as("pasado el debounce: inmediato").isEqualTo(1);
    worker.runAll();
    publisher.runAll();
    assertThat(rec.done).extracting(this::brightnessOf).containsExactly(1, 2, 3);
  }

  @Test
  void obsoleteResultIsDroppedEvenAfterItWasQueuedForPublishing() {
    Recorder rec = new Recorder();
    lane.submit(bright(1), rec, false);
    worker.runAll();
    assertThat(publisher.size()).as("resultado (y progreso) en cola").isPositive();
    lane.submit(bright(2), rec, false); // sustituye al 1 antes de publicarlo
    publisher.runAll();
    assertThat(rec.done).isEmpty();
    time.advance(ms(30));
    worker.runAll();
    publisher.runAll();
    assertThat(rec.done).extracting(this::brightnessOf).containsExactly(2);
  }

  @Test
  void sameKeyJoinsInsteadOfCancelling() {
    Recorder a = new Recorder();
    Recorder b = new Recorder();
    lane.submit(bright(5), a, false);
    lane.submit(bright(5), b, false);
    assertThat(worker.size()).isEqualTo(1);
    worker.runAll();
    publisher.runAll();
    assertThat(a.done).hasSize(1);
    assertThat(b.done).hasSize(1);
    assertThat(a.done.get(0)).isSameAs(b.done.get(0));
  }

  @Test
  void ticketCancelOnlyAffectsItsListener() {
    Recorder a = new Recorder();
    Recorder b = new Recorder();
    RenderLane.Ticket ta = lane.submit(bright(5), a, false);
    lane.submit(bright(5), b, false);
    ta.cancel();
    worker.runAll();
    publisher.runAll();
    assertThat(a.done).isEmpty();
    assertThat(b.done).hasSize(1);
    // Único oyente: el trabajo se abandona.
    Recorder c = new Recorder();
    time.advance(ms(30));
    RenderLane.Ticket tc = lane.submit(bright(6), c, false);
    tc.cancel();
    worker.runAll();
    publisher.runAll();
    assertThat(c.done).isEmpty();
    assertThat(lane.isIdle()).isTrue();
  }

  @Test
  void protectedRequestsAreNeverSupersededAndGoFirst() {
    Recorder exp = new Recorder();
    Recorder inter = new Recorder();
    lane.submit(bright(1), inter, false); // arranca
    lane.submit(bright(2), exp, true); // exportación: no cancela la interactiva en curso
    lane.submit(bright(3), inter, false); // sustituye a la interactiva en curso (1)
    worker.runAll(); // termina 1 (descartada)
    time.advance(ms(30));
    worker.runAll(); // exportación primero
    time.advance(ms(30));
    worker.runAll(); // luego la interactiva 3
    publisher.runAll();
    assertThat(exp.done).extracting(this::brightnessOf).containsExactly(2);
    assertThat(inter.done).extracting(this::brightnessOf).containsExactly(3);
  }

  @Test
  void errorsArePublishedNotSwallowed() {
    Recorder rec = new Recorder();
    lane.submit(TestOps.request(List.of(TestOps.spec(reg, "test.fail"))), rec, false);
    worker.runAll();
    publisher.runAll();
    assertThat(rec.done).isEmpty();
    assertThat(rec.failed).hasSize(1);
    RenderFailure f = rec.failed.get(0);
    assertThat(f.cause()).isSameAs(failing.next);
    assertThat(f.kind()).isEqualTo(RenderFailure.Kind.OPERATION_FAILED);
    assertThat(f.message()).contains("roto");
  }

  @Test
  void outOfMemoryClearsTheCacheAndIsReported() {
    Recorder rec = new Recorder();
    lane.submit(bright(1), rec, false);
    worker.runAll();
    publisher.runAll();
    assertThat(renderer.cache().weightBytes()).isPositive();
    failing.next = new OutOfMemoryError("simulado");
    time.advance(ms(30));
    lane.submit(TestOps.request(List.of(TestOps.spec(reg, "test.fail"))), rec, false);
    worker.runAll();
    publisher.runAll();
    assertThat(rec.failed)
        .singleElement()
        .satisfies(f -> assertThat(f.kind()).isEqualTo(RenderFailure.Kind.OUT_OF_MEMORY));
    assertThat(renderer.cache().weightBytes()).isZero();
    assertThat(rec.failed.get(0).message()).contains("Memoria insuficiente");
  }

  @Test
  void invalidParamsAreClassified() {
    assertThat(RenderFailure.of(new IllegalArgumentException("x")).kind())
        .isEqualTo(RenderFailure.Kind.INVALID_PARAMS);
    assertThat(RenderFailure.of(new IllegalArgumentException("x")).message()).contains("x");
    assertThat(RenderFailure.of(new IllegalStateException()).message())
        .isEqualTo("IllegalStateException");
  }

  @Test
  void progressIsPublishedAndBounded() {
    Recorder rec = new Recorder();
    lane.submit(bright(1), rec, false);
    worker.runAll();
    publisher.runAll();
    assertThat(rec.progress).isNotEmpty().allSatisfy(p -> assertThat(p).isBetween(0.0, 1.0));
    // Con el reloj quieto solo puede salir una actualización (≤ 30 Hz).
    assertThat(rec.progress).hasSize(1);
  }

  @Test
  void closeCancelsEverythingAndRejectsNewRequests() {
    Recorder rec = new Recorder();
    lane.submit(bright(1), rec, false);
    lane.submit(bright(2), rec, true);
    lane.close();
    lane.close();
    worker.runAll();
    publisher.runAll();
    time.advance(ms(100));
    worker.runAll();
    assertThat(rec.done).isEmpty();
    assertThat(time.pendingTasks()).isZero();
    assertThatThrownBy(() -> lane.submit(bright(3), rec, false))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void prepareRunsInTheWorkerAndItsValueIsPublished() {
    List<String> seen = new ArrayList<>();
    Thread[] prepareThread = new Thread[1];
    RenderLane.Listener l =
        new RenderLane.Listener() {
          @Override
          public void onDone(Renderer.Result r) {
            throw new AssertionError("debe llamarse la variante con prepared");
          }

          @Override
          public Object prepare(Renderer.Result r) {
            prepareThread[0] = Thread.currentThread();
            return "w" + r.image().width();
          }

          @Override
          public void onDone(Renderer.Result r, Object prepared) {
            seen.add((String) prepared);
          }
        };
    lane.submit(bright(1), l, false);
    assertThat(seen).isEmpty();
    worker.runAll();
    assertThat(prepareThread[0]).isSameAs(Thread.currentThread());
    assertThat(seen).as("aún no publicado").isEmpty();
    publisher.runAll();
    assertThat(seen).containsExactly("w16");
  }

  @Test
  void failureInPrepareIsReportedToThatListener() {
    Recorder rec = new Recorder();
    RenderLane.Listener bad =
        new RenderLane.Listener() {
          @Override
          public void onDone(Renderer.Result r) {}

          @Override
          public Object prepare(Renderer.Result r) {
            throw new IllegalStateException("conversión");
          }

          @Override
          public void onFailed(RenderFailure f) {
            rec.failed.add(f);
          }
        };
    lane.submit(bright(1), bad, false);
    lane.submit(bright(1), rec, false);
    worker.runAll();
    publisher.runAll();
    assertThat(rec.failed)
        .singleElement()
        .satisfies(f -> assertThat(f.cause()).hasMessage("conversión"));
    assertThat(rec.done).hasSize(1);
  }

  @Test
  void differentSlotsDoNotSupersedeEachOtherAndRunByPriority() {
    RenderLane.Slot low = new RenderLane.Slot("low", 1);
    RenderLane.Slot high = new RenderLane.Slot("high", 5);
    Recorder a = new Recorder();
    Recorder b = new Recorder();
    Recorder c = new Recorder();
    lane.submit(bright(1), a, low); // arranca
    lane.submit(bright(2), b, low); // sustituye a 1 en su ranura
    lane.submit(bright(3), c, high);
    worker.runAll(); // 1, descartada
    time.advance(ms(30));
    worker.runAll(); // 3 por prioridad
    time.advance(ms(30));
    worker.runAll(); // 2
    publisher.runAll();
    assertThat(a.done).isEmpty();
    assertThat(b.done).extracting(this::brightnessOf).containsExactly(2);
    assertThat(c.done).extracting(this::brightnessOf).containsExactly(3);
    assertThat(lane.isIdle()).isTrue();
  }

  @Test
  void supersedingOneSlotKeepsListenersOfOtherSlotsOnTheSameRender() {
    RenderLane.Slot s1 = new RenderLane.Slot("a", 1);
    RenderLane.Slot s2 = new RenderLane.Slot("b", 1);
    Recorder a = new Recorder();
    Recorder b = new Recorder();
    Recorder other = new Recorder();
    lane.submit(bright(5), a, s1);
    lane.submit(bright(5), b, s2); // se une
    lane.submit(bright(6), other, s1); // sustituye solo a "a"
    worker.runAll();
    publisher.runAll();
    assertThat(a.done).isEmpty();
    assertThat(b.done).extracting(this::brightnessOf).containsExactly(5);
    time.advance(ms(30));
    worker.runAll();
    publisher.runAll();
    assertThat(other.done).extracting(this::brightnessOf).containsExactly(6);
  }

  @Test
  void exportJoinedToAnInteractiveRenderSurvivesItsReplacement() {
    Recorder inter = new Recorder();
    Recorder exp = new Recorder();
    lane.submit(bright(5), inter, false);
    lane.submit(bright(5), exp, true); // se une al render en curso
    lane.submit(bright(6), inter, false);
    worker.runAll();
    publisher.runAll();
    assertThat(exp.done).extracting(this::brightnessOf).containsExactly(5);
  }

  @Test
  void rejectsNegativeDebounce() {
    assertThatThrownBy(
            () ->
                new RenderLane(
                    "x", renderer, TileExecutor.sequential(), worker, time, publisher, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
