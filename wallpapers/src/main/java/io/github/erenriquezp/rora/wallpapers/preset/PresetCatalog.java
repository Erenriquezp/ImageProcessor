package io.github.erenriquezp.rora.wallpapers.preset;

import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset.Origin;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Presets disponibles: integrados, propios y pantallas, con ids únicos. Inmutable.
 *
 * <p>Orden: por grupo (orden de {@link DevicePreset.Group}) y, dentro, por área y ancho.
 */
public final class PresetCatalog {

  private static final String RESOURCE = "presets.json";

  private static volatile List<DevicePreset> builtIns;

  private final Map<String, DevicePreset> byId;
  private final List<PresetProblem> problems;

  private PresetCatalog(Map<String, DevicePreset> byId, List<PresetProblem> problems) {
    this.byId = byId;
    this.problems = List.copyOf(problems);
  }

  /** Presets incluidos con la aplicación. Un error aquí es un fallo de construcción. */
  public static List<DevicePreset> builtIns() {
    List<DevicePreset> b = builtIns;
    if (b == null) {
      try (InputStream in = PresetCatalog.class.getResourceAsStream(RESOURCE)) {
        if (in == null) {
          throw new IllegalStateException("falta el recurso " + RESOURCE);
        }
        PresetCodec.Parsed p =
            PresetCodec.read(
                new String(in.readAllBytes(), StandardCharsets.UTF_8),
                "integrados",
                Origin.BUILT_IN);
        if (!p.problems().isEmpty()) {
          throw new IllegalStateException("presets integrados no válidos: " + p.problems());
        }
        b = p.presets();
        builtIns = b;
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      } catch (PresetCodec.FileException e) {
        throw new IllegalStateException("presets integrados no válidos: " + e.getMessage(), e);
      }
    }
    return b;
  }

  /**
   * Une las fuentes. Un id repetido se descarta con aviso (gana el que llega antes: pantallas,
   * integrados, propios).
   */
  public static PresetCatalog of(
      List<DevicePreset> screens,
      List<DevicePreset> builtIn,
      List<DevicePreset> user,
      List<PresetProblem> earlierProblems) {
    Map<String, DevicePreset> map = new LinkedHashMap<>();
    List<PresetProblem> problems = new ArrayList<>(earlierProblems);
    List<DevicePreset> all = new ArrayList<>(screens);
    all.addAll(builtIn);
    all.addAll(user);
    for (DevicePreset p : all) {
      if (map.putIfAbsent(p.id(), p) != null) {
        problems.add(
            new PresetProblem(p.origin().name().toLowerCase(Locale.ROOT), p.id(), "id duplicado"));
      }
    }
    List<DevicePreset> sorted = new ArrayList<>(map.values());
    sorted.sort(
        Comparator.comparing(DevicePreset::group)
            .thenComparingLong(p -> (long) p.width() * p.height())
            .thenComparingInt(DevicePreset::width)
            .thenComparing(DevicePreset::id));
    Map<String, DevicePreset> ordered = new LinkedHashMap<>();
    for (DevicePreset p : sorted) {
      ordered.put(p.id(), p);
    }
    return new PresetCatalog(ordered, problems);
  }

  public List<DevicePreset> all() {
    return List.copyOf(byId.values());
  }

  public Optional<DevicePreset> byId(String id) {
    return Optional.ofNullable(byId.get(id));
  }

  /** Avisos de lo que se ignoró al construir el catálogo. */
  public List<PresetProblem> problems() {
    return problems;
  }

  /**
   * Presets cuyo nombre, grupo o {@code ancho×alto} contienen todas las palabras de la consulta,
   * sin distinguir mayúsculas ni tildes. Consulta vacía = todos.
   */
  public List<DevicePreset> search(String query, Map<DevicePreset.Group, String> groupNames) {
    String[] words = fold(query).trim().split("\\s+");
    List<DevicePreset> out = new ArrayList<>();
    for (DevicePreset p : byId.values()) {
      String hay =
          fold(
              p.name()
                  + " "
                  + groupNames.getOrDefault(p.group(), p.group().json())
                  + " "
                  + p.width()
                  + "x"
                  + p.height()
                  + " "
                  + p.width()
                  + "×"
                  + p.height());
      boolean all = true;
      for (String w : words) {
        if (!w.isEmpty() && !hay.contains(w)) {
          all = false;
          break;
        }
      }
      if (all) {
        out.add(p);
      }
    }
    return out;
  }

  static String fold(String s) {
    String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return n.toLowerCase(Locale.ROOT).replace('×', 'x');
  }
}
