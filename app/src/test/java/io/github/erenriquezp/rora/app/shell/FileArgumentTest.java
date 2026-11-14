package io.github.erenriquezp.rora.app.shell;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.app.diag.AppInfo;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileArgumentTest {

  @TempDir Path dir;

  @Test
  void theFirstExistingFileThatIsNotAnOptionIsOpened() throws Exception {
    Path photo = Files.writeString(dir.resolve("foto de la playa ñ 🎉.jpg"), "x");
    Path second = Files.writeString(dir.resolve("otra.png"), "x");
    Path folder = Files.createDirectories(dir.resolve("carpeta"));
    assertThat(
            StudioApp.fileArgument(
                List.of(
                    "--lab",
                    dir.resolve("no-existe.jpg").toString(),
                    folder.toString(),
                    photo.toString(),
                    second.toString())))
        .contains(new File(photo.toString()));
  }

  @Test
  void nothingToOpen() {
    assertThat(StudioApp.fileArgument(List.of())).isEmpty();
    assertThat(StudioApp.fileArgument(List.of("--lab", " "))).isEmpty();
    assertThat(StudioApp.fileArgument(List.of(dir.toString()))).isEmpty();
  }

  @Test
  void theSessionLineHasVersionsButNothingPersonal() {
    String line = Launcher.sessionLine(new AppInfo("0.1.0", "beta"), true);
    assertThat(line)
        .startsWith("Rora Studio 0.1.0 (beta) · Java " + Runtime.version())
        .contains("núcleos")
        .endsWith("empaquetada sí");
    String home = System.getProperty("user.home");
    String user = System.getProperty("user.name");
    assertThat(line).doesNotContain(home);
    if (user.length() >= 3) {
      assertThat(line).doesNotContain(user);
    }
  }
}
