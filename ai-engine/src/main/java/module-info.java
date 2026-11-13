/** Motor de IA local de Rora Studio. ONNX Runtime no sale de este módulo. */
module io.github.erenriquezp.rora.ai {
  requires io.github.erenriquezp.rora.core;
  requires transitive io.github.erenriquezp.rora.wallpapers;
  requires com.microsoft.onnxruntime;

  exports io.github.erenriquezp.rora.ai.api;
  exports io.github.erenriquezp.rora.ai.tile;
  exports io.github.erenriquezp.rora.ai.store;
}
