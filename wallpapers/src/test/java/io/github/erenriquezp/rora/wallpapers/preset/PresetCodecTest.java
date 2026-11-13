package io.github.erenriquezp.rora.wallpapers.preset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset.Group;
import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset.Orientation;
import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset.Origin;
import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset.Status;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PresetCodecTest {

  private static final String OK =
      "{\"id\":\"user:a\",\"name\":\"A\",\"group\":\"custom\",\"width\":1000,\"height\":2000,"
          + "\"orientation\":\"portrait\",\"status\":\"user\",\"source\":\"yo\"";

  private static String doc(String... entries) {
    return "{\"schema\":1,\"revision\":\"2026-11-13\",\"presets\":["
        + String.join(",", entries)
        + "]}";
  }

  @Test
  void roundTripsUserPresets() throws Exception {
    DevicePreset p =
        new DevicePreset(
            "user:mi-movil",
            "Mi móvil \"viejo\"",
            Group.PHONE,
            1080,
            2340,
            Orientation.PORTRAIT,
            Status.USER,
            "medido a mano",
            Optional.of(LocalDate.of(2026, 1, 2)),
            List.of(new SafeZone(SafeZone.Kind.CLOCK, 0.1, 0.07, 0.8, 0.2)),
            0.05,
            Origin.USER);

    String text = PresetCodec.write(List.of(p), LocalDate.of(2026, 11, 13));
    PresetCodec.Parsed back = PresetCodec.read(text, "f.json", Origin.USER);

    assertThat(back.problems()).isEmpty();
    assertThat(back.presets()).containsExactly(p);
    assertThat(PresetCodec.write(back.presets(), LocalDate.of(2026, 11, 13))).isEqualTo(text);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[]",
        "{\"schema\":2,\"presets\":[]}",
        "{\"schema\":\"1\",\"presets\":[]}",
        "{\"schema\":1}",
        "{\"schema\":1,\"presets\":{}}",
        "{\"schema\":1,\"presets\":[",
      })
  void rejectsBrokenFiles(String text) {
    assertThatThrownBy(() -> PresetCodec.read(text, "f.json", Origin.USER))
        .isInstanceOf(PresetCodec.FileException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\"widht\":1}",
        "\"parallaxMargin\":0.5}",
        "\"reviewed\":\"ayer\"}",
        "\"safeZones\":[{\"kind\":\"clock\",\"rect\":[0,0,1.2,0.1]}]}",
        "\"safeZones\":[{\"kind\":\"reloj\",\"rect\":[0,0,1,0.1]}]}",
        "\"safeZones\":[{\"kind\":\"clock\",\"rect\":[0,0,1]}]}",
        "\"safeZones\":{}}",
      })
  void dropsOnlyTheInvalidEntry(String tail) throws Exception {
    String bad = OK.replace("user:a", "user:b") + "," + tail;
    PresetCodec.Parsed p = PresetCodec.read(doc(OK + "}", bad), "f.json", Origin.USER);

    assertThat(p.presets()).extracting(DevicePreset::id).containsExactly("user:a");
    assertThat(p.problems()).singleElement().satisfies(x -> assertThat(x.id()).isEqualTo("user:b"));
  }

  @Test
  void rejectsInconsistentEntries() throws Exception {
    PresetCodec.Parsed p =
        PresetCodec.read(
            doc(
                OK.replace("\"portrait\"", "\"landscape\"") + "}",
                OK.replace("1000", "10.5") + "}",
                OK.replace("1000", "8") + "}",
                OK.replace("user:a", "pc-a") + "}",
                OK.replace("\"user\",", "\"verified\",") + "}",
                OK.replace("\"custom\"", "\"reloj\"") + "}",
                "7"),
            "f.json",
            Origin.USER);

    assertThat(p.presets()).isEmpty();
    assertThat(p.problems()).hasSize(7);
  }

  @Test
  void keepsTheFirstOfRepeatedIdsAndIgnoresComments() throws Exception {
    PresetCodec.Parsed p =
        PresetCodec.read(
            doc(OK + ",\"_nota\":\"x\"}", OK.replace("1000", "1200") + "}"), "f.json", Origin.USER);

    assertThat(p.presets()).singleElement().satisfies(x -> assertThat(x.width()).isEqualTo(1000));
    assertThat(p.problems())
        .singleElement()
        .satisfies(x -> assertThat(x.detail()).contains("repetido"));
  }
}
