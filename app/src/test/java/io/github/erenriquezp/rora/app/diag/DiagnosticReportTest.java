package io.github.erenriquezp.rora.app.diag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DiagnosticReportTest {

  private static final Redactor R =
      new Redactor(
          new Redactor.Context(
              "C:\\Users\\Juan Pérez",
              "C:\\Users\\Juan Pérez\\AppData\\Local",
              null,
              null,
              "juanp",
              "PORTATIL-JUAN"));

  static DiagnosticReport.Facts facts(Optional<String> lastError, String tail) {
    return new DiagnosticReport.Facts(
        new AppInfo("0.1.0", "beta"),
        true,
        "es-EC",
        "25.0.5+8 (Eclipse Adoptium, OpenJDK 64-Bit Server VM)",
        List.of("-XX:MaxRAMPercentage=50", "-XX:-CreateCoredumpOnCrash"),
        "25.0.5",
        Optional.of(false),
        "Windows 11 10.0 amd64",
        8,
        "Intel64 Family 6 Model 126 Stepping 5, GenuineIntel",
        12L << 30,
        4L << 30,
        6L << 30,
        900L << 20,
        64L << 20,
        List.of("1536×864 lógicos, escala 125 %", "1920×1080 lógicos, escala 100 %"),
        "realesr-general-x4v3 1, 4 hilos",
        Optional.of("6.000 × 4.000 px JPEG"),
        List.of("adjust.basic", "blur.gaussian"),
        lastError,
        Optional.empty(),
        tail,
        ZonedDateTime.of(2026, 11, 14, 10, 15, 0, 0, ZoneOffset.ofHours(-5)));
  }

  @Test
  void sectionsInTheAgreedOrder() {
    String text = DiagnosticReport.build(facts(Optional.empty(), ""), R);
    assertThat(text)
        .startsWith(
            "Rora Studio 0.1.0 (beta) — informe de diagnóstico\nGenerado: 2026-11-14 10:15");
    String[] sections = {
      "[Aplicación]",
      "[Java]",
      "[JavaFX]",
      "[Sistema]",
      "[Memoria]",
      "[Pantallas]",
      "[Región]",
      "[IA]",
      "[Imagen]",
      "[Errores]",
      "[Registro (últimas líneas)]"
    };
    int last = -1;
    for (String s : sections) {
      int at = text.indexOf(s);
      assertThat(at).as(s).isGreaterThan(last);
      last = at;
    }
    assertThat(text)
        .contains("Aceleración por hardware: no (renderizado por software)")
        .contains("Pantalla 2: 1920×1080 lógicos, escala 100 %")
        .contains("Física: 12.0 GB (libre 4.0 GB)")
        .contains("Pasos activos: adjust.basic, blur.gaussian")
        .contains("Última abierta: 6.000 × 4.000 px JPEG")
        .contains("GPU: no disponible")
        .doesNotContain("Zona");
  }

  @Test
  void adversarialDataDoesNotSurvive() {
    String tail =
        "2026-11-14T10:00:00.000Z WARN  rora.app  No se pudo abrir "
            + "C:\\Users\\Juan Pérez\\Pictures\\Juan y Ana en la playa.jpg\n"
            + "    at java.base/java.nio.file.Files.newInputStream(Files.java:160)";
    String text =
        DiagnosticReport.build(
            facts(
                Optional.of(
                    "java.nio.file.AccessDeniedException: \\\\NAS-CASA\\fotos\\juanp\\boda.png"),
                tail),
            R);
    assertThat(text)
        .doesNotContain("Juan")
        .doesNotContain("juanp")
        .doesNotContain("playa")
        .doesNotContain("NAS-CASA")
        .doesNotContain("boda")
        .doesNotContain("PORTATIL");
    assertThat(text).contains("%USERPROFILE%\\Pictures\\<foto>.jpg").contains("<foto>.png");
  }

  @Test
  void jvmFlagsDropSystemPropertiesAndPaths() {
    assertThat(
            DiagnosticReport.jvmFlags(
                List.of(
                    "-XX:MaxRAMPercentage=50",
                    "-XX:ErrorFile=C:\\Users\\x\\logs\\hs_err_pid%p.log",
                    "-Djava.io.tmpdir=C:\\Users\\x\\tmp",
                    "-Drora.packaged=true",
                    "--enable-native-access=javafx.graphics,com.microsoft.onnxruntime",
                    "--patch-module",
                    "-Xmx2g")))
        .containsExactly(
            "-XX:MaxRAMPercentage=50",
            "-Drora.packaged=true",
            "--enable-native-access=javafx.graphics,com.microsoft.onnxruntime",
            "-Xmx2g");
  }

  @Test
  void humanSizes() {
    assertThat(DiagnosticReport.bytes(-1)).isEqualTo("no disponible");
    assertThat(DiagnosticReport.bytes(512)).isEqualTo("512 B");
    assertThat(DiagnosticReport.bytes(1536)).isEqualTo("1.5 KB");
    assertThat(DiagnosticReport.bytes(3L << 30)).isEqualTo("3.0 GB");
  }

  @Test
  void systemFactsAreAvailable() {
    DiagnosticReport.SystemFacts s = DiagnosticReport.system();
    assertThat(s.logicalCpus()).isPositive();
    assertThat(s.heapMax()).isPositive();
    assertThat(s.java()).contains(Integer.toString(Runtime.version().feature()));
  }
}
