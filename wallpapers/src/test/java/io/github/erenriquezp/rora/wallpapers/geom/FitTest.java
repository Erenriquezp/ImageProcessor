package io.github.erenriquezp.rora.wallpapers.geom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset;
import io.github.erenriquezp.rora.wallpapers.preset.PresetCatalog;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FitTest {

  /** Fuentes de la matriz de la fase (ancho, alto). */
  static final int[][] SOURCES = {
    {1, 1}, {7, 1}, {640, 480}, {3000, 2000}, {8000, 1200}, {1200, 8000}, {3840, 2160}
  };

  /** Destinos: todos los presets integrados y personalizados extremos. */
  static List<int[]> targets() {
    List<int[]> out = new ArrayList<>();
    for (DevicePreset p : PresetCatalog.builtIns()) {
      out.add(new int[] {p.width(), p.height()});
    }
    out.add(new int[] {16, 16});
    out.add(new int[] {16384, 64});
    out.add(new int[] {64, 16384});
    out.add(new int[] {DevicePreset.MAX_SIDE, DevicePreset.MAX_SIDE});
    return out;
  }

  @Test
  void coverIsInsideTheSourceWithTheTargetProportion() {
    for (int[] s : SOURCES) {
      for (int[] t : targets()) {
        FrameRect f = Fit.cover(s[0], s[1], t[0], t[1]);
        String what = s[0] + "x" + s[1] + " -> " + t[0] + "x" + t[1];

        assertThat(f.insideOf(s[0], s[1])).as(what).isTrue();
        assertThat(f.w() * t[1]).as(what).isCloseTo(f.h() * t[0], offset(1e-9 * f.w() * t[1]));
        assertThat(f.w() == s[0] || f.h() == s[1]).as(what + ": lado limitante exacto").isTrue();
        assertThat(f.centerX()).as(what).isCloseTo(s[0] / 2.0, offset(1e-9 * s[0]));
        assertThat(f.centerY()).as(what).isCloseTo(s[1] / 2.0, offset(1e-9 * s[1]));
      }
    }
  }

  @Test
  void coverOfTheSameProportionIsTheWholeSourceExactly() {
    assertThat(Fit.cover(1920, 1080, 3840, 2160)).isEqualTo(new FrameRect(0, 0, 1920, 1080));
    assertThat(Fit.cover(1179, 2556, 1179, 2556)).isEqualTo(new FrameRect(0, 0, 1179, 2556));
    assertThat(Fit.cover(3, 7, 300, 700)).isEqualTo(new FrameRect(0, 0, 3, 7));
  }

  @Test
  void containFitsInsideTheOutputCentred() {
    for (int[] s : SOURCES) {
      for (int[] t : targets()) {
        IntRect c = Fit.contain(s[0], s[1], t[0], t[1]);
        String what = s[0] + "x" + s[1] + " -> " + t[0] + "x" + t[1];

        assertThat(c.x()).as(what).isGreaterThanOrEqualTo(0);
        assertThat(c.y()).as(what).isGreaterThanOrEqualTo(0);
        assertThat(c.right()).as(what).isLessThanOrEqualTo(t[0]);
        assertThat(c.bottom()).as(what).isLessThanOrEqualTo(t[1]);
        assertThat(c.w() == t[0] || c.h() == t[1]).as(what + ": toca dos bordes").isTrue();
        assertThat(Math.abs(t[0] - c.right() - c.x())).as(what).isLessThanOrEqualTo(1);
        // Deformación por redondeo: menos de medio píxel por eje (o la fuente es de 1 px).
        double s2 = Math.min((double) t[0] / s[0], (double) t[1] / s[1]);
        assertThat(Math.abs(c.w() - s[0] * s2)).as(what).isLessThanOrEqualTo(0.5 + 1e-9);
        assertThat(Math.abs(c.h() - s[1] * s2)).as(what).isLessThanOrEqualTo(0.5 + 1e-9);
      }
    }
  }

  @Test
  void lostAreaAndScale() {
    FrameRect f = Fit.cover(3840, 2160, 1080, 2400);
    assertThat(Fit.lostArea(3840, 2160, f))
        .isCloseTo(1 - (2160 * 1080.0 / 2400) / 3840, offset(1e-12));
    assertThat(Fit.lostArea(100, 100, new FrameRect(0, 0, 100, 100))).isZero();
    assertThat(Fit.scale(f, 1080)).isCloseTo(2400.0 / 2160, offset(1e-12));
  }

  @Test
  void samplingConventionMapsPixelCentres() {
    assertThat(Fit.sourceIndex(0, 0, 10, 10)).isZero();
    assertThat(Fit.sourceIndex(9, 0, 10, 10)).isEqualTo(9);
    assertThat(Fit.sourceIndex(0, 0, 10, 20)).isEqualTo(-0.25);
    assertThat(Fit.sourceIndex(0, 0, 10, 5)).isEqualTo(0.5);
    assertThat(Fit.sourceIndex(0, 2.5, 1, 1)).isEqualTo(2.5);
  }

  @Test
  void frameRectHelpers() {
    FrameRect f = new FrameRect(-5, 90, 50, 20);
    assertThat(f.insideOf(100, 100)).isFalse();
    assertThat(f.clampedInto(100, 100)).isEqualTo(new FrameRect(0, 80, 50, 20));
    assertThat(f.normalized(100, 200).denormalized(100, 200)).isEqualTo(f);
    assertThatThrownBy(() -> new FrameRect(0, 0, 0, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FrameRect(Double.NaN, 0, 1, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FrameRect(0, 0, 200, 10).clampedInto(100, 100))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
