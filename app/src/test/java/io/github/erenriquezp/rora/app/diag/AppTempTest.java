package io.github.erenriquezp.rora.app.diag;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppTempTest {

  @TempDir Path dir;

  @AfterEach
  void release() throws Exception {
    AppTemp.release();
  }

  @Test
  void anAbsolutePropertyWinsOtherwiseTheAppFolder() {
    AppDirs dirs = new AppDirs(dir.resolve("local"), dir.resolve("roaming"));
    Path abs = dir.resolve("otro tmp").toAbsolutePath();
    assertThat(AppTemp.resolve(abs.toString(), dirs)).isEqualTo(abs);
    assertThat(AppTemp.resolve("$LOCALAPPDATA\\RoraStudio\\tmp", dirs)).isEqualTo(dirs.tmp());
    assertThat(AppTemp.resolve("", dirs)).isEqualTo(dirs.tmp());
    assertThat(AppTemp.resolve(null, dirs)).isEqualTo(dirs.tmp());
  }

  @Test
  void createsAndCleansLeftoversWhenAlone() throws Exception {
    Path tmp = dir.resolve("tmp ñ");
    Files.createDirectories(tmp.resolve("onnxruntime-java123"));
    Files.writeString(tmp.resolve("onnxruntime-java123").resolve("x.dll"), "x");
    Files.writeString(tmp.resolve("resto.tmp"), "x");
    assertThat(AppTemp.prepare(tmp)).isEqualTo(tmp);
    try (var s = Files.list(tmp)) {
      assertThat(s.map(p -> p.getFileName().toString())).containsExactly(AppTemp.LOCK);
    }
  }

  @Test
  void anotherInstanceKeepsItsFiles() throws Exception {
    Path tmp = Files.createDirectories(dir.resolve("tmp"));
    Files.writeString(tmp.resolve("de-la-otra.tmp"), "x");
    try (FileChannel ch =
            FileChannel.open(
                tmp.resolve(AppTemp.LOCK), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock = ch.lock()) {
      assertThat(lock.isValid()).isTrue();
      AppTemp.prepare(tmp);
    }
    assertThat(tmp.resolve("de-la-otra.tmp")).exists();
  }

  @Test
  void aMissingDirectoryIsCreated() throws Exception {
    Path tmp = dir.resolve("a").resolve("b");
    AppTemp.prepare(tmp);
    assertThat(tmp).isDirectory();
  }
}
