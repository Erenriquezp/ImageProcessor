package io.github.erenriquezp.rora.app.session;

import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.service.LegacySteps;
import io.github.erenriquezp.rora.service.SourceFormat;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rellena el parámetro oculto {@code sourceFormat} de cada convolución legacy de la receta
 * efectiva, reproduciendo el tipo de {@code BufferedImage} que esa convolución recibía en la cadena
 * de la app: el del archivo cargado mientras los pasos anteriores sean convoluciones o devuelvan su
 * entrada ({@link Operation#returnsInput}), {@code argb} tras cualquier otro paso.
 */
final class SourceFormats {

  private final OpRegistry registry;
  private final String initial;
  private final Optional<BufferedImage> nativeTemplate;
  private final Map<String, String> afterConvolution = new HashMap<>();

  SourceFormats(OpRegistry registry, String initial, Optional<BufferedImage> nativeTemplate) {
    this.registry = registry;
    this.initial = initial;
    this.nativeTemplate = nativeTemplate;
  }

  /** Pasos activos de {@code steps}, con los formatos rellenos. */
  List<OpSpec> apply(List<OpSpec> steps) {
    String fmt = initial;
    List<OpSpec> out = new ArrayList<>(steps.size());
    for (OpSpec s : steps) {
      if (!s.enabled()) {
        continue;
      }
      Operation op = registry.require(s.opId());
      if (s.opId().equals(LegacySteps.CONVOLUTION)) {
        s = s.withParams(s.params().toBuilder().set("sourceFormat", fmt).build());
        String in = fmt;
        fmt =
            afterConvolution.computeIfAbsent(
                in, f -> SourceFormat.afterConvolution(f, nativeTemplate));
      } else if (!op.returnsInput(s.params().withSchema(op.schema()))) {
        fmt = SourceFormat.ARGB;
      }
      out.add(s);
    }
    return out;
  }
}
