package io.github.erenriquezp.rora.app.update;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versión numérica {@code MAYOR.MENOR.PARCHE}. Las etiquetas de release son {@code vX.Y.Z};
 * cualquier otra cosa (sufijos como {@code -academic}, betas con nombre, borradores) no cuenta.
 */
public record VersionTag(int major, int minor, int patch) implements Comparable<VersionTag> {

  private static final Pattern TAG = Pattern.compile("v(\\d{1,4})\\.(\\d{1,4})\\.(\\d{1,4})");
  private static final Pattern VERSION = Pattern.compile("(\\d{1,4})\\.(\\d{1,4})\\.(\\d{1,4})");

  /** Etiqueta de release estricta: {@code v0.2.0}. */
  public static Optional<VersionTag> parseTag(String tag) {
    return tag == null ? Optional.empty() : of(TAG.matcher(tag.strip()), true);
  }

  /** Versión de la app ({@code 0.1.0} o {@code 0.1.0-SNAPSHOT}: cuentan los tres números). */
  public static Optional<VersionTag> parseVersion(String version) {
    return version == null ? Optional.empty() : of(VERSION.matcher(version.strip()), false);
  }

  private static Optional<VersionTag> of(Matcher m, boolean whole) {
    if (whole ? !m.matches() : !m.lookingAt()) {
      return Optional.empty();
    }
    return Optional.of(
        new VersionTag(
            Integer.parseInt(m.group(1)),
            Integer.parseInt(m.group(2)),
            Integer.parseInt(m.group(3))));
  }

  @Override
  public int compareTo(VersionTag o) {
    int c = Integer.compare(major, o.major);
    if (c == 0) {
      c = Integer.compare(minor, o.minor);
    }
    return c != 0 ? c : Integer.compare(patch, o.patch);
  }

  @Override
  public String toString() {
    return major + "." + minor + "." + patch;
  }
}
