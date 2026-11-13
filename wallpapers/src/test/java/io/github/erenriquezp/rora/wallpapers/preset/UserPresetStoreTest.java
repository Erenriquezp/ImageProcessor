package io.github.erenriquezp.rora.wallpapers.preset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset.Group;
import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset.Origin;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UserPresetStoreTest {

  private static final LocalDate DAY = LocalDate.of(2026, 11, 13);

  @TempDir Path dir;

  private static DevicePreset mine(String id, int w, int h) {
    return PresetCatalogTest.preset(id, Group.CUSTOM, w, h, Origin.USER);
  }

  @Test
  void aMissingDirectoryIsEmpty() {
    UserPresetStore.Loaded l = new UserPresetStore(dir.resolve("no")).load();
    assertThat(l.presets()).isEmpty();
    assertThat(l.problems()).isEmpty();
  }

  @Test
  void savesAtomicallyAndReadsBack() throws IOException {
    UserPresetStore store = new UserPresetStore(dir.resolve("a").resolve("b"));
    List<DevicePreset> ps = List.of(mine("user:uno", 1000, 2000), mine("user:dos", 3000, 1000));

    store.save(ps, DAY);
    store.save(ps, DAY);

    try (Stream<Path> files = Files.list(store.directory())) {
      assertThat(files.map(p -> p.getFileName().toString())).containsExactly(UserPresetStore.FILE);
    }
    assertThat(store.load().presets()).containsExactlyElementsOf(ps);
  }

  @Test
  void refusesToSaveBuiltInPresets() {
    DevicePreset b = PresetCatalog.builtIns().getFirst();
    assertThatThrownBy(() -> new UserPresetStore(dir).save(List.of(b), DAY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ignoresBadFilesAndKeepsTheRest() throws IOException {
    new UserPresetStore(dir).save(List.of(mine("user:bueno", 1000, 1000)), DAY);
    Files.writeString(dir.resolve("roto.json"), "{ no es json");
    Files.write(dir.resolve("latin1.json"), new byte[] {'{', (byte) 0xE9, '}'});
    Files.writeString(
        dir.resolve("grande.json"), " ".repeat((int) UserPresetStore.MAX_FILE_BYTES + 1));
    Files.writeString(dir.resolve("otro.txt"), "ignorado por la extensión");
    Files.createDirectory(dir.resolve("carpeta.json"));
    Files.writeString(
        dir.resolve("duplicado.json"),
        PresetCodec.write(
            List.of(mine("user:bueno", 2000, 2000), mine("user:otro", 500, 500)), DAY));

    UserPresetStore.Loaded l = new UserPresetStore(dir).load();

    assertThat(l.presets())
        .extracting(DevicePreset::id)
        .containsExactly("user:bueno", "user:otro"); // duplicado.json va antes en orden alfabético
    assertThat(l.presets().getFirst().width()).isEqualTo(2000);
    assertThat(l.problems())
        .extracting(PresetProblem::source)
        .containsExactlyInAnyOrder(
            "carpeta.json", "grande.json", "latin1.json", "roto.json", UserPresetStore.FILE);
  }

  @Test
  void doesNotFollowSymbolicLinks() throws IOException {
    Path outside = Files.createDirectory(dir.resolve("fuera"));
    Path target = outside.resolve("secreto.json");
    Files.writeString(target, PresetCodec.write(List.of(mine("user:x", 100, 100)), DAY));
    Path presets = Files.createDirectory(dir.resolve("presets"));
    try {
      Files.createSymbolicLink(presets.resolve("enlace.json"), target);
    } catch (IOException | UnsupportedOperationException e) {
      Assumptions.abort("el sistema no permite crear enlaces simbólicos: " + e);
    }

    UserPresetStore.Loaded l = new UserPresetStore(presets).load();

    assertThat(l.presets()).isEmpty();
    assertThat(l.problems())
        .singleElement()
        .satisfies(p -> assertThat(p.detail()).contains("normal"));
  }

  @Test
  void idsNeverBecomePaths() throws IOException {
    UserPresetStore store = new UserPresetStore(dir);
    store.save(List.of(mine("user:../../fuera", 100, 100)), DAY);

    try (Stream<Path> files = Files.walk(dir.getParent(), 1)) {
      assertThat(files.filter(p -> p.getFileName().toString().equals("fuera"))).isEmpty();
    }
    assertThat(store.load().presets())
        .extracting(DevicePreset::id)
        .containsExactly("user:../../fuera");
  }

  @Test
  void capsTheNumberOfPresets() throws IOException {
    List<DevicePreset> many =
        java.util.stream.IntStream.range(0, UserPresetStore.MAX_PRESETS + 3)
            .mapToObj(i -> mine("user:p" + i, 100 + i, 100))
            .toList();
    String text = PresetCodec.write(many, DAY);
    assertThat((long) text.getBytes(StandardCharsets.UTF_8).length)
        .isLessThan(UserPresetStore.MAX_FILE_BYTES);
    Files.writeString(dir.resolve("muchos.json"), text);

    UserPresetStore.Loaded l = new UserPresetStore(dir).load();

    assertThat(l.presets()).hasSize(UserPresetStore.MAX_PRESETS);
    assertThat(l.problems()).hasSize(3);
  }

  @Test
  void defaultDirectoryPerSystem() {
    assertThat(
            UserPresetStore.defaultDirectory(
                Map.of("APPDATA", "C:\\Users\\u\\AppData\\Roaming"), "Windows 11", "C:\\Users\\u"))
        .isEqualTo(Path.of("C:\\Users\\u\\AppData\\Roaming", "RoraStudio", "wallpapers"));
    assertThat(UserPresetStore.defaultDirectory(Map.of(), "Mac OS X", "/Users/u"))
        .isEqualTo(
            Path.of("/Users/u", "Library", "Application Support", "RoraStudio", "wallpapers"));
    assertThat(UserPresetStore.defaultDirectory(Map.of(), "Linux", "/home/u"))
        .isEqualTo(Path.of("/home/u", ".local", "share", "rora-studio", "wallpapers"));
    assertThat(
            UserPresetStore.defaultDirectory(
                Map.of("XDG_DATA_HOME", "relativo"), "Linux", "/home/u"))
        .isEqualTo(Path.of("/home/u", ".local", "share", "rora-studio", "wallpapers"));
  }

  @Test
  void newIdsAreSlugsWithoutCollisions() {
    assertThat(UserPresetStore.newId("Mi Móvil 2 ×", Set.of())).isEqualTo("user:mi-movil-2-x");
    assertThat(UserPresetStore.newId("Mi móvil", Set.of("user:mi-movil", "user:mi-movil-2")))
        .isEqualTo("user:mi-movil-3");
    assertThat(UserPresetStore.newId("../..", Set.of())).isEqualTo("user:preset");
  }
}
