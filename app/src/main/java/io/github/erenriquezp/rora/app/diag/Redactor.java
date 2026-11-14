package io.github.erenriquezp.rora.app.diag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quita datos personales de un texto de registro o de informe: rutas del perfil, carpetas y nombres
 * de archivo, servidores de red, nombre de usuario y de equipo. Se aplica al texto ya formateado,
 * traza incluida, así que vale para mensajes que no controlamos (excepciones del JDK).
 *
 * <p>Reglas, en orden:
 *
 * <ol>
 *   <li>Prefijos conocidos ({@code user.home}, {@code LOCALAPPDATA}, {@code APPDATA}, {@code TEMP})
 *       y cualquier {@code X:\Users\nombre\}, también en forma corta 8.3, pasan a {@code
 *       %USERPROFILE%} y compañía.
 *   <li>En cada ruta absoluta (unidad, UNC o tras uno de esos prefijos), un segmento que no esté en
 *       la lista blanca pasa a {@code …}; servidor y recurso UNC a {@code <servidor>\<recurso>}.
 *   <li>El nombre final: imagen → {@code <foto>.ext}; archivo propio de la app → se queda; otro →
 *       {@code <archivo>.ext}.
 *   <li>Usuario y equipo sueltos → {@code <usuario>}, {@code <equipo>}.
 * </ol>
 */
public final class Redactor {

  /** Datos del entorno que hay que ocultar. Cualquier campo puede ser nulo o vacío. */
  public record Context(
      String userHome,
      String localAppData,
      String appData,
      String temp,
      String userName,
      String computerName) {

    /** Los del proceso actual. */
    public static Context fromSystem() {
      Map<String, String> env = System.getenv();
      return new Context(
          System.getProperty("user.home"),
          env.get("LOCALAPPDATA"),
          env.get("APPDATA"),
          env.getOrDefault("TEMP", env.get("TMP")),
          System.getProperty("user.name"),
          env.get("COMPUTERNAME"));
    }
  }

  static final String ELLIPSIS = "…";

  private static final Set<String> IMAGE_EXT =
      Set.of(
          "jpg", "jpeg", "jpe", "jfif", "png", "tif", "tiff", "webp", "bmp", "gif", "heic", "heif",
          "avif", "dng", "nef", "nrw", "cr2", "cr3", "crw", "arw", "srf", "sr2", "orf", "rw2",
          "raf", "pef", "srw", "x3f", "3fr", "erf", "kdc", "mrw", "raw", "rwl", "iiq");

  /** Carpetas que no dicen nada de la persona: de Windows, de la app y genéricas. */
  private static final Set<String> SAFE_DIRS =
      Set.of(
          "appdata",
          "local",
          "locallow",
          "roaming",
          "temp",
          "programs",
          "program files",
          "program files (x86)",
          "windows",
          "system32",
          "rorastudio",
          "rora studio",
          "cache",
          "ai",
          "logs",
          "tmp",
          "runtime",
          "ort-native",
          "bin",
          "javafx",
          "server",
          "lib",
          "app",
          "legal",
          "wallpapers",
          "desktop",
          "escritorio",
          "documents",
          "documentos",
          "pictures",
          "imágenes",
          "imagenes",
          "downloads",
          "descargas",
          "onedrive",
          "users",
          "%userprofile%",
          "%localappdata%",
          "%appdata%",
          "%temp%");

  private static final Pattern SAFE_FILE =
      Pattern.compile(
          "(?i)(hs_err_pid\\d+\\.log(\\.visto)?|rora-\\d+\\.log(\\.lck)?|\\.rora-export-[0-9a-z-]+\\.tmp"
              + "|[a-z0-9_.-]+\\.dll|realesr-general-x4v3\\.onnx|face_detection_yunet_2023mar\\.onnx"
              + "|onnxruntime-java\\d*|java\\.exe|javaw\\.exe|rora studio\\.exe)");

  /**
   * Inicio de una ruta: prefijo ya sustituido, unidad (no tras una letra: evita http://), UNC o
   * raíz POSIX con al menos dos segmentos ({@code /tmp/x/foto.jpg}; no tras letra, dígito, punto,
   * {@code :} ni otra barra, para no tocar URLs, tipos MIME, módulos de las trazas ni {@code 1/2}).
   */
  private static final Pattern PATH =
      Pattern.compile(
          "(%(?:USERPROFILE|LOCALAPPDATA|APPDATA|TEMP)%"
              + "|(?:file:/+)?(?<![\\p{L}\\p{N}])[A-Za-z]:(?=[\\\\/])"
              + "|(?<![\\\\/])\\\\\\\\[^\\\\/\\s:*?\"<>|]+[\\\\/][^\\\\/\\s:*?\"<>|]+"
              + "|(?<![\\p{L}\\p{N}_.:/\\\\~$%-])(?=/[^/\\s:*?\"<>|]+/))"
              + "((?:[\\\\/]+[^\\\\/:*?\"<>|\\r\\n\\t!]*)*)");

  private static final Pattern SEP = Pattern.compile("[\\\\/]+");
  private static final Pattern EXT_END = Pattern.compile("\\.([A-Za-z0-9]{1,8})(?=$|[\\s,;)\\]'])");

  /** {@code X:\Users\nombre} de cualquier usuario, también {@code NOMBRE~1}. */
  private static final Pattern ANY_PROFILE =
      Pattern.compile(
          "(?i)(?<![\\p{L}\\p{N}])(?:file:/+)?[A-Za-z]:[\\\\/]+(?:Users|Documents and Settings)"
              + "[\\\\/]+[^\\\\/:*?\"<>|\\r\\n\\t]+(?=[\\\\/])");

  private final List<Map.Entry<Pattern, String>> prefixes = new ArrayList<>();
  private final List<Map.Entry<Pattern, String>> words = new ArrayList<>();

  public Redactor(Context c) {
    List<Map.Entry<String, String>> known = new ArrayList<>();
    add(known, c.temp(), "%TEMP%");
    add(known, c.localAppData(), "%LOCALAPPDATA%");
    add(known, c.appData(), "%APPDATA%");
    add(known, c.userHome(), "%USERPROFILE%");
    known.sort(
        Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed());
    for (Map.Entry<String, String> e : known) {
      prefixes.add(Map.entry(prefixPattern(e.getKey()), e.getValue()));
    }
    addWord(c.userName(), "<usuario>");
    addWord(c.computerName(), "<equipo>");
  }

  private static void add(List<Map.Entry<String, String>> out, String path, String token) {
    if (path == null) {
      return;
    }
    String p = path.strip();
    while (p.length() > 3 && (p.endsWith("\\") || p.endsWith("/"))) {
      p = p.substring(0, p.length() - 1);
    }
    if (p.length() >= 3) {
      out.add(Map.entry(p, token));
    }
  }

  /**
   * La ruta tal cual, sin distinguir mayúsculas, con {@code \} o {@code /}, como segmento completo.
   */
  private static Pattern prefixPattern(String path) {
    StringBuilder sb = new StringBuilder("(?iu)(?:file:/+)?");
    for (String part : SEP.split(path, -1)) {
      if (sb.length() > "(?iu)(?:file:/+)?".length()) {
        sb.append("[\\\\/]+");
      }
      sb.append(Pattern.quote(part));
    }
    sb.append("(?=$|[\\\\/\\s\"'<>|:;,)\\]])");
    return Pattern.compile(sb.toString());
  }

  private void addWord(String word, String token) {
    if (word == null || word.strip().length() < 3) {
      return;
    }
    // Ni dentro de un identificador ni pegado a un punto: "java" no toca "java.lang".
    words.add(
        Map.entry(
            Pattern.compile(
                "(?iu)(?<![\\p{L}\\p{N}._$-])"
                    + Pattern.quote(word.strip())
                    + "(?![\\p{L}\\p{N}._$-])"),
            token));
  }

  /** Texto sin datos personales. {@code null} se devuelve tal cual. */
  public String redact(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    String out = text;
    for (Map.Entry<Pattern, String> p : prefixes) {
      out = p.getKey().matcher(out).replaceAll(Matcher.quoteReplacement(p.getValue()));
    }
    out = ANY_PROFILE.matcher(out).replaceAll("%USERPROFILE%");
    out = redactPaths(out);
    for (Map.Entry<Pattern, String> w : words) {
      out = w.getKey().matcher(out).replaceAll(Matcher.quoteReplacement(w.getValue()));
    }
    return out;
  }

  private static String redactPaths(String text) {
    Matcher m = PATH.matcher(text);
    StringBuilder sb = new StringBuilder(text.length());
    int last = 0;
    while (m.find()) {
      String start = m.group(1);
      String rest = m.group(2);
      if (rest.isEmpty()) {
        continue;
      }
      sb.append(text, last, m.start());
      sb.append(rewrite(start, rest));
      last = m.end();
    }
    sb.append(text, last, text.length());
    return sb.toString();
  }

  /** Reescribe una ruta; lo que sigue al nombre final (texto del mensaje) se conserva. */
  private static String rewrite(String start, String rest) {
    StringBuilder out = new StringBuilder();
    if (start.startsWith("\\\\")) {
      out.append("\\\\<servidor>\\<recurso>");
    } else {
      out.append(start);
    }
    // Separadores y segmentos, conservando el separador original.
    Matcher sep = SEP.matcher(rest);
    List<String> seps = new ArrayList<>();
    List<String> segs = new ArrayList<>();
    int pos = 0;
    while (sep.find()) {
      if (sep.start() > pos || !seps.isEmpty()) {
        segs.add(rest.substring(pos, sep.start()));
      }
      seps.add(sep.group());
      pos = sep.end();
    }
    segs.add(rest.substring(pos));
    // segs.size() == seps.size(): cada separador precede a su segmento.
    String tail = "";
    for (int i = 0; i < segs.size(); i++) {
      String seg = segs.get(i);
      boolean lastSeg = i == segs.size() - 1;
      String shown;
      if (lastSeg) {
        Matcher ext = EXT_END.matcher(seg);
        if (ext.find()) {
          tail = seg.substring(ext.end());
          seg = seg.substring(0, ext.end());
        }
        shown = fileName(seg);
      } else {
        shown = SAFE_DIRS.contains(seg.strip().toLowerCase(Locale.ROOT)) ? seg : ELLIPSIS;
      }
      boolean collapse =
          shown.equals(ELLIPSIS) && out.length() > 0 && out.toString().endsWith(ELLIPSIS);
      if (!collapse) {
        out.append(i < seps.size() ? seps.get(i) : "").append(shown);
      }
    }
    return out + tail;
  }

  private static String fileName(String seg) {
    if (seg.isEmpty()) {
      return seg;
    }
    String trimmed = seg.strip();
    if (SAFE_DIRS.contains(trimmed.toLowerCase(Locale.ROOT))
        || SAFE_FILE.matcher(trimmed).matches()) {
      return seg;
    }
    int dot = trimmed.lastIndexOf('.');
    if (dot > 0 && dot < trimmed.length() - 1) {
      String ext = trimmed.substring(dot + 1);
      if (ext.length() <= 8 && ext.chars().allMatch(Character::isLetterOrDigit)) {
        return (IMAGE_EXT.contains(ext.toLowerCase(Locale.ROOT)) ? "<foto>." : "<archivo>.") + ext;
      }
    }
    return ELLIPSIS;
  }
}
