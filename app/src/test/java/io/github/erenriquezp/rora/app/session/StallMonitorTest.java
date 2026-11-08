package io.github.erenriquezp.rora.app.session;

import static io.github.erenriquezp.rora.app.session.ManualScheduler.ms;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link StallMonitor} con tiempo virtual y un "hilo FX" manual. */
class StallMonitorTest {

  private final ManualScheduler time = new ManualScheduler();
  private final ManualExecutor ui = new ManualExecutor();
  private final List<String> reports = new ArrayList<>();
  private final StallMonitor monitor =
      new StallMonitor(time, ui, () -> "TRAZA", reports::add, ms(50), ms(100));

  @Test
  void aResponsiveThreadReportsNothing() {
    monitor.start();
    for (int i = 0; i < 20; i++) {
      time.advance(ms(50));
      ui.runAll();
    }
    assertThat(reports).isEmpty();
  }

  @Test
  void aStallIsReportedOnceWithTheStackAndThenWithItsDuration() {
    monitor.start();
    time.advance(ms(50)); // latido enviado; el hilo no lo atiende
    time.advance(ms(200));
    assertThat(reports).hasSize(1);
    assertThat(reports.get(0)).contains("bloqueado desde").contains("TRAZA");
    time.advance(ms(200));
    assertThat(reports).as("una sola traza por bloqueo").hasSize(1);
    ui.runAll();
    assertThat(reports).hasSize(2);
    assertThat(reports.get(1)).contains("400 ms");
  }

  @Test
  void aPauseJustUnderTheThresholdIsNotReported() {
    monitor.start();
    time.advance(ms(50));
    time.advance(ms(100));
    ui.runAll();
    assertThat(reports).isEmpty();
  }

  @Test
  void closeStopsTheHeartbeat() {
    monitor.start();
    monitor.close();
    time.advance(ms(500));
    assertThat(ui.size()).isZero();
    assertThat(time.pendingTasks()).isZero();
  }
}
