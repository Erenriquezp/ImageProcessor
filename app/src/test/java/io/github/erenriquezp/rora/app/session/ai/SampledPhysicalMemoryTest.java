package io.github.erenriquezp.rora.app.session.ai;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.app.session.ManualExecutor;
import org.junit.jupiter.api.Test;

class SampledPhysicalMemoryTest {

  private final ManualExecutor background = new ManualExecutor();
  private long now;
  private long value = 1000;
  private int reads;

  private final SampledPhysicalMemory memory =
      new SampledPhysicalMemory(
          () -> {
            reads++;
            return value;
          },
          background,
          () -> now);

  @Test
  void theCallerNeverReadsTheSystemItself() {
    assertThat(memory.freeBytes()).isEqualTo(Long.MAX_VALUE);
    assertThat(reads).isZero();
    background.runAll();
    assertThat(reads).isEqualTo(1);
    assertThat(memory.freeBytes()).isEqualTo(1000);
  }

  @Test
  void aFreshValueIsNotReadAgain() {
    memory.freeBytes();
    background.runAll();
    now += SampledPhysicalMemory.MAX_AGE_NANOS;
    memory.freeBytes();
    assertThat(background.runAll()).isZero();
    assertThat(reads).isEqualTo(1);
  }

  @Test
  void anOldValueIsServedWhileANewOneIsRead() {
    memory.freeBytes();
    background.runAll();
    value = 5;
    now += SampledPhysicalMemory.MAX_AGE_NANOS + 1;
    assertThat(memory.freeBytes()).isEqualTo(1000);
    background.runAll();
    assertThat(memory.freeBytes()).isEqualTo(5);
  }

  @Test
  void repeatedCallsDoNotQueueSeveralReads() {
    memory.freeBytes();
    memory.freeBytes();
    memory.freeBytes();
    assertThat(background.runAll()).isEqualTo(1);
  }
}
