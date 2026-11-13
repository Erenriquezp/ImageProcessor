package io.github.erenriquezp.rora.wallpapers.preset;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset.Group;
import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset.Orientation;
import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset.Origin;
import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset.Status;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PresetCatalogTest {

  static DevicePreset preset(String id, Group g, int w, int h, Origin origin) {
    return new DevicePreset(
        id,
        "Nombre " + id,
        g,
        w,
        h,
        Orientation.of(w, h),
        origin == Origin.USER ? Status.USER : Status.STANDARD,
        "prueba",
        Optional.empty(),
        List.of(),
        0,
        origin);
  }

  @Test
  void builtInsAreValidAndUnique() {
    List<DevicePreset> b = PresetCatalog.builtIns();

    assertThat(b).hasSize(35);
    Set<String> ids = new HashSet<>();
    for (DevicePreset p : b) {
      assertThat(ids.add(p.id())).as("id único " + p.id()).isTrue();
      assertThat(p.origin()).isEqualTo(Origin.BUILT_IN);
      assertThat(p.orientation()).isEqualTo(Orientation.of(p.width(), p.height()));
      assertThat(p.status()).isNotEqualTo(Status.USER);
      for (SafeZone z : p.safeZones()) {
        assertThat(z.x() + z.w()).isLessThanOrEqualTo(1.0 + 1e-9);
        assertThat(z.y() + z.h()).isLessThanOrEqualTo(1.0 + 1e-9);
      }
    }
  }

  @Test
  void builtInGroupsHaveCoherentProportions() {
    for (DevicePreset p : PresetCatalog.builtIns()) {
      double a = p.aspect();
      switch (p.group()) {
        case PC_16_9 -> assertThat(a).as(p.id()).isCloseTo(16.0 / 9, within(0.01));
        case PC_16_10 -> assertThat(a).as(p.id()).isBetween(1.5, 1.6 + 0.01);
        case ULTRAWIDE -> assertThat(a).as(p.id()).isGreaterThan(2.3);
        case LAPTOP_3_2 -> assertThat(a).as(p.id()).isCloseTo(1.5, within(0.01));
        case PHONE -> assertThat(1 / a).as(p.id()).isBetween(2.0, 2.3);
        case TABLET -> assertThat(1 / a).as(p.id()).isBetween(1.3, 1.65);
        default -> throw new AssertionError("grupo inesperado en integrados: " + p.group());
      }
    }
  }

  private static org.assertj.core.data.Offset<Double> within(double d) {
    return org.assertj.core.data.Offset.offset(d);
  }

  @Test
  void verifiedPresetsCiteAnOfficialPageAndADate() {
    for (DevicePreset p : PresetCatalog.builtIns()) {
      if (p.status() == Status.VERIFIED) {
        assertThat(p.source()).as(p.id()).startsWith("https://");
        assertThat(p.reviewed()).as(p.id()).isPresent();
        assertThat(p.reviewed().get()).isAfterOrEqualTo(LocalDate.of(2026, 11, 12));
      }
    }
  }

  @Test
  void mergesSourcesDroppingDuplicatesWithAWarning() {
    DevicePreset screen = preset("screen-1", Group.SCREEN, 2560, 1440, Origin.SCREEN);
    DevicePreset a = preset("pc-x", Group.PC_16_9, 1920, 1080, Origin.BUILT_IN);
    DevicePreset dup = preset("pc-x", Group.PC_16_9, 3840, 2160, Origin.BUILT_IN);
    DevicePreset mine = preset("user:mio", Group.CUSTOM, 1000, 1000, Origin.USER);
    PresetProblem earlier = new PresetProblem("x.json", "", "roto");

    PresetCatalog c =
        PresetCatalog.of(List.of(screen), List.of(a, dup), List.of(mine), List.of(earlier));

    assertThat(c.all())
        .extracting(DevicePreset::id)
        .containsExactly("screen-1", "pc-x", "user:mio");
    assertThat(c.byId("pc-x")).contains(a);
    assertThat(c.problems()).hasSize(2).first().isEqualTo(earlier);
  }

  @Test
  void ordersByGroupThenArea() {
    PresetCatalog c = PresetCatalog.of(List.of(), PresetCatalog.builtIns(), List.of(), List.of());
    List<DevicePreset> all = c.all();
    for (int i = 1; i < all.size(); i++) {
      DevicePreset p = all.get(i - 1);
      DevicePreset q = all.get(i);
      assertThat(p.group().compareTo(q.group())).isLessThanOrEqualTo(0);
      if (p.group() == q.group()) {
        assertThat((long) p.width() * p.height())
            .isLessThanOrEqualTo((long) q.width() * q.height());
      }
    }
  }

  @Test
  void searchIgnoresCaseAndAccentsAndMatchesDimensions() {
    PresetCatalog c = PresetCatalog.of(List.of(), PresetCatalog.builtIns(), List.of(), List.of());
    Map<Group, String> names = Map.of(Group.PHONE, "Móvil", Group.TABLET, "Tableta");

    assertThat(c.search("MOVIL", names)).allMatch(p -> p.group() == Group.PHONE).hasSize(11);
    assertThat(c.search("1179x2556", names))
        .extracting(DevicePreset::id)
        .containsExactly("phone-1179-2556");
    assertThat(c.search("1179×2556", names)).hasSize(1);
    assertThat(c.search("  ", names)).hasSize(35);
    assertThat(c.search("tableta 2732", names))
        .extracting(DevicePreset::id)
        .containsExactly("tablet-2048-2732");
  }
}
