/**
 * Wallpapers de Rora Studio: presets de dispositivo, encuadre, remuestreo, relleno y exportación
 * multi-destino sobre el resultado de la receta. Lógica pura: solo {@code java.base} y el núcleo.
 */
module io.github.erenriquezp.rora.wallpapers {
  requires io.github.erenriquezp.rora.core;

  exports io.github.erenriquezp.rora.wallpapers.frame;
  exports io.github.erenriquezp.rora.wallpapers.geom;
  exports io.github.erenriquezp.rora.wallpapers.plan;
  exports io.github.erenriquezp.rora.wallpapers.preset;
  exports io.github.erenriquezp.rora.wallpapers.render;
  exports io.github.erenriquezp.rora.wallpapers.subject;
  // Solo para leer la lista de releases en la comprobación de actualizaciones de la app.
  exports io.github.erenriquezp.rora.wallpapers.json to
      io.github.erenriquezp.rora.app;
}
