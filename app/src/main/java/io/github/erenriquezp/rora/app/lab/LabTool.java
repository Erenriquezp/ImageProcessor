package io.github.erenriquezp.rora.app.lab;

import javafx.scene.Node;

/** Una pestaña de la ventana Laboratorio. */
public interface LabTool extends AutoCloseable {

  String title();

  Node view(LabContext context);

  @Override
  default void close() {}
}
