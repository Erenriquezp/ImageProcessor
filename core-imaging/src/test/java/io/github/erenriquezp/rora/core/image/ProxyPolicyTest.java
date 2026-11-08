package io.github.erenriquezp.rora.core.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.core.image.ProxyPolicy.Decision;
import io.github.erenriquezp.rora.core.image.ProxyPolicy.Size;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProxyPolicyTest {

  private static final ProxyPolicy P = ProxyPolicy.DEFAULT;
  private static final Size MP24 = new Size(6000, 4000);

  @Test
  void typicalViewport() {
    Decision d = P.decide(MP24, 1600, 900, 1.0, 1.0, Optional.empty());
    // 1600 -> 1792 (múltiplo de 256); s = 1792/6000.
    assertThat(d.proxy()).contains(new Size(1792, 1195));
    assertThat(d.fullNeeded()).isFalse();
  }

  @Test
  void dpiScaleRaisesTheRequestedSide() {
    Decision d = P.decide(MP24, 1600, 900, 1.25, 1.0, Optional.empty());
    assertThat(d.proxy().orElseThrow().longSide()).isEqualTo(2048);
  }

  @Test
  void smallImageHasNoProxy() {
    assertThat(P.decide(new Size(1000, 800), 1600, 900, 1, 1, Optional.empty()).proxy()).isEmpty();
    // Escala 0,8 >= 0,75: tampoco.
    assertThat(P.decide(new Size(2240, 1000), 1792, 900, 1, 1, Optional.empty()).proxy()).isEmpty();
  }

  @Test
  void pixelCapAndZoomBeyondTheProxyNeedTheFullRender() {
    Decision d = P.decide(MP24, 1600, 900, 1.0, 4.0, Optional.empty());
    Size s = d.proxy().orElseThrow();
    assertThat(s.pixels()).isLessThanOrEqualTo(4_000_000L);
    assertThat(s).isEqualTo(new Size(2449, 1633));
    assertThat(d.fullNeeded()).isTrue();
    // Con el proxy en el tope, más zoom no lo cambia.
    Decision again = P.decide(MP24, 1600, 900, 1.0, 8.0, d.proxy());
    assertThat(again.proxy()).isEqualTo(d.proxy());
    assertThat(again.fullNeeded()).isTrue();
  }

  @Test
  void hysteresis() {
    Optional<Size> cur = P.decide(MP24, 1600, 900, 1, 1, Optional.empty()).proxy();
    // Pequeños resizes dentro del mismo múltiplo de 256, o hacia abajo hasta /1,5: se conserva.
    assertThat(P.decide(MP24, 1700, 900, 1, 1, cur).proxy()).isEqualTo(cur);
    assertThat(P.decide(MP24, 1500, 900, 1, 1, cur).proxy()).isEqualTo(cur);
    assertThat(P.decide(MP24, 1200, 900, 1, 1, cur).proxy()).isEqualTo(cur);
    // Mucho más pequeño: proxy nuevo más pequeño.
    assertThat(P.decide(MP24, 1000, 600, 1, 1, cur).proxy().orElseThrow().longSide())
        .isEqualTo(1024);
    // Más grande: proxy nuevo.
    assertThat(P.decide(MP24, 1900, 900, 1, 1, cur).proxy().orElseThrow().longSide())
        .isEqualTo(2048);
  }

  @Test
  void invalidInputs() {
    assertThatThrownBy(() -> P.decide(MP24, 0, 900, 1, 1, Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> P.decide(MP24, 10, 900, 1, 0.5, Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ProxyPolicy(0, 0.75, 256, 1.5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ProxyPolicy(10, 0.75, 256, 0.5))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Size(0, 1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void onePixelImage() {
    assertThat(P.decide(new Size(1, 1), 800, 600, 1, 1, Optional.empty()).proxy()).isEmpty();
  }
}
