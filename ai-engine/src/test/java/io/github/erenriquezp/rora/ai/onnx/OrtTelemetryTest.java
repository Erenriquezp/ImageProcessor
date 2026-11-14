package io.github.erenriquezp.rora.ai.onnx;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import ai.onnxruntime.OrtEnvironment;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * La telemetría de ONNX Runtime se desactiva al crear el entorno, y nadie puede crearlo por otro
 * camino. No se puede observar el estado interno de ORT: se prueba que la llamada se hace sin error
 * con los nativos reales y que el único punto de creación es {@link OrtEnvironments}.
 */
class OrtTelemetryTest {

  @Test
  void theSharedEnvironmentIsCreatedWithTelemetryOff() throws Exception {
    OrtNative.prepare();
    OrtEnvironment env = OrtEnvironments.quiet();
    assertThat(env).isNotNull();
    assertThat(OrtEnvironments.telemetryDisabled()).isTrue();
    assertThat(OrtEnvironments.quiet()).isSameAs(env);
  }

  @Test
  void onlyOrtEnvironmentsObtainsTheEnvironment() {
    JavaClasses classes = new ClassFileImporter().importPath(Path.of("target", "classes"));
    noClasses()
        .that()
        .doNotHaveFullyQualifiedName(OrtEnvironments.class.getName())
        .should()
        .callMethod(OrtEnvironment.class, "getEnvironment")
        .orShould()
        .callMethod(OrtEnvironment.class, "getEnvironment", String.class)
        .orShould()
        .callMethod(OrtEnvironment.class, "getEnvironment", ai.onnxruntime.OrtLoggingLevel.class)
        .orShould()
        .callMethod(
            OrtEnvironment.class,
            "getEnvironment",
            ai.onnxruntime.OrtLoggingLevel.class,
            String.class)
        .check(classes);
  }
}
