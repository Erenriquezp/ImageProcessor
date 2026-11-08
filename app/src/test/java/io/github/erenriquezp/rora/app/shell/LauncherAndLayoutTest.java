package io.github.erenriquezp.rora.app.shell;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LauncherAndLayoutTest {

  @Test
  void theLabNeedsItsFlag() {
    assertThat(StudioApp.labRequested(java.util.List.of())).isFalse();
    assertThat(StudioApp.labRequested(java.util.List.of("--lab"))).isTrue();
  }

  @Test
  void theMemoryStoreKeepsValuesAndFallsBack() {
    LayoutStore s = LayoutStore.inMemory();
    assertThat(s.getDouble("a", 7)).isEqualTo(7);
    s.putDouble("a", 3);
    assertThat(s.getDouble("a", 7)).isEqualTo(3);
    assertThat(s.getString("b", "x")).isEqualTo("x");
    s.putString("b", "y");
    assertThat(s.getString("b", "x")).isEqualTo("y");
  }
}
