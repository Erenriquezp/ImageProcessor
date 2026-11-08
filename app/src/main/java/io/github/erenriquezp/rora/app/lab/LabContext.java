package io.github.erenriquezp.rora.app.lab;

import io.github.erenriquezp.rora.app.session.RenderFailure;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import javafx.stage.Window;

/** Lo que una herramienta del Laboratorio puede pedir al editor. */
public interface LabContext {

  Window owner();

  boolean hasImage();

  /** La imagen actual a resolución completa. */
  void currentImage(Consumer<BufferedImage> onImage, Consumer<RenderFailure> onFailed);

  /** Lleva {@code image} al editor como imagen nueva. */
  void useImage(BufferedImage image);

  void status(String message);
}
