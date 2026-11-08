package io.github.erenriquezp.rora.core.op;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.module.ModuleDescriptor;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Comprueba el módulo real (surefire ejecuta los tests en el <i>module path</i>): que solo depende
 * de {@code java.base} y que {@link OpRegistry#load()} descubre exactamente lo que declara {@code
 * provides}.
 */
class ModuleAndServiceLoaderTest {

  private static ModuleDescriptor descriptor() {
    Module m = Operation.class.getModule();
    assertThat(m.isNamed()).as("los tests deben correr como módulo").isTrue();
    assertThat(m.getName()).isEqualTo("io.github.erenriquezp.rora.core");
    return m.getDescriptor();
  }

  @Test
  void coreModuleRequiresOnlyJavaBase() {
    Set<String> requires =
        descriptor().requires().stream()
            .map(ModuleDescriptor.Requires::name)
            .collect(Collectors.toSet());
    assertThat(requires).containsExactly("java.base");
  }

  @Test
  void coreDoesNotExportImplementationPackages() {
    Set<String> exported =
        descriptor().exports().stream()
            .map(ModuleDescriptor.Exports::source)
            .collect(Collectors.toSet());
    assertThat(exported)
        .containsExactlyInAnyOrder(
            "io.github.erenriquezp.rora.core.image",
            "io.github.erenriquezp.rora.core.color",
            "io.github.erenriquezp.rora.core.op",
            "io.github.erenriquezp.rora.core.exec",
            "io.github.erenriquezp.rora.core.io");
    assertThat(descriptor().uses()).containsExactly(Operation.class.getName());
  }

  @Test
  void serviceLoaderFindsExactlyTheDeclaredProviders() {
    Set<String> declared =
        descriptor().provides().stream()
            .filter(p -> p.service().equals(Operation.class.getName()))
            .flatMap(p -> p.providers().stream())
            .collect(Collectors.toSet());
    Set<String> loaded =
        OpRegistry.load().all().stream()
            .map(op -> op.getClass().getName())
            .collect(Collectors.toSet());
    assertThat(loaded).isEqualTo(declared);
  }
}
