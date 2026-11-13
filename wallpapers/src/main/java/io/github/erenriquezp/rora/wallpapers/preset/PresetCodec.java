package io.github.erenriquezp.rora.wallpapers.preset;

import io.github.erenriquezp.rora.wallpapers.json.Json;
import io.github.erenriquezp.rora.wallpapers.json.JsonException;
import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset.Group;
import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset.Orientation;
import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset.Origin;
import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset.Status;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Esquema JSON de los presets, versión 1.
 *
 * <pre>{@code
 * { "schema": 1, "revision": "2026-11-13",
 *   "presets": [ { "id", "name", "group", "width", "height", "orientation", "status", "source",
 *                  "reviewed"?, "safeZones"?: [ { "kind", "rect": [x, y, w, h] } ],
 *                  "parallaxMargin"? } ] }
 * }</pre>
 *
 * <p>Las claves que empiezan por {@code _} son comentarios. Una clave desconocida invalida la
 * entrada (suele ser una errata). Un error de archivo invalida el archivo; uno de entrada, solo la
 * entrada.
 */
public final class PresetCodec {

  /** Única versión del esquema que se entiende. */
  public static final int SCHEMA = 1;

  private static final Set<String> KEYS =
      Set.of(
          "id",
          "name",
          "group",
          "width",
          "height",
          "orientation",
          "status",
          "source",
          "reviewed",
          "safeZones",
          "parallaxMargin");

  private PresetCodec() {}

  /** Presets leídos y entradas descartadas. */
  public record Parsed(List<DevicePreset> presets, List<PresetProblem> problems) {
    public Parsed {
      presets = List.copyOf(presets);
      problems = List.copyOf(problems);
    }
  }

  /** El archivo entero no se puede usar. */
  public static final class FileException extends Exception {
    private static final long serialVersionUID = 1L;

    public FileException(String detail) {
      super(detail);
    }
  }

  /**
   * Lee un documento. Los ids repetidos dentro del archivo se descartan (gana el primero).
   *
   * @param sourceName nombre para los avisos, sin ruta
   */
  public static Parsed read(String text, String sourceName, Origin origin) throws FileException {
    Object root;
    try {
      root = Json.parse(text);
    } catch (JsonException e) {
      throw new FileException("JSON no válido: " + e.getMessage());
    }
    if (!(root instanceof Map<?, ?> doc)) {
      throw new FileException("el documento no es un objeto");
    }
    Object schema = doc.get("schema");
    if (!(schema instanceof BigDecimal s) || s.compareTo(BigDecimal.valueOf(SCHEMA)) != 0) {
      throw new FileException("versión de esquema no admitida: " + schema);
    }
    if (!(doc.get("presets") instanceof List<?> list)) {
      throw new FileException("falta la lista \"presets\"");
    }
    List<DevicePreset> out = new ArrayList<>();
    List<PresetProblem> problems = new ArrayList<>();
    Set<String> seen = new java.util.HashSet<>();
    for (Object item : list) {
      String id = item instanceof Map<?, ?> m && m.get("id") instanceof String sid ? sid : "";
      try {
        DevicePreset p = entry(item, origin);
        if (!seen.add(p.id())) {
          problems.add(new PresetProblem(sourceName, p.id(), "id repetido en el archivo"));
          continue;
        }
        out.add(p);
      } catch (IllegalArgumentException e) {
        problems.add(new PresetProblem(sourceName, id, e.getMessage()));
      }
    }
    return new Parsed(out, problems);
  }

  private static DevicePreset entry(Object item, Origin origin) {
    if (!(item instanceof Map<?, ?> m)) {
      throw new IllegalArgumentException("la entrada no es un objeto");
    }
    for (Object k : m.keySet()) {
      String key = (String) k;
      if (!key.startsWith("_") && !KEYS.contains(key)) {
        throw new IllegalArgumentException("clave desconocida \"" + key + "\"");
      }
    }
    String id = text(m, "id");
    Group group =
        Group.fromJson(text(m, "group"))
            .orElseThrow(() -> new IllegalArgumentException("grupo desconocido"));
    Orientation orientation =
        Orientation.fromJson(text(m, "orientation"))
            .orElseThrow(() -> new IllegalArgumentException("orientación desconocida"));
    Status status =
        Status.fromJson(text(m, "status"))
            .orElseThrow(() -> new IllegalArgumentException("estado desconocido"));
    if ((status == Status.USER) != (origin == Origin.USER)) {
      throw new IllegalArgumentException("el estado \"user\" es solo para presets propios");
    }
    Optional<LocalDate> reviewed = Optional.empty();
    if (m.containsKey("reviewed")) {
      try {
        reviewed = Optional.of(LocalDate.parse(text(m, "reviewed")));
      } catch (DateTimeParseException e) {
        throw new IllegalArgumentException("fecha \"reviewed\" no válida");
      }
    }
    List<SafeZone> zones = new ArrayList<>();
    if (m.containsKey("safeZones")) {
      if (!(m.get("safeZones") instanceof List<?> zl)) {
        throw new IllegalArgumentException("\"safeZones\" no es una lista");
      }
      for (Object z : zl) {
        zones.add(zone(z));
      }
    }
    double parallax = m.containsKey("parallaxMargin") ? number(m, "parallaxMargin") : 0;
    return new DevicePreset(
        id,
        text(m, "name"),
        group,
        integer(m, "width"),
        integer(m, "height"),
        orientation,
        status,
        text(m, "source"),
        reviewed,
        zones,
        parallax,
        origin);
  }

  private static SafeZone zone(Object z) {
    if (!(z instanceof Map<?, ?> m)) {
      throw new IllegalArgumentException("zona segura no válida");
    }
    SafeZone.Kind kind =
        SafeZone.Kind.fromJson(text(m, "kind"))
            .orElseThrow(() -> new IllegalArgumentException("tipo de zona desconocido"));
    if (!(m.get("rect") instanceof List<?> r) || r.size() != 4) {
      throw new IllegalArgumentException("\"rect\" debe tener 4 números");
    }
    double[] v = new double[4];
    for (int i = 0; i < 4; i++) {
      if (!(r.get(i) instanceof BigDecimal d)) {
        throw new IllegalArgumentException("\"rect\" debe tener 4 números");
      }
      v[i] = d.doubleValue();
    }
    return new SafeZone(kind, v[0], v[1], v[2], v[3]);
  }

  private static String text(Map<?, ?> m, String key) {
    if (!(m.get(key) instanceof String s)) {
      throw new IllegalArgumentException("falta el texto \"" + key + "\"");
    }
    return s;
  }

  private static double number(Map<?, ?> m, String key) {
    if (!(m.get(key) instanceof BigDecimal d)) {
      throw new IllegalArgumentException("falta el número \"" + key + "\"");
    }
    return d.doubleValue();
  }

  private static int integer(Map<?, ?> m, String key) {
    if (!(m.get(key) instanceof BigDecimal d)) {
      throw new IllegalArgumentException("falta el número \"" + key + "\"");
    }
    try {
      return d.intValueExact();
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException("\"" + key + "\" no es un entero");
    }
  }

  /** Documento con los presets dados (para los propios). Orden de entrada; salida estable. */
  public static String write(List<DevicePreset> presets, LocalDate revision) {
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("schema", SCHEMA);
    doc.put("revision", revision.toString());
    List<Object> list = new ArrayList<>();
    for (DevicePreset p : presets) {
      Map<String, Object> e = new LinkedHashMap<>();
      e.put("id", p.id());
      e.put("name", p.name());
      e.put("group", p.group().json());
      e.put("width", p.width());
      e.put("height", p.height());
      e.put("orientation", p.orientation().json());
      e.put("status", p.status().json());
      e.put("source", p.source());
      p.reviewed().ifPresent(d -> e.put("reviewed", d.toString()));
      if (!p.safeZones().isEmpty()) {
        List<Object> zones = new ArrayList<>();
        for (SafeZone z : p.safeZones()) {
          Map<String, Object> zm = new LinkedHashMap<>();
          zm.put("kind", z.kind().json());
          zm.put("rect", List.of(z.x(), z.y(), z.w(), z.h()));
          zones.add(zm);
        }
        e.put("safeZones", zones);
      }
      if (p.parallaxMargin() > 0) {
        e.put("parallaxMargin", p.parallaxMargin());
      }
      list.add(e);
    }
    doc.put("presets", list);
    return Json.write(doc);
  }
}
