package io.github.erenriquezp.rora.core.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.CancelledException;
import org.junit.jupiter.api.Test;

class LiveHistogramTest {

  private static LiveHistogram of(int... argb) {
    return LiveHistogram.compute(Img.copyOf(argb.length, 1, argb), CancellationToken.none());
  }

  @Test
  void countsEveryChannelAndTheLuminance() {
    LiveHistogram h = of(0xFF102030);
    assertThat(h.red()[0x10]).isEqualTo(1);
    assertThat(h.green()[0x20]).isEqualTo(1);
    assertThat(h.blue()[0x30]).isEqualTo(1);
    assertThat(h.luma()[(2126 * 0x10 + 7152 * 0x20 + 722 * 0x30 + 5000) / 10000]).isEqualTo(1);
  }

  @Test
  void aPixelIsClippedWhenAnyChannelReachesTheLimit() {
    LiveHistogram h = of(0xFFFF8080, 0xFF808000, 0xFF808080, 0xFF808080);
    assertThat(h.highlightsPct()).isEqualTo(25.0);
    assertThat(h.shadowsPct()).isEqualTo(25.0);
  }

  @Test
  void transparentPixelsDoNotCount() {
    LiveHistogram h = of(0x00FFFFFF, 0xFF808080);
    assertThat(h.counted()).isEqualTo(1);
    assertThat(h.highlightsPct()).isZero();
  }

  @Test
  void anImageWithoutVisiblePixelsHasNoClipping() {
    LiveHistogram h = of(0x00000000);
    assertThat(h.counted()).isZero();
    assertThat(h.shadowsPct()).isZero();
  }

  @Test
  void aCancelledTokenStopsTheCalculation() {
    CancellationToken t = CancellationToken.create();
    t.cancel();
    Img img = Img.copyOf(1, 1, new int[] {0});
    assertThatThrownBy(() -> LiveHistogram.compute(img, t)).isInstanceOf(CancelledException.class);
  }
}
