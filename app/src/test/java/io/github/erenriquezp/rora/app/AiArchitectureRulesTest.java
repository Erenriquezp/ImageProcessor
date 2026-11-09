package io.github.erenriquezp.rora.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** La IA de {@code app}: sin red, y el motor solo se ve desde {@code app.session.ai}. */
class AiArchitectureRulesTest {

  private static final String AI_SESSION = "io.github.erenriquezp.rora.app.session.ai..";

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes = new ClassFileImporter().importPath(Path.of("target", "classes"));
  }

  @Test
  void aiClassesDoNotUseTheNetwork() {
    noClasses()
        .that()
        .resideInAPackage(AI_SESSION)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("java.net..", "javax.net..", "java.net.http..")
        .check(classes);
  }

  @Test
  void onnxRuntimeTypesNeverReachTheApp() {
    noClasses().should().dependOnClassesThat().resideInAPackage("ai.onnxruntime..").check(classes);
  }

  @Test
  void onlyTheAiSessionLayerSeesTheEngine() {
    noClasses()
        .that()
        .resideOutsideOfPackage(AI_SESSION)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("io.github.erenriquezp.rora.ai..")
        .check(classes);
  }
}
