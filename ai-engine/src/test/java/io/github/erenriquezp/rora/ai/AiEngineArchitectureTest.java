package io.github.erenriquezp.rora.ai;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** El motor no habla con la red y ONNX Runtime no sale de {@code ai.onnx}. */
class AiEngineArchitectureTest {

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes = new ClassFileImporter().importPath(Path.of("target", "classes"));
  }

  @Test
  void theEngineDoesNotUseTheNetwork() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("java.net..", "javax.net..")
        .check(classes);
  }

  @Test
  void onnxRuntimeStaysInItsPackage() {
    noClasses()
        .that()
        .resideOutsideOfPackage("io.github.erenriquezp.rora.ai.onnx..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("ai.onnxruntime..")
        .check(classes);
  }
}
