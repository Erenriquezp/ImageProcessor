package io.github.erenriquezp.rora.app.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.app.i18n.Messages;
import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.ParamDef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

class CatalogAndLabelsTest {

  private static final OpRegistry REGISTRY = OpRegistry.load();
  private static final Messages MESSAGES = Messages.of(Locale.of("es"));

  @Test
  void everyOperationIsClassifiedExactlyOnce() {
    Set<String> visible = new HashSet<>();
    Catalog.ENTRIES.forEach(e -> visible.add(e.opId()));
    Set<String> classified = new HashSet<>(visible);
    classified.addAll(Catalog.HIDDEN_OPERATIONS);
    assertThat(classified).containsExactlyInAnyOrderElementsOf(REGISTRY.ids());
    assertThat(visible).doesNotContainAnyElementsOf(Catalog.HIDDEN_OPERATIONS);
  }

  @Test
  void theCatalogHasTheAgreedSize() {
    assertThat(Catalog.ENTRIES).hasSize(15);
  }

  @Test
  void entriesOnlyFixOrHideParametersThatExist() {
    for (Catalog.Entry e : Catalog.ENTRIES) {
      Operation op = REGISTRY.require(e.opId());
      for (String key : e.fixedParams().keySet()) {
        assertThat(op.schema().find(key)).as(e.id() + "." + key).isPresent();
      }
      for (String key : e.hiddenParams()) {
        assertThat(op.schema().find(key)).as(e.id() + "." + key).isPresent();
      }
    }
  }

  @Test
  void everyIconExistsInThePack() {
    Set<String> literals = new HashSet<>();
    for (Ikon ikon : FontAwesomeSolid.values()) {
      literals.add(ikon.getDescription());
    }
    for (Catalog.Entry e : Catalog.ENTRIES) {
      assertThat(literals).as(e.id()).contains(e.icon());
    }
  }

  @Test
  void searchFindsByNameAndSynonym() {
    assertThat(Catalog.search("nitid", MESSAGES)).extracting(Catalog.Entry::id).contains("sharpen");
    assertThat(Catalog.search("sharpen", MESSAGES))
        .extracting(Catalog.Entry::id)
        .contains("sharpen");
    assertThat(Catalog.search("  ", MESSAGES)).hasSize(Catalog.ENTRIES.size());
    assertThat(Catalog.search("zzzz", MESSAGES)).isEmpty();
  }

  // ── Completitud de textos ─────────────────────────────────────────────

  private static Set<String> operationKeys() {
    Set<String> keys = new HashSet<>();
    for (Operation op : REGISTRY.all()) {
      keys.add("op." + op.id() + ".title");
      for (ParamDef d : op.schema().params()) {
        keys.add(d.labelKey());
        List<String> values =
            switch (d) {
              case ParamDef.EnumParam e -> e.values();
              case ParamDef.SelectionParam s -> s.options();
              default -> List.of();
            };
        values.forEach(v -> keys.add(d.labelKey() + "." + v));
      }
    }
    return keys;
  }

  private static Set<String> catalogKeys() {
    Set<String> keys = new HashSet<>();
    for (Catalog.Entry e : Catalog.ENTRIES) {
      keys.add(e.labelKey());
      keys.add(e.synonymsKey());
    }
    for (Catalog.Category c : Catalog.Category.values()) {
      keys.add(c.labelKey);
    }
    return keys;
  }

  /** Claves {@code ui.*} de la interfaz e {@code io.*} del núcleo y los códecs. */
  private static Set<String> uiKeysInSource() throws Exception {
    String io = "io\\.(?:error|note)\\.[a-z0-9_]+";
    Set<String> keys = new HashSet<>();
    collectKeys(Path.of("src", "main", "java"), "ui\\.[a-zA-Z.]+", keys);
    collectKeys(Path.of("..", "core-imaging", "src", "main", "java"), io, keys);
    collectKeys(Path.of("..", "imaging-io", "src", "main", "java"), io, keys);
    return keys;
  }

  private static void collectKeys(Path root, String regex, Set<String> keys) throws Exception {
    Pattern p = Pattern.compile("\"(" + regex + ")\"");
    try (Stream<Path> files = Files.walk(root)) {
      for (Path f : files.filter(x -> x.toString().endsWith(".java")).toList()) {
        Matcher m = p.matcher(Files.readString(f));
        while (m.find()) {
          keys.add(m.group(1));
        }
      }
    }
  }

  @Test
  void everyKeyInUseExists() throws Exception {
    Set<String> used = new HashSet<>(operationKeys());
    used.addAll(catalogKeys());
    used.addAll(uiKeysInSource());
    assertThat(MESSAGES.keys()).containsAll(used);
  }

  @Test
  void thereAreNoOrphanKeys() throws Exception {
    Set<String> used = new HashSet<>(operationKeys());
    used.addAll(catalogKeys());
    used.addAll(uiKeysInSource());
    assertThat(used).containsAll(MESSAGES.keys());
  }

  @Test
  void anUnavailableLanguageFallsBackToSpanish() {
    assertThat(Messages.of(Locale.JAPANESE).locale().getLanguage()).isEqualTo("es");
  }
}
