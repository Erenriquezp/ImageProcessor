package io.github.erenriquezp.rora.core.op;

import io.github.erenriquezp.rora.core.image.Img;
import java.util.Optional;

/**
 * Resultados ya calculados fuera del pipeline (por el motor de IA), identificados por un número. Un
 * identificador designa siempre el mismo contenido, así que las operaciones que lo usan siguen
 * siendo puras: mismos parámetros, mismo resultado.
 */
public interface ArtifactLookup {

  /** Sin resultados: el contexto por defecto. */
  ArtifactLookup NONE =
      new ArtifactLookup() {
        @Override
        public Optional<Img> full(int id) {
          return Optional.empty();
        }

        @Override
        public Optional<Img> proxy(int id, int width, int height) {
          return Optional.empty();
        }
      };

  /** El resultado completo. */
  Optional<Img> full(int id);

  /** El resultado reducido a {@code width × height}. */
  Optional<Img> proxy(int id, int width, int height);
}
