package io.github.erenriquezp.rora.app.diag;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Registro en archivo con un directorio temporal; restaura el JUL global al cerrar. */
class LogsTest {

  private static final Redactor REDACTOR =
      new Redactor(new Redactor.Context("C:\\Users\\Juan Pérez", null, null, null, "juanp", null));

  @TempDir Path dir;

  private Logs install(int limit, int count) throws IOException {
    return Logs.install(dir, REDACTOR, false, Level.INFO, ZoneOffset.UTC, limit, count);
  }

  private List<Path> logFiles() throws IOException {
    try (Stream<Path> s = Files.list(dir)) {
      return s.filter(p -> p.getFileName().toString().matches("rora-\\d+\\.log")).sorted().toList();
    }
  }

  private String read(String name) throws IOException {
    return Files.readString(dir.resolve(name), StandardCharsets.UTF_8);
  }

  @Test
  void existingSystemLoggerCallsReachTheFileRedacted() throws IOException {
    try (Logs logs = install(Logs.LIMIT_BYTES, Logs.COUNT)) {
      System.getLogger("rora.render")
          .log(
              System.Logger.Level.WARNING,
              "fallo al abrir C:\\Users\\Juan Pérez\\Pictures\\Juan en la playa.jpg");
      logs.tail(1, 1); // fuerza el vaciado
    }
    String text = read("rora-0.log");
    assertThat(text)
        .contains("WARN  rora.render  fallo al abrir %USERPROFILE%\\Pictures\\<foto>.jpg")
        .doesNotContain("Juan")
        .matches("(?s)\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z .*");
  }

  @Test
  void stackTracesAreRedactedToo() throws IOException {
    try (Logs logs = install(Logs.LIMIT_BYTES, Logs.COUNT)) {
      Logger.getLogger("rora.app")
          .log(
              Level.SEVERE,
              "error",
              new java.nio.file.NoSuchFileException("D:\\Privado\\juanp\\nota.txt"));
      logs.tail(1, 1);
    }
    String text = read("rora-0.log");
    assertThat(text)
        .contains("ERROR rora.app  error")
        .contains("NoSuchFileException: D:\\…\\<archivo>.txt");
    assertThat(text).doesNotContain("Privado").doesNotContain("juanp");
  }

  @Test
  void rotatesBySizeAndKeepsCountFiles() throws IOException {
    try (Logs logs = install(4_000, 3)) {
      Logger l = Logger.getLogger("rora.test");
      for (int i = 0; i < 400; i++) {
        l.info("registro número " + i + " " + "x".repeat(40));
      }
      logs.tail(1, 1);
    }
    List<Path> files = logFiles();
    assertThat(files)
        .extracting(p -> p.getFileName().toString())
        .containsExactly("rora-0.log", "rora-1.log", "rora-2.log");
    for (Path p : files) {
      assertThat(Files.size(p)).isLessThanOrEqualTo(4_000 + 200);
    }
    assertThat(read("rora-0.log")).contains("registro número 399");
  }

  @Test
  void concurrentWritersProduceWholeLines() throws Exception {
    int threads = 8;
    int each = 500;
    try (Logs logs = install(Logs.LIMIT_BYTES * 8, 1)) {
      Logger l = Logger.getLogger("rora.concurrency");
      CountDownLatch start = new CountDownLatch(1);
      List<Thread> ts = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        int id = t;
        Thread th =
            new Thread(
                () -> {
                  try {
                    start.await();
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                  for (int i = 0; i < each; i++) {
                    l.info("hilo " + id + " linea " + i + " fin");
                  }
                });
        ts.add(th);
        th.start();
      }
      start.countDown();
      for (Thread th : ts) {
        th.join();
      }
      logs.tail(1, 1);
    }
    List<String> lines =
        Files.readAllLines(dir.resolve("rora-0.log"), StandardCharsets.UTF_8).stream()
            .filter(s -> s.contains("rora.concurrency"))
            .toList();
    assertThat(lines).hasSize(threads * each);
    assertThat(lines)
        .allMatch(s -> s.matches("\\S+ INFO  rora\\.concurrency  hilo \\d linea \\d+ fin"));
  }

  @Test
  void levelIsConfigurableAndVerboseIsTemporary() throws IOException {
    try (Logs logs = install(Logs.LIMIT_BYTES, Logs.COUNT)) {
      Logger l = Logger.getLogger("rora.level");
      l.fine("antes oculto");
      logs.setVerbose(true);
      assertThat(logs.isVerbose()).isTrue();
      l.fine("detalle visible");
      logs.setVerbose(false);
      l.fine("despues oculto");
      Logger.getLogger("org.otra.biblioteca").info("ruido de terceros");
      logs.tail(1, 1);
    }
    String text = read("rora-0.log");
    assertThat(text).contains("DEBUG rora.level  detalle visible");
    assertThat(text).doesNotContain("antes oculto").doesNotContain("despues oculto");
    assertThat(text).doesNotContain("ruido de terceros");
    assertThat(Logs.levelFromProperty("FINE")).isEqualTo(Level.FINE);
    assertThat(Logs.levelFromProperty("fine")).isEqualTo(Level.FINE);
    assertThat(Logs.levelFromProperty("no-es-un-nivel")).isEqualTo(Level.INFO);
    assertThat(Logs.levelFromProperty(null)).isEqualTo(Level.INFO);
  }

  @Test
  void tailReturnsTheLastLinesRedacted() throws IOException {
    try (Logs logs = install(Logs.LIMIT_BYTES, Logs.COUNT)) {
      Logger l = Logger.getLogger("rora.tail");
      for (int i = 0; i < 50; i++) {
        l.info("linea " + i);
      }
      String tail = logs.tail(3, 10_000);
      assertThat(tail.split("\n")).hasSize(3);
      assertThat(tail).contains("linea 49").doesNotContain("linea 46");
      assertThat(logs.tail(500, 120)).hasSizeLessThanOrEqualTo(120);
    }
  }

  @Test
  void tailOfAnEmptyDirectoryIsEmpty() {
    assertThat(Logs.tail(dir.resolve("no-existe"), REDACTOR, 10, 1000)).isEmpty();
  }

  @Test
  void standardStreamsGoToTheLogAndAreRestored() throws IOException {
    var out = System.out;
    var err = System.err;
    try (Logs logs = install(Logs.LIMIT_BYTES, Logs.COUNT)) {
      logs.redirectStandardStreams();
      System.err.println("aviso de una biblioteca en C:\\Users\\Juan Pérez\\x.png");
      System.out.print("sin salto ");
      System.out.println("de linea");
      logs.tail(1, 1);
    }
    assertThat(System.out).isSameAs(out);
    assertThat(System.err).isSameAs(err);
    String text = read("rora-0.log");
    assertThat(text)
        .contains("WARN  rora.stderr  aviso de una biblioteca en %USERPROFILE%\\<foto>.png");
    assertThat(text).contains("INFO  rora.stdout  sin salto de linea");
  }
}
