package io.github.erenriquezp.rora.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Downscale;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.PointFusion;
import io.github.erenriquezp.rora.core.op.Renderer;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Reglas de arquitectura de la fase 2. La interfaz solo habla con la capa de sesión; el trabajo
 * pesado no se puede invocar desde una clase de interfaz sin pasar por el coordinador.
 */
class ArchitectureRulesTest {

  private static final String[] UI = {
    "io.github.erenriquezp.rora.app",
    "io.github.erenriquezp.rora.app.ui",
    "io.github.erenriquezp.rora.app.fx"
  };
  private static final String[] NEW_VIEWS = {
    "io.github.erenriquezp.rora.app.view..", "io.github.erenriquezp.rora.app.shell.."
  };
  private static final String VIEW_MODEL = "io.github.erenriquezp.rora.app.viewmodel..";
  private static final String MODEL = "io.github.erenriquezp.rora.app.model..";
  private static final String SESSION = "io.github.erenriquezp.rora.app.session";
  private static final String SERVICE = "io.github.erenriquezp.rora.service";

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    // Por ruta: bajo JPMS el escaneo por paquete no ve las clases del módulo.
    classes = new ClassFileImporter().importPath(Path.of("target", "classes"));
  }

  @Test
  void theUiDoesNotDependOnTheFacadeNorOnLegacyImplementations() {
    noClasses()
        .that()
        .resideInAnyPackage(UI)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(SERVICE)
        .check(classes);
  }

  @Test
  void noCoreProcessingIsInvokedFromTheUi() {
    noClasses()
        .that()
        .resideInAnyPackage(UI)
        .should()
        .dependOnClassesThat()
        .areAssignableTo(Operation.class)
        .orShould()
        .dependOnClassesThat()
        .areAssignableTo(Renderer.class)
        .orShould()
        .dependOnClassesThat()
        .areAssignableTo(PointFusion.class)
        .orShould()
        .dependOnClassesThat()
        .areAssignableTo(Downscale.class)
        .orShould()
        .dependOnClassesThat()
        .areAssignableTo(TileExecutor.class)
        .check(classes);
  }

  @Test
  void fullResolutionSwingConversionIsGone() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("javafx.embed.swing.SwingFXUtils")
        .check(classes);
  }

  @Test
  void theSessionLayerDoesNotUseJavaFx() {
    noClasses()
        .that()
        .resideInAPackage(SESSION)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("javafx..")
        .check(classes);
  }

  @Test
  void viewModelsDoNotUseNodesOrTheToolkit() {
    noClasses()
        .that()
        .resideInAPackage(VIEW_MODEL)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("javafx.scene..", "javafx.stage..", "javafx.application..")
        .check(classes);
  }

  @Test
  void pureModelsDoNotUseJavaFx() {
    noClasses()
        .that()
        .resideInAPackage(MODEL)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("javafx..")
        .check(classes);
  }

  @Test
  void newViewsOnlyTalkToViewModels() {
    noClasses()
        .that()
        .resideInAnyPackage(NEW_VIEWS)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(SERVICE, "io.github.erenriquezp.rora.core.op..")
        .check(classes);
  }

  @Test
  void viewModelsDoNotProcessImagesThemselves() {
    noClasses()
        .that()
        .resideInAPackage(VIEW_MODEL)
        .should()
        .dependOnClassesThat()
        .areAssignableTo(Renderer.class)
        .orShould()
        .dependOnClassesThat()
        .areAssignableTo(PointFusion.class)
        .orShould()
        .dependOnClassesThat()
        .areAssignableTo(Downscale.class)
        .orShould()
        .dependOnClassesThat()
        .areAssignableTo(TileExecutor.class)
        .check(classes);
  }
}
