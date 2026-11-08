package io.github.erenriquezp.rora.app.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LatestWinsTest {

  private final ManualExecutor worker = new ManualExecutor();
  private final ManualExecutor publisher = new ManualExecutor();
  private final List<CancellationToken> seen = new ArrayList<>();
  private final LatestWins<Integer, String> lw =
      new LatestWins<>(
          "test",
          worker,
          publisher,
          (i, token) -> {
            seen.add(token);
            if (i < 0) {
              throw new IllegalStateException("negativo " + i);
            }
            token.throwIfCancelled();
            return "r" + i;
          });

  @Test
  void onlyTheLatestIsPublished() {
    List<String> out = new ArrayList<>();
    for (int i = 1; i <= 5; i++) {
      lw.submit(i, out::add, e -> {});
    }
    worker.runAll();
    publisher.runAll();
    assertThat(out).containsExactly("r5");
    // Se ejecutaron solo la primera (ya cancelada) y la última.
    assertThat(seen).hasSize(1).allSatisfy(t -> assertThat(t.isCancelled()).isFalse());
  }

  @Test
  void obsoleteAfterQueuedForPublishingIsDropped() {
    List<String> out = new ArrayList<>();
    lw.submit(1, out::add, e -> {});
    worker.runAll();
    lw.submit(2, out::add, e -> {});
    publisher.runAll();
    assertThat(out).isEmpty();
    worker.runAll();
    publisher.runAll();
    assertThat(out).containsExactly("r2");
  }

  @Test
  void errorsAreDelivered() {
    List<Throwable> errors = new ArrayList<>();
    lw.submit(-1, r -> {}, errors::add);
    worker.runAll();
    publisher.runAll();
    assertThat(errors).singleElement().satisfies(e -> assertThat(e).hasMessage("negativo -1"));
  }

  @Test
  void cancelAllAndClose() {
    List<String> out = new ArrayList<>();
    lw.submit(1, out::add, e -> {});
    lw.cancelAll();
    worker.runAll();
    publisher.runAll();
    assertThat(out).isEmpty();
    lw.close();
    assertThatThrownBy(() -> lw.submit(2, out::add, e -> {}))
        .isInstanceOf(IllegalStateException.class);
  }
}
