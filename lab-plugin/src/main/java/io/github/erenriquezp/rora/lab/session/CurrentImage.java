package io.github.erenriquezp.rora.lab.session;

import io.github.erenriquezp.rora.app.session.RenderFailure;

import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * La imagen actual del editor para los paneles (blending, histograma). Se obtiene de forma
 * asíncrona porque puede requerir un render a resolución completa.
 */
public interface CurrentImage {

  /** ¿Hay una imagen cargada en el editor? */
  boolean isAvailable();

  /** Entrega la imagen en el hilo FX (de inmediato si ya está calculada para este estado). */
  void fetch(Consumer<BufferedImage> onImage, Consumer<RenderFailure> onFailed);
}
