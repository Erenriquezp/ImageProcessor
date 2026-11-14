package io.github.erenriquezp.rora.app.diag;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Detecta los {@code hs_err_pid*.log} que deja la JVM al morir por un fallo nativo ({@code
 * -XX:ErrorFile} los pone en la carpeta de registros). Del archivo solo se usa la cabecera: el
 * resto lleva variables de entorno, rutas y registros de la CPU.
 */
public final class HsErrScanner {

  /** Visto por el usuario: se renombra con este sufijo. */
  public static final String SEEN = ".visto";

  static final int KEEP_SEEN = 3;
  static final int MAX_SUMMARY = 2048;

  private final Path dir;
  private final Redactor redactor;

  public HsErrScanner(Path dir, Redactor redactor) {
    this.dir = dir;
    this.redactor = redactor;
  }

  /** El más reciente sin ver, si hay. */
  public Optional<Path> newestUnseen() {
    return list(false).stream().findFirst();
  }

  private List<Path> list(boolean seen) {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    String suffix = seen ? "\\.log" + SEEN.replace(".", "\\.") : "\\.log";
    try (Stream<Path> s = Files.list(dir)) {
      return s.filter(p -> p.getFileName().toString().matches("hs_err_pid\\d+" + suffix))
          .sorted(Comparator.comparing(HsErrScanner::modified).reversed())
          .toList();
    } catch (IOException e) {
      return List.of();
    }
  }

  private static FileTime modified(Path p) {
    try {
      return Files.getLastModifiedTime(p);
    } catch (IOException e) {
      return FileTime.fromMillis(0);
    }
  }

  /**
   * Cabecera redactada: las líneas {@code #} hasta el resumen, sin las de "envía un informe".
   *
   * @return vacío si no se puede leer
   */
  public String summary(Path file) {
    List<String> out = new ArrayList<>();
    try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
      for (String line = r.readLine(); line != null && out.size() < 40; line = r.readLine()) {
        if (line.contains("S U M M A R Y") || (!line.startsWith("#") && !out.isEmpty())) {
          break;
        }
        String t = line.replaceFirst("^#\\s?", "").strip();
        if (t.isEmpty()
            || t.startsWith("If you would like to submit")
            || t.startsWith("http")
            || t.startsWith("The crash happened outside")
            || t.startsWith("See problematic frame")) {
          continue;
        }
        out.add(t);
      }
    } catch (IOException e) {
      return "";
    }
    String text = redactor.redact(String.join("\n", out));
    return text.length() <= MAX_SUMMARY ? text : text.substring(0, MAX_SUMMARY) + "…";
  }

  /** Lo marca como visto y deja solo los {@link #KEEP_SEEN} vistos más recientes. */
  public void markSeen(Path file) throws IOException {
    Files.move(file, file.resolveSibling(file.getFileName() + SEEN));
    List<Path> seen = list(true);
    for (int i = KEEP_SEEN; i < seen.size(); i++) {
      Files.deleteIfExists(seen.get(i));
    }
  }
}
