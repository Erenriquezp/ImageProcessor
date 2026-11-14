package io.github.erenriquezp.rora.app.diag;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Una línea por registro ({@code fecha nivel logger mensaje}) más la traza indentada, y todo pasa
 * por el {@link Redactor} antes de llegar al archivo.
 */
public final class RedactingFormatter extends Formatter {

  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT);

  private final Redactor redactor;
  private final ZoneId zone;

  public RedactingFormatter(Redactor redactor, ZoneId zone) {
    this.redactor = redactor;
    this.zone = zone;
  }

  static String levelName(Level level) {
    int v = level.intValue();
    if (v >= Level.SEVERE.intValue()) {
      return "ERROR";
    }
    if (v >= Level.WARNING.intValue()) {
      return "WARN ";
    }
    if (v >= Level.INFO.intValue()) {
      return "INFO ";
    }
    if (v >= Level.FINE.intValue()) {
      return "DEBUG";
    }
    return "TRACE";
  }

  @Override
  public String format(LogRecord r) {
    StringBuilder sb = new StringBuilder(128);
    sb.append(TIME.format(r.getInstant().atZone(zone))).append(' ');
    sb.append(levelName(r.getLevel())).append(' ');
    sb.append(r.getLoggerName() == null ? "-" : r.getLoggerName()).append("  ");
    String message = formatMessage(r);
    sb.append(message == null ? "" : message.replace("\r\n", "\n").replace('\r', '\n'));
    sb.append(System.lineSeparator());
    if (r.getThrown() != null) {
      StringWriter w = new StringWriter();
      r.getThrown().printStackTrace(new PrintWriter(w));
      for (String line : w.toString().split("\\R")) {
        sb.append("    ").append(line).append(System.lineSeparator());
      }
    }
    return redactor.redact(sb.toString());
  }
}
