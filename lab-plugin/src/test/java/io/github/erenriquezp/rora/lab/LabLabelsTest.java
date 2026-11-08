package io.github.erenriquezp.rora.lab;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.app.catalog.Catalog;
import io.github.erenriquezp.rora.app.imaging.SourceFormat;
import io.github.erenriquezp.rora.app.lab.LabBundle;
import io.github.erenriquezp.rora.app.lab.LabBundles;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.ParamDef;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** El módulo se descubre y todos sus textos existen, sin claves huérfanas. */
class LabLabelsTest {

  private final LabBundle bundle = new LegacyLabBundle();

  @Test
  void theBundleIsDiscoveredByTheApp() {
    assertThat(LabBundles.discover()).hasSize(1);
  }

  @Test
  void thereAreFifteenOperationsWithACatalogEntryEach() {
    List<Operation> ops = bundle.operations(SourceFormat.NativeSource.NONE);
    assertThat(ops).hasSize(15);
    assertThat(bundle.catalogEntries())
        .extracting(Catalog.Entry::opId)
        .containsExactlyInAnyOrderElementsOf(ops.stream().map(Operation::id).toList());
  }

  private Set<String> usedKeys() {
    Set<String> used = new HashSet<>();
    for (Operation op : bundle.operations(SourceFormat.NativeSource.NONE)) {
      used.add("op." + op.id() + ".title");
      for (ParamDef d : op.schema().params()) {
        if (d.hidden()) {
          continue;
        }
        used.add(d.labelKey());
        if (d instanceof ParamDef.EnumParam e) {
          e.values().forEach(v -> used.add(d.labelKey() + "." + v));
        }
      }
    }
    for (Catalog.Entry e : bundle.catalogEntries()) {
      used.add(e.labelKey());
      used.add(e.synonymsKey());
    }
    return used;
  }

  @Test
  void everyLabelExistsAndNoneIsOrphan() {
    ResourceBundle labels = bundle.labels(Locale.of("es"));
    assertThat(labels.keySet()).containsExactlyInAnyOrderElementsOf(usedKeys());
  }

  @Test
  void thereAreThreeTools() {
    assertThat(bundle.tools()).extracting(t -> t.title()).hasSize(3);
  }
}
