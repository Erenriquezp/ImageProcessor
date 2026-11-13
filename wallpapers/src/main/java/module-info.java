/**
 * Wallpapers de Rora Studio: presets de dispositivo, encuadre, remuestreo, relleno y exportación
 * multi-destino sobre el resultado de la receta. Lógica pura: solo {@code java.base} y el núcleo.
 */
module io.github.erenriquezp.rora.wallpapers {
  requires io.github.erenriquezp.rora.core;

  exports io.github.erenriquezp.rora.wallpapers.preset;
}
