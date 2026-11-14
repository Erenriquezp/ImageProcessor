package io.github.erenriquezp.rora.app.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.app.diag.AppInfo;
import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.app.session.ManualExecutor;
import io.github.erenriquezp.rora.app.update.ReleaseFeed;
import io.github.erenriquezp.rora.app.update.UpdateChecker;
import io.github.erenriquezp.rora.app.update.UpdateResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class UpdateViewModelTest {

  private static final Messages M = Messages.of(Locale.of("es"));
  private static final String PAGE = ReleaseFeed.RELEASES_PAGE + "/tag/v0.2.0";

  private final ManualExecutor publisher = new ManualExecutor();
  private final ManualExecutor background = new ManualExecutor();
  private final List<String> browsed = new ArrayList<>();
  private final AtomicInteger fetches = new AtomicInteger();

  private UpdateViewModel vm(UpdateChecker.Source source, boolean enabled) {
    return new UpdateViewModel(
        M,
        publisher,
        background,
        new UpdateChecker(source, enabled),
        new AppInfo("0.1.0", "beta"),
        browsed::add);
  }

  private void drain() {
    while (background.runAll() + publisher.runAll() > 0) {
      // hasta vaciar
    }
  }

  private static String feed(String tag, String date) {
    return "[{\"tag_name\":\""
        + tag
        + "\",\"draft\":false,\"html_url\":\""
        + ReleaseFeed.RELEASES_PAGE
        + "/tag/"
        + tag
        + "\",\"published_at\":\""
        + date
        + "\"}]";
  }

  @Test
  void nothingHappensUntilTheUserAsks() {
    UpdateViewModel v =
        vm(
            () -> {
              fetches.incrementAndGet();
              return "[]";
            },
            true);
    drain();
    assertThat(fetches).hasValue(0);
    assertThat(v.visibleProperty().get()).isFalse();
    v.check();
    assertThat(v.stateProperty().get()).isEqualTo(UpdateViewModel.State.CHECKING);
    assertThat(v.messageProperty().get()).isEqualTo(M.get("ui.update.checking"));
    v.check(); // una sola a la vez
    drain();
    assertThat(fetches).hasValue(1);
    assertThat(v.stateProperty().get()).isEqualTo(UpdateViewModel.State.UP_TO_DATE);
    assertThat(v.messageProperty().get()).isEqualTo(M.format("ui.update.upToDate", "0.1.0"));
  }

  @Test
  void aNewVersionOpensItsPageOnlyWhenAsked() {
    UpdateViewModel v = vm(() -> feed("v0.2.0", "2027-01-10T00:00:00Z"), true);
    v.check();
    drain();
    assertThat(v.stateProperty().get()).isEqualTo(UpdateViewModel.State.NEW_VERSION);
    assertThat(v.messageProperty().get())
        .isEqualTo(M.format("ui.update.available", "0.2.0", "0.1.0", "2027-01-10"));
    assertThat(browsed).isEmpty();
    v.openRelease();
    assertThat(browsed).containsExactly(PAGE);
  }

  @Test
  void majorAndUndated() {
    UpdateViewModel major = vm(() -> feed("v1.0.0", "2027-01-10T00:00:00Z"), true);
    major.check();
    drain();
    assertThat(major.majorChangeProperty().get()).isTrue();
    assertThat(major.messageProperty().get())
        .isEqualTo(M.format("ui.update.major", "1.0.0", "0.1.0"));
    UpdateViewModel undated = vm(() -> feed("v0.2.0", ""), true);
    undated.check();
    drain();
    assertThat(undated.messageProperty().get())
        .isEqualTo(M.format("ui.update.availableUndated", "0.2.0", "0.1.0"));
  }

  @Test
  void failuresExplainThemselvesAndOfferTheReleasesPage() {
    for (UpdateResult.Reason r :
        List.of(
            UpdateResult.Reason.NO_NETWORK,
            UpdateResult.Reason.TIMEOUT,
            UpdateResult.Reason.RATE_LIMITED)) {
      UpdateViewModel v =
          vm(
              () -> {
                throw new UpdateChecker.SourceException(r, "x");
              },
              true);
      v.check();
      drain();
      assertThat(v.stateProperty().get()).isEqualTo(UpdateViewModel.State.FAILED);
    }
    UpdateViewModel bad = vm(() -> "no json", true);
    bad.check();
    drain();
    assertThat(bad.messageProperty().get()).isEqualTo(M.get("ui.update.invalid"));
    bad.openReleasesPage();
    assertThat(browsed).containsExactly(ReleaseFeed.RELEASES_PAGE);
  }

  @Test
  void closingDiscardsALateAnswer() {
    UpdateViewModel v = vm(() -> feed("v0.2.0", "2027-01-10T00:00:00Z"), true);
    v.check();
    v.close();
    drain();
    assertThat(v.visibleProperty().get()).isFalse();
    assertThat(v.stateProperty().get()).isEqualTo(UpdateViewModel.State.IDLE);
  }

  @Test
  void disabledDoesNothing() {
    UpdateViewModel v =
        vm(
            () -> {
              fetches.incrementAndGet();
              return "[]";
            },
            false);
    assertThat(v.enabled()).isFalse();
    v.check();
    drain();
    assertThat(fetches).hasValue(0);
    assertThat(v.visibleProperty().get()).isFalse();
  }
}
