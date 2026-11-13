package io.github.erenriquezp.rora.wallpapers;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * La lógica de wallpapers es pura: sin AWT, ImageIO, JavaFX, ONNX Runtime ni red, y el módulo solo
 * requiere {@code java.base} y el núcleo.
 */
class WallpapersArchitectureTest {

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes = new ClassFileImporter().importPath(Path.of("target", "classes"));
  }

  @Test
  void theLogicIsPure() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "java.awt..", "javax.imageio..", "javax.swing..", "javafx..", "ai.onnxruntime..")
        .check(classes);
  }

  @Test
  void noClassUsesTheNetwork() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("java.net..", "javax.net..")
        .check(classes);
  }

  @Test
  void theModuleOnlyRequiresTheBaseAndTheCore() {
    ModuleDescriptor d = WallpapersArchitectureTest.class.getModule().getDescriptor();
    assertThat(d).as("los tests corren como módulo (JPMS)").isNotNull();
    Set<String> requires =
        d.requires().stream().map(ModuleDescriptor.Requires::name).collect(Collectors.toSet());
    // Surefire añade lecturas con --add-reads, no requires: el descriptor es el compilado.
    assertThat(requires)
        .containsExactlyInAnyOrder("java.base", "io.github.erenriquezp.rora.core");
  }
}
