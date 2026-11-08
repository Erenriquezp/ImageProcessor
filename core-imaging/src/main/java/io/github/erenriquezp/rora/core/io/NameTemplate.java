package io.github.erenriquezp.rora.core.io;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Plantilla de nombre con {@code {nombre}}, {@code {fecha}}, {@code {hora}}, {@code {n}}, {@code
 * {ancho}}, {@code {alto}} y {@code {formato}}. Un token desconocido se deja tal cual. La fecha se
 * recibe como dato: la plantilla no consulta el reloj.
 */
public final class NameTemplate {

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT);
  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("HH-mm-ss", Locale.ROOT);

  /** Valores de los tokens. */
  public record Values(
      String name, LocalDateTime now, int number, int width, int height, FormatId format) {}

  private NameTemplate() {}

  /** Nombre final, saneado y con extensión. */
  public static String fileName(String template, Values v) {
    return FileNames.withExtension(expand(template, v), v.format().extension());
  }

  /** Sustituye los tokens, sin sanear. */
  public static String expand(String template, Values v) {
    return template
        .replace("{nombre}", v.name())
        .replace("{fecha}", DATE.format(v.now()))
        .replace("{hora}", TIME.format(v.now()))
        .replace("{n}", Integer.toString(v.number()))
        .replace("{ancho}", Integer.toString(v.width()))
        .replace("{alto}", Integer.toString(v.height()))
        .replace("{formato}", v.format().extension());
  }
}
