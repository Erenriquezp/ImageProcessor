package io.github.erenriquezp.rora.app.diag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Redacción con un entorno ficticio: no depende del usuario ni del SO de quien ejecuta el test. */
class RedactorTest {

  private static final Redactor R =
      new Redactor(
          new Redactor.Context(
              "C:\\Users\\Juan Pérez",
              "C:\\Users\\Juan Pérez\\AppData\\Local",
              "C:\\Users\\Juan Pérez\\AppData\\Roaming",
              "C:\\Users\\JUANPE~1\\AppData\\Local\\Temp",
              "juanp",
              "PORTATIL-JUAN"));

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        // perfil, tildes y espacios
        "No se pudo abrir C:\\Users\\Juan Pérez\\Pictures\\Boda Ana y Luis\\IMG_0001.JPG"
            + "|No se pudo abrir %USERPROFILE%\\Pictures\\…\\<foto>.JPG",
        // barras normales y mayúsculas distintas
        "c:/users/juan pérez/Desktop/vacaciones 2024/foto 1.jpeg"
            + "|%USERPROFILE%/Desktop/…/<foto>.jpeg",
        // carpetas de la app, que sí se conservan
        "C:\\Users\\Juan Pérez\\AppData\\Local\\RoraStudio\\logs\\rora-0.log"
            + "|%LOCALAPPDATA%\\RoraStudio\\logs\\rora-0.log",
        "C:\\Users\\Juan Pérez\\AppData\\Roaming\\RoraStudio\\wallpapers\\mis tamaños.json"
            + "|%APPDATA%\\RoraStudio\\wallpapers\\<archivo>.json",
        // forma corta 8.3 del temporal
        "C:\\Users\\JUANPE~1\\AppData\\Local\\Temp\\onnxruntime-java123"
            + "|%TEMP%\\onnxruntime-java123",
        // otro perfil cualquiera
        "C:\\Users\\María José\\Documents\\cv.pdf|%USERPROFILE%\\Documents\\<archivo>.pdf",
        // otras unidades
        "D:\\Fotos\\Familia García\\2019\\playa.tiff|D:\\…\\<foto>.tiff",
        // UNC
        "\\\\NAS-CASA\\fotos\\Viaje\\DSC_0042.NEF|\\\\<servidor>\\<recurso>\\…\\<foto>.NEF",
        // URI de archivo
        "file:/C:/Users/Juan%20Pérez/x.png|%USERPROFILE%/<foto>.png",
        "file:///D:/Fotos/x.png|file:///D:/…/<foto>.png",
        // el texto que sigue a la ruta se conserva
        "C:\\Datos\\Juan\\informe final.docx no existe|C:\\…\\<archivo>.docx no existe",
        // nombres con puntos: no se filtra el apellido
        "E:\\x\\Juan.Perez.jpg|E:\\…\\<foto>.jpg",
        // temporal de exportación y DLL: se conservan
        "D:\\Mis cosas\\.rora-export-3f2a.tmp|D:\\…\\.rora-export-3f2a.tmp",
        // usuario y equipo sueltos, sin romper paquetes Java
        "Usuario juanp en PORTATIL-JUAN|Usuario <usuario> en <equipo>",
      })
  void redactsPersonalData(String in, String expected) {
    assertThat(R.redact(in)).isEqualTo(expected);
  }

  @Test
  void leavesStackTracesUrlsAndNumbersAlone() {
    String trace =
        "java.nio.file.AccessDeniedException: x\n\tat java.base/sun.nio.fs.WindowsException"
            + ".translateToIOException(WindowsException.java:89)\n"
            + "https://github.com/Erenriquezp/rora-studio/releases 10:15 1/2 ERROR: fallo";
    assertThat(R.redact(trace)).isEqualTo(trace);
  }

  @Test
  void aUserNamedLikeAPackageDoesNotBreakTraces() {
    Redactor java = new Redactor(new Redactor.Context(null, null, null, null, "java", null));
    assertThat(java.redact("at java.base/java.lang.Thread.run(Thread.java:1474) por java"))
        .isEqualTo("at java.base/java.lang.Thread.run(Thread.java:1474) por <usuario>");
  }

  @Test
  void longPathsAndQuotes() {
    String deep = "C:\\Users\\Juan Pérez\\" + "carpeta\\".repeat(60) + "final.jpg";
    assertThat(deep.length()).isGreaterThan(260);
    assertThat(R.redact("'" + deep + "'")).isEqualTo("'%USERPROFILE%\\…\\<foto>.jpg'");
    assertThat(R.redact("\"D:\\a b\\c.png\" y \"E:\\d\\e.txt\""))
        .isEqualTo("\"D:\\…\\<foto>.png\" y \"E:\\…\\<archivo>.txt\"");
  }

  @Test
  void emojiAndCjkInPaths() {
    assertThat(R.redact("C:\\Users\\Juan Pérez\\写真\\🎉 fiesta\\猫.webp"))
        .isEqualTo("%USERPROFILE%\\…\\<foto>.webp");
  }

  @Test
  void nothingPersonalSurvivesInAMixedLog() {
    String log =
        "WARN rora.render fallo al abrir C:\\Users\\Juan Pérez\\Pictures\\Juan en la playa.jpg\n"
            + "Caused by: java.io.IOException: \\\\NAS-CASA\\privado\\juanp\\notas.txt";
    String out = R.redact(log);
    assertThat(out)
        .doesNotContain("Juan")
        .doesNotContain("juanp")
        .doesNotContain("playa")
        .doesNotContain("NAS-CASA")
        .doesNotContain("privado")
        .doesNotContain("notas");
  }

  @Test
  void posixPathsAreRedactedToo() {
    assertThat(R.redact("No se pudo abrir /home/juan/Fotos/Boda Ana/IMG_0001.jpg"))
        .isEqualTo("No se pudo abrir /…/<foto>.jpg");
    assertThat(R.redact("export: /tmp/junit123/archivo-normal.jpg (Permission denied)"))
        .isEqualTo("export: /tmp/…/<foto>.jpg (Permission denied)");
    assertThat(R.redact("en '/mnt/datos/privado/nota.txt'")).isEqualTo("en '/…/<archivo>.txt'");
  }

  @Test
  void posixRuleLeavesUrlsMimeTypesModulesAndFractionsAlone() {
    String s =
        "at java.base/java.nio.file.Files.read(Files.java:1) image/jpeg 3 / 4 1/2 "
            + "https://api.github.com/repos/Erenriquezp/rora-studio/releases /tmp";
    assertThat(R.redact(s)).isEqualTo(s);
  }

  @Test
  void nullAndEmpty() {
    assertThat(R.redact(null)).isNull();
    assertThat(R.redact("")).isEmpty();
  }
}
