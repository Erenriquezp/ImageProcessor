package io.github.erenriquezp.rora.app.view;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.javafx.FontIcon;

/** Ayudas para crear botones de icono. */
final class Icons {

  private Icons() {}

  static FontIcon icon(String literal) {
    return new FontIcon(literal);
  }

  static Button iconButton(String literal, String tooltip, String... styles) {
    Button b = new Button();
    b.setGraphic(icon(literal));
    b.setTooltip(new Tooltip(tooltip));
    b.setAccessibleText(tooltip);
    b.getStyleClass().addAll(styles);
    return b;
  }
}
