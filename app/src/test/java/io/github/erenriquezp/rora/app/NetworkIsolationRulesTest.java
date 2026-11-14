package io.github.erenriquezp.rora.app;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.github.erenriquezp.rora.app.update.GitHubReleasesClient;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Fase 8: la red solo existe en {@link GitHubReleasesClient}. {@code java.net.URL} y {@code URI} se
 * permiten como valores (las hojas de estilo se cargan con {@code Class.getResource}), pero nadie
 * más puede abrir conexiones. Cubre todos los módulos del instalador.
 */
class NetworkIsolationRulesTest {

  private static final List<Path> MODULES =
      List.of(
          Path.of("target", "classes"),
          Path.of("..", "core-imaging", "target", "classes"),
          Path.of("..", "imaging-io", "target", "classes"),
          Path.of("..", "wallpapers", "target", "classes"),
          Path.of("..", "ai-engine", "target", "classes"));

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    for (Path p : MODULES) {
      assertThat(Files.isDirectory(p)).as(p.toString()).isTrue();
    }
    classes = new ClassFileImporter().importPaths(MODULES);
  }

  @Test
  void onlyTheReleasesClientUsesTheNetwork() {
    noClasses()
        .that()
        .doNotHaveFullyQualifiedName(GitHubReleasesClient.class.getName())
        .should()
        .dependOnClassesThat(
            resideInAnyPackage("java.net..", "javax.net..")
                .and(not(belongToAnyOf(URL.class, URI.class))))
        .check(classes);
  }

  @Test
  void nobodyOpensAnUrlOutsideTheClient() {
    noClasses()
        .that()
        .doNotHaveFullyQualifiedName(GitHubReleasesClient.class.getName())
        .should()
        .callMethod(URL.class, "openConnection")
        .orShould()
        .callMethod(URL.class, "openStream")
        .orShould()
        .callMethod(URL.class, "getContent")
        .check(classes);
  }

  @Test
  void theUpdatePackageKeepsTheNetworkInOneClass() {
    noClasses()
        .that()
        .resideInAPackage("io.github.erenriquezp.rora.app.update")
        .and()
        .doNotHaveFullyQualifiedName(GitHubReleasesClient.class.getName())
        .should()
        .dependOnClassesThat(resideInAPackage("java.net.."))
        .check(classes);
  }
}
