package io.github.erenriquezp.rora.app.update;

import io.github.erenriquezp.rora.wallpapers.json.Json;
import io.github.erenriquezp.rora.wallpapers.json.JsonException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lee la lista de releases de la API de GitHub y elige la más nueva publicable: sin borradores y
 * con etiqueta {@code vX.Y.Z}. Puro: sin red, se prueba con texto.
 */
public final class ReleaseFeed {

  /** Solo se abre en el navegador una página de releases del propio proyecto. */
  public static final String RELEASES_PAGE = "https://github.com/Erenriquezp/rora-studio/releases";

  private ReleaseFeed() {}

  /** Release candidata. */
  record Release(VersionTag version, String page, String published) {}

  /**
   * @throws JsonException si el texto no es la lista esperada
   */
  public static UpdateResult evaluate(String json, VersionTag current) throws JsonException {
    Optional<Release> best = newest(json);
    if (best.isEmpty() || best.get().version().compareTo(current) <= 0) {
      return new UpdateResult.UpToDate(current);
    }
    Release r = best.get();
    return new UpdateResult.NewVersion(
        r.version(), r.page(), r.published(), r.version().major() > current.major());
  }

  static Optional<Release> newest(String json) throws JsonException {
    if (!(Json.parse(json) instanceof List<?> list)) {
      throw new JsonException("se esperaba una lista de releases", 1, 1);
    }
    Release best = null;
    for (Object o : list) {
      if (!(o instanceof Map<?, ?> m)) {
        throw new JsonException("release que no es un objeto", 1, 1);
      }
      if (Boolean.TRUE.equals(m.get("draft"))) {
        continue;
      }
      Optional<VersionTag> v =
          m.get("tag_name") instanceof String tag ? VersionTag.parseTag(tag) : Optional.empty();
      if (v.isEmpty()) {
        continue;
      }
      if (best == null || v.get().compareTo(best.version()) > 0) {
        best = new Release(v.get(), safePage(m.get("html_url")), date(m.get("published_at")));
      }
    }
    return Optional.ofNullable(best);
  }

  /** La página de la release solo si es del propio proyecto; si no, la lista de releases. */
  static String safePage(Object url) {
    if (url instanceof String s
        && s.startsWith(RELEASES_PAGE + "/")
        && !s.contains("..")
        && s.chars().noneMatch(c -> c <= ' ' || c == '"' || c == '<' || c == '>' || c == '\\')) {
      return s;
    }
    return RELEASES_PAGE;
  }

  private static String date(Object value) {
    if (value instanceof String s
        && s.length() >= 10
        && s.substring(0, 10).matches("\\d{4}-\\d{2}-\\d{2}")) {
      return s.substring(0, 10);
    }
    return "";
  }
}
