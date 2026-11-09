package io.github.erenriquezp.rora.app.catalog;

import io.github.erenriquezp.rora.app.i18n.Messages;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Filtros que ve el usuario, con nombres y categorías suyos. Es presentación: el núcleo no sabe
 * nada de esto. {@code CatalogTest} exige que todo id del registro esté clasificado aquí.
 */
public final class Catalog {

  /** Categorías en lenguaje de usuario, en orden de aparición. */
  public enum Category {
    BLACK_AND_WHITE("catalog.category.bw"),
    COLOR("catalog.category.color"),
    STYLES("catalog.category.styles"),
    EFFECTS("catalog.category.effects"),
    CORRECTION("catalog.category.correction"),
    LAB("catalog.category.lab");

    public final String labelKey;

    Category(String labelKey) {
      this.labelKey = labelKey;
    }
  }

  /**
   * @param fixedParams parámetros que la entrada fija (p. ej. el preset de una matriz)
   * @param hiddenParams parámetros que no se muestran
   */
  public record Entry(
      String id,
      String opId,
      Category category,
      String icon,
      Map<String, Object> fixedParams,
      Set<String> hiddenParams) {

    public String labelKey() {
      return "catalog.entry." + id;
    }

    public String synonymsKey() {
      return "catalog.synonyms." + id;
    }
  }

  private static Entry entry(String id, String opId, Category c, String icon) {
    return new Entry(id, opId, c, icon, Map.of(), Set.of());
  }

  private static Entry preset(String id, String preset, String icon) {
    return new Entry(
        id,
        "color.matrix-preset",
        Category.STYLES,
        icon,
        Map.of("preset", preset),
        Set.of("preset"));
  }

  public static final List<Entry> ENTRIES =
      List.of(
          entry("luminance", "point.luminance", Category.BLACK_AND_WHITE, "fas-adjust"),
          entry("threshold", "point.soft-threshold", Category.BLACK_AND_WHITE, "fas-square"),
          entry("negative", "color.negative", Category.COLOR, "fas-yin-yang"),
          entry("hue", "point.hue-rotate", Category.COLOR, "fas-palette"),
          entry("duotone", "color.recolor", Category.COLOR, "fas-tint"),
          preset("sepia", "sepia", "fas-image"),
          preset("polaroid", "polaroid", "fas-camera-retro"),
          preset("kodachrome", "kodachrome", "fas-film"),
          entry("blur", "blur.gaussian", Category.EFFECTS, "fas-water"),
          entry("sharpen", "detail.unsharp-mask", Category.EFFECTS, "fas-gem"),
          entry("motion", "accum.motion", Category.EFFECTS, "fas-wind"),
          entry("pixelate", "raster.pixelate", Category.EFFECTS, "fas-chess-board"),
          entry("posterize", "tone.posterize", Category.EFFECTS, "fas-layer-group"),
          entry("posterize-channels", "tone.posterize-channels", Category.EFFECTS, "fas-sliders-h"),
          new Entry(
              "equalize",
              "tone.equalize",
              Category.CORRECTION,
              "fas-chart-bar",
              Map.of("markBurned", false, "markDark", false),
              Set.of("markBurned", "markDark")));

  /** Operaciones del registro que no aparecen en el catálogo; su clasificación es deliberada. */
  public static final Set<String> HIDDEN_OPERATIONS =
      Set.of(
          // Sustituidas por los Ajustes básicos
          "color.brightness",
          "color.hsv",
          "point.saturation",
          "point.scale-bias",
          "point.color-gain",
          // Duplicadas o pendientes de otra fase
          "color.grayscale-average",
          "color.threshold-bw",
          "color.grayscale-quantized",
          "artistic.circular-fade",
          "raster.depth-map",
          "raster.texture-sample",
          "fragment.blend-color",
          // Internas o de sus propias herramientas
          "analysis.exposure",
          "analysis.histogram",
          "adjust.basic",
          "ai.enhance",
          "transform.crop",
          "transform.flip",
          "transform.resize",
          "transform.rotate90",
          "transform.straighten");

  private Catalog() {}

  /** Entradas cuyo nombre o sinónimos contienen {@code query} (sin distinguir mayúsculas). */
  public static List<Entry> search(String query, Messages messages) {
    return search(query, messages, ENTRIES);
  }

  /** Como {@link #search(String, Messages)}, sobre {@code entries}. */
  public static List<Entry> search(String query, Messages messages, List<Entry> entries) {
    String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    if (q.isEmpty()) {
      return entries;
    }
    return entries.stream()
        .filter(
            e ->
                messages.get(e.labelKey()).toLowerCase(Locale.ROOT).contains(q)
                    || messages.get(e.synonymsKey()).toLowerCase(Locale.ROOT).contains(q))
        .toList();
  }
}
