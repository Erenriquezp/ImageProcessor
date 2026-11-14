package io.github.erenriquezp.rora.app.diag;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Textos legales incluidos en el JAR de la app (los muestra "Acerca de" aunque falte la carpeta
 * {@code legal} de la instalación) y versiones de los componentes principales.
 */
public final class Notices {

  public static final String THIRD_PARTY = "THIRD_PARTY_LICENSES.md";
  public static final String MODELS = "MODEL_NOTICE.md";
  public static final String LICENSE = "LICENSE";
  public static final String SOURCE = "SOURCE.txt";
  public static final String FONT_AWESOME = "FontAwesome-6.5.2-LICENSE.txt";
  public static final String ORT = "onnxruntime-1.31.0-LICENSE.txt";

  /** Todo lo que se copia a {@code legal\} al empaquetar. */
  public static final List<String> ALL =
      List.of(THIRD_PARTY, MODELS, LICENSE, SOURCE, FONT_AWESOME, ORT);

  private Notices() {}

  /** Contenido del aviso, o vacío si no está (p. ej. desde un IDE sin Maven). */
  public static String text(String name) {
    try (InputStream in = Notices.class.getResourceAsStream("legal/" + name)) {
      return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "";
    }
  }

  /** Versión de un módulo del runtime ({@code com.microsoft.onnxruntime}), si la declara. */
  public static Optional<String> moduleVersion(String module) {
    return ModuleLayer.boot()
        .findModule(module)
        .map(Module::getDescriptor)
        .flatMap(ModuleDescriptor::rawVersion);
  }

  /** Versión de JavaFX que publica el propio toolkit, o vacío antes de arrancarlo. */
  public static Optional<String> javafxVersion() {
    String v = System.getProperty("javafx.runtime.version", System.getProperty("javafx.version"));
    return Optional.ofNullable(v).filter(s -> !s.isBlank());
  }
}
