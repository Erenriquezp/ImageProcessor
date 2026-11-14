package io.github.erenriquezp.rora.app.diag;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Versión y canal de la aplicación. La única fuente es el POM raíz, que Maven escribe en {@code
 * build.properties}; "Acerca de", el informe y el User-Agent la leen de aquí.
 *
 * @param version versión del POM, p. ej. {@code 0.1.0} o {@code 0.1.0-SNAPSHOT}
 * @param channel canal que se muestra junto a la versión, p. ej. {@code beta}; puede estar vacío
 */
public record AppInfo(String version, String channel) {

  /** Sin recurso filtrado (p. ej. desde un IDE que no ejecuta Maven). */
  public static final String DEVELOPMENT = "desarrollo";

  private static final String RESOURCE = "build.properties";
  private static final Pattern NUMERIC = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

  private static volatile AppInfo current;

  public AppInfo {
    version = clean(version, DEVELOPMENT);
    channel = clean(channel, "");
  }

  /** La de esta compilación; se lee una vez. */
  public static AppInfo current() {
    AppInfo info = current;
    if (info == null) {
      try (InputStream in = AppInfo.class.getResourceAsStream(RESOURCE)) {
        info = in == null ? new AppInfo(DEVELOPMENT, "") : load(in);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      current = info;
    }
    return info;
  }

  /** Lee un {@code build.properties}. Un valor sin filtrar ({@code ${...}}) cuenta como ausente. */
  public static AppInfo load(InputStream in) throws IOException {
    Properties p = new Properties();
    try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
      p.load(r);
    }
    return new AppInfo(p.getProperty("version"), p.getProperty("channel"));
  }

  private static String clean(String value, String fallback) {
    if (value == null || value.isBlank() || value.contains("${")) {
      return fallback;
    }
    return value.strip();
  }

  /** {@code true} si es una versión de desarrollo (instantánea o sin recurso). */
  public boolean isDevelopment() {
    return version.equals(DEVELOPMENT) || version.endsWith("-SNAPSHOT");
  }

  /** Solo los tres números ({@code 0.1.0}), o vacío si la versión no los tiene. */
  public String numericVersion() {
    var m = NUMERIC.matcher(version);
    return m.lookingAt() ? m.group() : "";
  }

  /** Lo que ve el usuario: {@code 0.1.0 (beta)}. */
  public String displayVersion() {
    return channel.isEmpty() ? version : version + " (" + channel + ")";
  }

  /** User-Agent genérico de la consulta de actualizaciones: nombre y versión, nada más. */
  public String userAgent() {
    String v = numericVersion();
    return "RoraStudio/" + (v.isEmpty() ? "0" : v);
  }
}
