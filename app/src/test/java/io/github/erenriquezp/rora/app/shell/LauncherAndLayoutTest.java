package io.github.erenriquezp.rora.app.shell;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LauncherAndLayoutTest {

  @Test
  void theNewUiNeedsTheFlag() {
    assertThat(Launcher.wantsNewUi(new String[0], null)).isFalse();
    assertThat(Launcher.wantsNewUi(new String[] {"--ui=next"}, null)).isTrue();
    assertThat(Launcher.wantsNewUi(new String[0], "next")).isTrue();
    assertThat(Launcher.wantsNewUi(new String[] {"--ui=old"}, "old")).isFalse();
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
