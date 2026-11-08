package io.github.erenriquezp.rora.core.io;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Nombres de archivo válidos en Windows y Linux. Se aplican las mismas reglas en todos los sistemas
 * para que un nombre no cambie al mover la carpeta.
 */
public final class FileNames {

  /** Longitud máxima del nombre completo, en bytes UTF-8. */
  public static final int MAX_BYTES = 240;

  private static final String INVALID = "<>:\"/\\|?*";
  private static final String FALLBACK = "imagen";
  private static final Set<String> RESERVED = reserved();

  private FileNames() {}

  private static Set<String> reserved() {
    Set<String> s = new java.util.HashSet<>(Set.of("CON", "PRN", "AUX", "NUL"));
    for (String port : new String[] {"COM", "LPT"}) {
      for (char d : "0123456789¹²³".toCharArray()) {
        s.add(port + d);
      }
    }
    return Set.copyOf(s);
  }

  /** Nombre base seguro, sin extensión ni separadores de ruta. */
  public static String sanitize(String name) {
    return finish(clean(name), MAX_BYTES);
  }

  /** {@code base.extension}, con el nombre base recortado para que quepa. */
  public static String withExtension(String base, String extension) {
    String suffix = "." + extension;
    int room = MAX_BYTES - suffix.getBytes(StandardCharsets.UTF_8).length;
    return finish(clean(base), room) + suffix;
  }

  /** Quita la última extensión de {@code fileName}. */
  public static String baseName(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(0, dot) : fileName;
  }

  private static String clean(String name) {
    StringBuilder out = new StringBuilder(name.length());
    name.codePoints()
        .forEach(
            cp -> {
              boolean bad =
                  cp < 32
                      || cp == 127
                      || INVALID.indexOf(cp) >= 0
                      || (cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE);
              out.appendCodePoint(bad ? '_' : cp);
            });
    return out.toString();
  }

  private static String finish(String cleaned, int maxBytes) {
    String s = trimEnds(truncate(trimEnds(cleaned), maxBytes));
    if (s.isEmpty()) {
      return FALLBACK;
    }
    int dot = s.indexOf('.');
    String stem = (dot < 0 ? s : s.substring(0, dot)).toUpperCase(Locale.ROOT);
    return RESERVED.contains(stem.trim()) ? "_" + s : s;
  }

  /** Sin puntos ni espacios al final, ni puntos o espacios al principio. */
  private static String trimEnds(String s) {
    int start = 0;
    int end = s.length();
    while (start < end && (s.charAt(start) == '.' || s.charAt(start) == ' ')) {
      start++;
    }
    while (end > start && (s.charAt(end - 1) == '.' || s.charAt(end - 1) == ' ')) {
      end--;
    }
    return s.substring(start, end);
  }

  private static String truncate(String s, int maxBytes) {
    if (s.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
      return s;
    }
    StringBuilder out = new StringBuilder();
    int[] used = {0};
    s.codePoints()
        .takeWhile(
            cp -> {
              used[0] += new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
              return used[0] <= maxBytes;
            })
        .forEach(out::appendCodePoint);
    return out.toString();
  }
}
