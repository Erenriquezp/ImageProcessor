package io.github.erenriquezp.rora.app.session;

import io.github.erenriquezp.rora.app.imaging.SourceFormat;
import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.Operation;
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
public final class SourceFormats {

  private static final String CONVOLUTION = "legacy.convolution";

  private final OpRegistry registry;
  private final String initial;
  private final Optional<BufferedImage> nativeTemplate;
  private final Map<String, String> afterConvolution = new HashMap<>();

  public SourceFormats(
      OpRegistry registry, String initial, Optional<BufferedImage> nativeTemplate) {
    this.registry = registry;
    this.initial = initial;
    this.nativeTemplate = nativeTemplate;
  }

  /**
   * @param steps pasos activos con los formatos rellenos
   * @param finalFormat formato del resultado (tipo del {@code BufferedImage} que tenía la app al
   *     final de la cadena: lo usa la exportación)
   */
  public record Effective(List<OpSpec> steps, String finalFormat) {}

  /** Pasos activos de {@code steps}, con los formatos rellenos. */
  public Effective apply(List<OpSpec> steps) {
    String fmt = initial;
    List<OpSpec> out = new ArrayList<>(steps.size());
    for (OpSpec s : steps) {
      if (!s.enabled()) {
        continue;
      }
      Operation op = registry.require(s.opId());
      if (s.opId().equals(CONVOLUTION)) {
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
    return new Effective(List.copyOf(out), fmt);
  }
}
