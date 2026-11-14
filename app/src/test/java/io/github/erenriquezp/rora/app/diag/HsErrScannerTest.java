package io.github.erenriquezp.rora.app.diag;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Archivos {@code hs_err} de ejemplo escritos por el test, con fechas fijas. */
class HsErrScannerTest {

  private static final String SAMPLE =
      """
      #
      # A fatal error has been detected by the Java Runtime Environment:
      #
      #  EXCEPTION_ACCESS_VIOLATION (0xc0000005) at pc=0x00007ffb1c2d3e4f, pid=4242, tid=1717
      #
      # JRE version: OpenJDK Runtime Environment Temurin-25.0.5+8 (25.0.5+8) (build 25.0.5+8-LTS)
      # Java VM: OpenJDK 64-Bit Server VM Temurin-25.0.5+8 (mixed mode, g1 gc, windows-amd64)
      # Problematic frame:
      # C  [onnxruntime.dll+0x12345]
      #
      # No core dump will be written. Minidumps are not enabled by default on client versions of Windows
      #
      # If you would like to submit a bug report, please visit:
      #   https://github.com/adoptium/adoptium-support/issues
      #

      ---------------  S U M M A R Y ------------

      Command Line: -Djava.io.tmpdir=C:\\Users\\Juan Pérez\\AppData\\Local\\RoraStudio\\tmp
      USERNAME=juanp
      Host: PORTATIL-JUAN
      """;

  private static final Redactor R =
      new Redactor(
          new Redactor.Context(
              "C:\\Users\\Juan Pérez", null, null, null, "juanp", "PORTATIL-JUAN"));

  @TempDir Path dir;

  private Path write(String name, long millis) throws IOException {
    Path p = Files.writeString(dir.resolve(name), SAMPLE, StandardCharsets.ISO_8859_1);
    Files.setLastModifiedTime(p, FileTime.fromMillis(millis));
    return p;
  }

  @Test
  void findsTheNewestUnseenAndKeepsOnlyTheHeader() throws IOException {
    write("hs_err_pid100.log", 1_000_000);
    Path newest = write("hs_err_pid200.log", 2_000_000);
    Files.writeString(dir.resolve("rora-0.log"), "no es un hs_err");
    HsErrScanner s = new HsErrScanner(dir, R);
    assertThat(s.newestUnseen()).hasValue(newest);

    String summary = s.summary(newest);
    assertThat(summary)
        .contains("EXCEPTION_ACCESS_VIOLATION")
        .contains("Temurin-25.0.5+8")
        .contains("onnxruntime.dll+0x12345");
    assertThat(summary)
        .doesNotContain("Command Line")
        .doesNotContain("juanp")
        .doesNotContain("PORTATIL")
        .doesNotContain("Juan")
        .doesNotContain("submit a bug report")
        .doesNotContain("https://");
  }

  @Test
  void markSeenRenamesAndKeepsTheLastThree() throws IOException {
    HsErrScanner s = new HsErrScanner(dir, R);
    for (int i = 1; i <= 5; i++) {
      Path p = write("hs_err_pid" + i + ".log", i * 1_000_000L);
      s.markSeen(p);
      Files.setLastModifiedTime(
          dir.resolve(p.getFileName() + HsErrScanner.SEEN), FileTime.fromMillis(i * 1_000_000L));
    }
    assertThat(s.newestUnseen()).isEmpty();
    try (Stream<Path> files = Files.list(dir)) {
      assertThat(files.map(p -> p.getFileName().toString()).sorted())
          .containsExactly(
              "hs_err_pid3.log.visto", "hs_err_pid4.log.visto", "hs_err_pid5.log.visto");
    }
  }

  @Test
  void missingDirectoryOrUnreadableFile() {
    HsErrScanner s = new HsErrScanner(dir.resolve("no-existe"), R);
    assertThat(s.newestUnseen()).isEmpty();
    assertThat(s.summary(dir.resolve("no-existe.log"))).isEmpty();
  }
}
