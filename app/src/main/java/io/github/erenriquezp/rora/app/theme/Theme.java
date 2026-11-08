package io.github.erenriquezp.rora.app.theme;

import atlantafx.base.theme.PrimerDark;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.text.Font;

/** Tema de la interfaz nueva: AtlantaFX, tokens propios y la fuente incluida. */
public final class Theme {

  private static final List<String> FONTS =
      List.of("fonts/IBMPlexSans-Regular.ttf", "fonts/IBMPlexSans-SemiBold.ttf");

  private Theme() {}

  /** Activa AtlantaFX para toda la aplicación y carga la fuente. */
  public static void install() {
    Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
    for (String font : FONTS) {
      try (InputStream in = Theme.class.getResourceAsStream(font)) {
        Font.loadFont(Objects.requireNonNull(in, font), 13);
      } catch (IOException e) {
        throw new IllegalStateException("No se pudo cargar la fuente " + font, e);
      }
    }
  }

  /** Añade las hojas de estilo propias a una escena. */
  public static void apply(Scene scene) {
    scene
        .getStylesheets()
        .addAll(
            Objects.requireNonNull(Theme.class.getResource("tokens.css")).toExternalForm(),
            Objects.requireNonNull(Theme.class.getResource("studio.css")).toExternalForm(),
            Objects.requireNonNull(Theme.class.getResource("color-chooser.css")).toExternalForm());
  }
}
