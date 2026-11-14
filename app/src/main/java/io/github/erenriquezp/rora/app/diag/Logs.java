package io.github.erenriquezp.rora.app.diag;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Registro de la app en archivo: {@code System.Logger} → {@code java.util.logging} → {@code
 * FileHandler} rotativo con {@link RedactingFormatter}. Las llamadas existentes no cambian.
 *
 * <p>Se instala una vez desde {@code Launcher}; los tests y las herramientas de desarrollo que
 * arrancan la interfaz directamente no lo instalan.
 */
public final class Logs implements AutoCloseable {

  /** {@code rora-0.log} es siempre el más reciente. */
  public static final String PATTERN = "rora-%g.log";

  public static final int LIMIT_BYTES = 1 << 20;
  public static final int COUNT = 5;

  /** Loggers de la app: los nombres de {@code System.getLogger("rora.*")} y las clases propias. */
  static final String[] APP = {"rora", "io.github.erenriquezp.rora"};

  private final Path dir;
  private final FileHandler handler;
  private final Redactor redactor;
  private final Level normal;
  private final List<Handler> removed = new ArrayList<>();
  private Level previousRoot;
  // JUL guarda los loggers con referencias débiles: sin esto, el nivel podría perderse.
  private final List<Logger> pinned = new ArrayList<>();
  private PrintStream originalOut;
  private PrintStream originalErr;

  private Logs(Path dir, FileHandler handler, Redactor redactor, Level normal) {
    this.dir = dir;
    this.handler = handler;
    this.redactor = redactor;
    this.normal = normal;
  }

  /**
   * @param replaceConsole quita el {@code ConsoleHandler} del logger raíz (versión empaquetada, sin
   *     consola)
   */
  public static Logs install(
      Path dir,
      Redactor redactor,
      boolean replaceConsole,
      Level appLevel,
      ZoneId zone,
      int limitBytes,
      int count)
      throws IOException {
    Files.createDirectories(dir);
    // En el patrón de FileHandler "%" es especial: una carpeta con "%" en el nombre se escapa.
    String pattern = dir.toAbsolutePath().toString().replace("%", "%%") + "/" + PATTERN;
    FileHandler h = new FileHandler(pattern, limitBytes, count, true);
    h.setEncoding(StandardCharsets.UTF_8.name());
    h.setFormatter(new RedactingFormatter(redactor, zone));
    h.setLevel(Level.ALL);
    Logs logs = new Logs(dir, h, redactor, appLevel);
    Logger root = Logger.getLogger("");
    if (replaceConsole) {
      for (Handler old : root.getHandlers()) {
        root.removeHandler(old);
        logs.removed.add(old);
      }
    }
    logs.previousRoot = root.getLevel();
    root.setLevel(Level.WARNING);
    root.addHandler(h);
    for (String name : APP) {
      Logger l = Logger.getLogger(name);
      l.setLevel(appLevel);
      logs.pinned.add(l);
    }
    return logs;
  }

  /** Nivel de {@code -Drora.log.level} (p. ej. {@code FINE}), o INFO. */
  public static Level levelFromProperty(String value) {
    if (value == null || value.isBlank()) {
      return Level.INFO;
    }
    try {
      return Level.parse(value.strip().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return Level.INFO;
    }
  }

  public Path directory() {
    return dir;
  }

  /** Registro detallado (FINE) hasta cerrar la app; no se guarda. */
  public void setVerbose(boolean verbose) {
    for (Logger l : pinned) {
      l.setLevel(verbose ? Level.FINE : normal);
    }
  }

  public boolean isVerbose() {
    return !pinned.isEmpty() && pinned.getFirst().getLevel() == Level.FINE;
  }

  /**
   * Lleva {@code System.out} y {@code System.err} al registro, línea a línea (versión empaquetada:
   * sin consola se perderían los avisos de las bibliotecas).
   */
  public void redirectStandardStreams() {
    originalOut = System.out;
    originalErr = System.err;
    System.setOut(lineStream(Logger.getLogger("rora.stdout"), Level.INFO));
    System.setErr(lineStream(Logger.getLogger("rora.stderr"), Level.WARNING));
  }

  private PrintStream lineStream(Logger logger, Level level) {
    pinned.add(logger);
    logger.setLevel(Level.ALL);
    return new PrintStream(new LineToLogger(logger, level), true, StandardCharsets.UTF_8);
  }

  /**
   * Últimas líneas del registro más reciente (redactadas), para el informe de diagnóstico.
   *
   * @return vacío si aún no hay registro
   */
  public String tail(int maxLines, int maxBytes) {
    handler.flush();
    return tail(dir, redactor, maxLines, maxBytes);
  }

  static String tail(Path dir, Redactor redactor, int maxLines, int maxBytes) {
    Optional<Path> newest = newest(dir);
    if (newest.isEmpty()) {
      return "";
    }
    try (RandomAccessFile f = new RandomAccessFile(newest.get().toFile(), "r")) {
      long len = f.length();
      long from = Math.max(0, len - maxBytes);
      byte[] buf = new byte[(int) (len - from)];
      f.seek(from);
      f.readFully(buf);
      String text = new String(buf, StandardCharsets.UTF_8);
      List<String> lines = new ArrayList<>(Arrays.asList(text.split("\\R")));
      if (from > 0 && !lines.isEmpty()) {
        lines.removeFirst(); // la primera puede estar cortada
      }
      int start = Math.max(0, lines.size() - maxLines);
      return redactor.redact(String.join("\n", lines.subList(start, lines.size())));
    } catch (IOException e) {
      return "";
    }
  }

  /** El {@code rora-*.log} modificado más tarde (con dos instancias, JUL añade un sufijo). */
  static Optional<Path> newest(Path dir) {
    if (!Files.isDirectory(dir)) {
      return Optional.empty();
    }
    try (Stream<Path> s = Files.list(dir)) {
      return s.filter(p -> p.getFileName().toString().matches("rora-\\d+\\.log(\\.\\d+)?"))
          .max(
              (a, b) -> {
                try {
                  return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                } catch (IOException e) {
                  return 0;
                }
              });
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  @Override
  public void close() {
    if (originalOut != null) {
      System.setOut(originalOut);
      System.setErr(originalErr);
    }
    Logger root = Logger.getLogger("");
    root.removeHandler(handler);
    handler.close();
    for (Handler h : removed) {
      root.addHandler(h);
    }
    root.setLevel(previousRoot);
    for (Logger l : pinned) {
      l.setLevel(null);
    }
  }

  /** Acumula bytes hasta un salto de línea y emite la línea como un registro. */
  private static final class LineToLogger extends OutputStream {
    private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> false);
    private final Logger logger;
    private final Level level;
    private final ByteArrayOutputStream line = new ByteArrayOutputStream();

    LineToLogger(Logger logger, Level level) {
      this.logger = logger;
      this.level = level;
    }

    @Override
    public synchronized void write(int b) {
      if (b == '\n') {
        emit();
      } else if (b != '\r') {
        line.write(b);
      }
    }

    @Override
    public synchronized void flush() {
      // Las líneas se emiten completas; un resto sin salto de línea espera al siguiente.
    }

    private void emit() {
      String text = line.toString(StandardCharsets.UTF_8);
      line.reset();
      if (text.isEmpty() || BUSY.get()) {
        return; // un fallo del propio registro escrito en System.err no vuelve a entrar
      }
      BUSY.set(true);
      try {
        logger.log(level, text);
      } finally {
        BUSY.set(false);
      }
    }
  }
}
