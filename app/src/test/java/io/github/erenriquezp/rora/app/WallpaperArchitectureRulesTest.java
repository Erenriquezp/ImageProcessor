package io.github.erenriquezp.rora.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.github.erenriquezp.rora.wallpapers.render.FillSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Wallpapers en {@code app}: el render (salvo el valor {@code FillSpec}), la tanda y los detectores
 * solo desde {@code app.session.wallpaper} (y la creación del detector, desde {@code
 * app.session.ai}); vistas y ViewModels solo ven el plan y los presets. Sin red.
 */
class WallpaperArchitectureRulesTest {

  private static final String WALLPAPER_SESSION =
      "io.github.erenriquezp.rora.app.session.wallpaper..";
  private static final String AI_SESSION = "io.github.erenriquezp.rora.app.session.ai..";

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes = new ClassFileImporter().importPath(Path.of("target", "classes"));
  }

  @Test
  void wallpaperSessionClassesDoNotUseTheNetwork() {
    noClasses()
        .that()
        .resideInAPackage(WALLPAPER_SESSION)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("java.net..", "javax.net..", "java.net.http..")
        .check(classes);
  }

  @Test
  void onlyTheWallpaperSessionRendersAndWrites() {
    noClasses()
        .that()
        .resideOutsideOfPackage(WALLPAPER_SESSION)
        .should()
        .dependOnClassesThat(
            JavaClass.Predicates.resideInAPackage("io.github.erenriquezp.rora.wallpapers.render..")
                .and(
                    DescribedPredicate.not(
                        JavaClass.Predicates.equivalentTo(FillSpec.class)
                            .or(JavaClass.Predicates.equivalentTo(FillSpec.Kind.class)))))
        .orShould()
        .dependOnClassesThat()
        .haveFullyQualifiedName("io.github.erenriquezp.rora.wallpapers.plan.WallpaperJob")
        .orShould()
        .dependOnClassesThat()
        .haveFullyQualifiedName("io.github.erenriquezp.rora.wallpapers.plan.TargetSink")
        .check(classes);
  }

  @Test
  void detectorsAreOnlyHandledInTheSessionLayers() {
    noClasses()
        .that()
        .resideOutsideOfPackages(WALLPAPER_SESSION, AI_SESSION)
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("io.github.erenriquezp.rora.wallpapers.subject.SubjectDetector")
        .check(classes);
  }
}
