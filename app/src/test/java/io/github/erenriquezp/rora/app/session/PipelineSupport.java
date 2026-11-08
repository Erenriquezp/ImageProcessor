package io.github.erenriquezp.rora.app.session;

import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.RenderLevel;
import io.github.erenriquezp.rora.core.op.Renderer;
import io.github.erenriquezp.rora.core.op.ResultCache;
import io.github.erenriquezp.rora.domain.FilterType;
import io.github.erenriquezp.rora.service.ImageBridge;
import io.github.erenriquezp.rora.service.LegacySteps;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Ejecuta una receta de filtros de la UI por el pipeline nuevo y por la referencia. */
final class PipelineSupport {

  record Step(FilterType type, FilterParams params) {}

  /** Resultado del pipeline nuevo: receta efectiva + render FULL. */
  record Run(Img image, List<OpSpec> effective) {}

  private PipelineSupport() {}

  static Run pipeline(BufferedImage loaded, List<Step> steps, boolean fusion, OpContext ctx) {
    SessionSource src = SessionSource.of(loaded);
    OpRegistry reg = LegacySteps.registryWithLegacy(src.nativeSource());
    FilterSpecs specs = new FilterSpecs(reg);
    List<OpSpec> recipe = new ArrayList<>();
    for (Step s : steps) {
      specs.of(s.type(), s.params()).ifPresent(recipe::add);
    }
    List<OpSpec> effective =
        new SourceFormats(reg, src.format(), src.nativeImage()).apply(recipe).steps();
    Renderer r = new Renderer(reg, new ResultCache(256L << 20), fusion);
    Img out =
        r.render(
                new Renderer.Request(
                    src.asRenderSource(), effective, effective.size(), RenderLevel.FULL),
                ctx)
            .image();
    return new Run(out, effective);
  }

  static BufferedImage reference(BufferedImage loaded, List<Step> steps) {
    BufferedImage cur = loaded;
    for (Step s : steps) {
      cur = ReferenceFilters.process(s.type(), cur, s.params());
    }
    return cur;
  }

  static boolean same(BufferedImage expected, Img actual) {
    return SessionFixtures.sameRgb(expected, ImageBridge.toBufferedImage(actual));
  }
}
