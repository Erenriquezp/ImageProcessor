package io.github.erenriquezp.rora.app.diag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Reloj y publicador manuales: sin hilos reales ni tiempo real. */
class GlobalErrorsTest {

  private final long[] now = {0};
  private final Deque<Runnable> queue = new ArrayDeque<>();
  private final Executor publisher = queue::add;
  private final List<String> events = new ArrayList<>();
  private int released;
  private GlobalErrors errors;

  @BeforeEach
  void setUp() {
    errors = new GlobalErrors(() -> now[0]);
    errors.attach(
        new GlobalErrors.Listener() {
          @Override
          public void onError(Throwable error) {
            events.add("error " + error.getMessage());
          }

          @Override
          public void onOutOfMemory(boolean repeated) {
            events.add("oom repetido=" + repeated);
          }
        },
        publisher,
        () -> released++);
  }

  private void drain() {
    while (!queue.isEmpty()) {
      queue.poll().run();
    }
  }

  @Test
  void anUncaughtErrorIsPublishedOnceWhileItRepeats() {
    Thread t = new Thread(() -> {}, "rora-render");
    errors.uncaughtException(t, new IllegalStateException("roto"));
    assertThat(events).isEmpty(); // aún en el publicador
    drain();
    assertThat(events).containsExactly("error roto");

    now[0] += 1_000_000_000L;
    errors.uncaughtException(t, new IllegalStateException("roto"));
    drain();
    assertThat(events).hasSize(1);

    now[0] += GlobalErrors.SAME_ERROR_NANOS + 1;
    errors.uncaughtException(t, new IllegalStateException("roto"));
    errors.uncaughtException(t, new IllegalArgumentException("otro"));
    drain();
    assertThat(events).containsExactly("error roto", "error roto", "error otro");
    assertThat(errors.lastError()).hasValue("java.lang.IllegalArgumentException: otro");
  }

  @Test
  void outOfMemoryReleasesTheReserveAndWorkAndKeepsGoing() {
    assertThat(errors.hasReserve()).isTrue();
    Thread t = new Thread(() -> {}, "rora-full");
    errors.uncaughtException(t, new OutOfMemoryError("Java heap space"));
    drain();
    assertThat(released).isEqualTo(1);
    assertThat(events).containsExactly("oom repetido=false");
    assertThat(errors.hasReserve()).as("la reserva se recupera").isTrue();
    assertThat(errors.lastError()).hasValue("OutOfMemoryError en rora-full");

    now[0] += 10_000_000_000L;
    errors.uncaughtException(t, new RuntimeException("envuelto", new OutOfMemoryError()));
    now[0] += GlobalErrors.OOM_REPEAT_NANOS + 1;
    errors.uncaughtException(t, new OutOfMemoryError());
    drain();
    assertThat(released).isEqualTo(3);
    assertThat(events)
        .containsExactly("oom repetido=false", "oom repetido=true", "oom repetido=false");
  }

  @Test
  void aFailingReleaserDoesNotHideTheWarning() {
    GlobalErrors g = new GlobalErrors(() -> 0);
    List<Boolean> warned = new ArrayList<>();
    g.attach(
        new GlobalErrors.Listener() {
          @Override
          public void onError(Throwable error) {}

          @Override
          public void onOutOfMemory(boolean repeated) {
            warned.add(repeated);
          }
        },
        Runnable::run,
        () -> {
          throw new IllegalStateException("no se pudo cancelar");
        });
    g.uncaughtException(Thread.currentThread(), new OutOfMemoryError());
    assertThat(warned).containsExactly(false);
  }

  @Test
  void withoutInterfaceItOnlyRecords() {
    GlobalErrors g = new GlobalErrors(() -> 0);
    g.uncaughtException(Thread.currentThread(), new IllegalStateException("antes de la ventana"));
    g.uncaughtException(Thread.currentThread(), new OutOfMemoryError());
    assertThat(g.lastError()).isPresent();
  }

  @Test
  void detectsOutOfMemoryInTheCauseChain() {
    assertThat(GlobalErrors.isOutOfMemory(new OutOfMemoryError())).isTrue();
    assertThat(GlobalErrors.isOutOfMemory(new RuntimeException(new Error(new OutOfMemoryError()))))
        .isTrue();
    assertThat(GlobalErrors.isOutOfMemory(new RuntimeException("no"))).isFalse();
  }
}
