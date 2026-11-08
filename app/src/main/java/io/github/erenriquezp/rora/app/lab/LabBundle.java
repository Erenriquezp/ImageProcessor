package io.github.erenriquezp.rora.app.lab;

import io.github.erenriquezp.rora.app.catalog.Catalog;
import io.github.erenriquezp.rora.app.imaging.SourceFormat;
import io.github.erenriquezp.rora.core.op.Operation;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Lo que aporta el módulo opcional Laboratorio. Se descubre con {@code ServiceLoader}: si el
 * módulo no está resuelto, el producto funciona igual sin él.
 */
public interface LabBundle {

  /** Operaciones adicionales; {@code nativeSource} da el original de los formatos con pérdida. */
  List<Operation> operations(SourceFormat.NativeSource nativeSource);

  /** Filtros que se añaden al catálogo con {@code --lab}. */
  List<Catalog.Entry> catalogEntries();

  /** Textos de esas operaciones y filtros. */
  ResourceBundle labels(Locale locale);

  /** Herramientas propias de la ventana Laboratorio. */
  List<LabTool> tools();
}
