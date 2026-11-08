package io.github.erenriquezp.rora.core.op;

import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.image.Downscale;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy.Size;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Evalúa una receta sobre la imagen completa ({@link RenderLevel#FULL}, exacto) o sobre un proxy
 * reducido ({@link RenderLevel.Preview}, aproximado). Síncrono y cancelable; seguro para hilos (el
 * estado compartido es la {@link ResultCache}, que lo es).
 *
 * <ol>
 *   <li>PREVIEW: obtiene (o calcula y cachea) el proxy y escala los parámetros {@link
 *       ParamUnit#PIXELS} por la proporción entre preview y completa <b>en ese paso</b>.
 *   <li>Busca en la caché el prefijo calculado más largo y solo calcula el resto.
 *   <li>Tramos de operaciones puntuales consecutivas se fusionan ({@link PointFusion}) si la fusión
 *       está activada; nunca a través del final de la cabeza confirmada.
 *   <li>Cachea solo puntos de control: tras cada paso no puntual, al final de la cabeza y el
 *       resultado final; siempre después de que el paso termine (un render cancelado no deja
 *       entradas parciales). Un paso con datos auxiliares (análisis) no se cachea.
 * </ol>
 */
public final class Renderer {

  /** Imagen fuente de una sesión con un id estable (parte de la clave de caché). */
  public record SourceImage(long id, Img full) {
    public SourceImage {
      Objects.requireNonNull(full, "full");
    }
  }

  /**
   * @param steps pasos de la receta efectiva (los inactivos se ignoran)
   * @param headLength número de pasos activos de la cabeza confirmada (el resto es el pendiente);
   *     su resultado se cachea
   */
  public record Request(SourceImage source, List<OpSpec> steps, int headLength, RenderLevel level) {
    public Request {
      Objects.requireNonNull(source, "source");
      steps = List.copyOf(steps);
      Objects.requireNonNull(level, "level");
    }
  }

  /**
   * @param aux datos auxiliares del último paso si se ha calculado en este render (no se cachean)
   * @param stepsComputed pasos calculados (0 si todo venía de la caché)
   */
  public record Result(Img image, Object aux, int stepsComputed, RenderKey key) {}

  /** Unidades de progreso por paso (el progreso total es {@code pasos · PROGRESS_SCALE}). */
  public static final long PROGRESS_SCALE = 1_000_000L;

  private final OpRegistry registry;
  private final ResultCache cache;
  private final boolean fusion;

  public Renderer(OpRegistry registry, ResultCache cache, boolean fusion) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.cache = Objects.requireNonNull(cache, "cache");
    this.fusion = fusion;
  }

  public ResultCache cache() {
    return cache;
  }

  public boolean fusion() {
    return fusion;
  }

  /** Pasos tal como se ejecutan (activos, esquema de la operación, PIXELS escalados en preview). */
  public List<OpSpec> executedSteps(Request req) {
    Size full = new Size(req.source().full().width(), req.source().full().height());
    Size preview =
        req.level() instanceof RenderLevel.Preview pv ? new Size(pv.width(), pv.height()) : full;
    List<OpSpec> out = new ArrayList<>();
    for (OpSpec s : req.steps()) {
      if (!s.enabled()) {
        continue;
      }
      Operation op = registry.require(s.opId());
      Params p = s.params().withSchema(op.schema());
      double scale = Math.min(1.0, (double) preview.width() / full.width());
      Params executed = scale < 1 ? op.forPreview(p, scale, full, preview) : p;
      out.add(new OpSpec(s.opId(), executed, true));
      full = op.outputSize(full, p);
      preview = op.outputSize(preview, executed);
    }
    return out;
  }

  /** Clave del resultado final de {@code req}. */
  public RenderKey keyOf(Request req) {
    RenderKey k = RenderKey.root(req.source().id(), req.level());
    for (OpSpec s : executedSteps(req)) {
      k = k.then(s);
    }
    return k;
  }

  public Result render(Request req, OpContext ctx) {
    Objects.requireNonNull(ctx, "ctx");
    List<OpSpec> exec = executedSteps(req);
    int n = exec.size();
    int head = Math.max(0, Math.min(req.headLength(), n));
    RenderKey[] keys = new RenderKey[n + 1];
    keys[0] = RenderKey.root(req.source().id(), req.level());
    for (int i = 0; i < n; i++) {
      keys[i + 1] = keys[i].then(exec.get(i));
    }
    ctx.token().throwIfCancelled();

    int start = 0;
    Img cur = null;
    for (int i = n; i >= 1; i--) {
      Optional<Img> hit = cache.get(keys[i]);
      if (hit.isPresent()) {
        start = i;
        cur = hit.get();
        break;
      }
    }
    if (cur == null) {
      cur = rootImage(req, keys[0], ctx);
    }
    if (start == n) {
      return new Result(cur, null, 0, keys[n]);
    }

    Operation[] ops = new Operation[n];
    for (int i = 0; i < n; i++) {
      ops[i] = registry.require(exec.get(i).opId());
    }
    Object aux = null;
    int i = start;
    while (i < n) {
      ctx.token().throwIfCancelled();
      int end = i + 1;
      if (fusion && isPoint(ops[i], exec.get(i))) {
        int limit = i < head ? head : n;
        while (end < limit && isPoint(ops[end], exec.get(end))) {
          end++;
        }
      }
      OpContext stepCtx = progressFor(ctx, i, n);
      if (end - i >= 2) {
        List<PointFusion.Stage> stages = new ArrayList<>();
        for (int j = i; j < end; j++) {
          stages.add(new PointFusion.Stage((PointOperation) ops[j], exec.get(j).params()));
        }
        cur = PointFusion.apply(cur, stages, stepCtx);
        aux = null;
      } else {
        OpResult r = ops[i].apply(cur, exec.get(i).params(), stepCtx);
        cur = r.image();
        aux = r.aux();
      }
      ctx.token().throwIfCancelled();
      boolean checkpoint = end == n || end == head || !isPoint(ops[end - 1], exec.get(end - 1));
      // Un paso con datos auxiliares (análisis) no se cachea: un acierto perdería el aux, y
      // recalcularlo sobre su entrada (que sí está en caché) es barato.
      if (checkpoint && aux == null) {
        cache.put(keys[end], cur);
      }
      i = end;
    }
    ctx.progress().update(n * PROGRESS_SCALE, n * PROGRESS_SCALE);
    return new Result(cur, aux, n - start, keys[n]);
  }

  private Img rootImage(Request req, RenderKey rootKey, OpContext ctx) {
    Img full = req.source().full();
    if (!(req.level() instanceof RenderLevel.Preview pv)) {
      return full;
    }
    if (pv.width() == full.width() && pv.height() == full.height()) {
      return full;
    }
    Optional<Img> hit = cache.get(rootKey);
    if (hit.isPresent()) {
      return hit.get();
    }
    Img proxy =
        Downscale.areaAverage(
            full, pv.width(), pv.height(), ctx.executor(), ctx.token(), Progress.NONE);
    cache.put(rootKey, proxy);
    return proxy;
  }

  private static boolean isPoint(Operation op, OpSpec s) {
    return op instanceof PointOperation && op.kind(s.params()) instanceof OpKind.Point;
  }

  /**
   * Escala los parámetros en píxeles: enteros {@code max(min, round(v·s))}, decimales {@code
   * max(min, v·s)}.
   */
  static Params scalePixels(Params p, double s) {
    Params.Builder b = null;
    for (ParamDef d : p.schema().params()) {
      if (d.unit() != ParamUnit.PIXELS) {
        continue;
      }
      if (b == null) {
        b = p.toBuilder();
      }
      switch (d) {
        case IntParam ip ->
            b.set(ip.key(), Math.max(ip.min(), (int) Math.round(p.getInt(ip.key()) * s)));
        case DecimalParam dp -> b.set(dp.key(), Math.max(dp.min(), p.getDouble(dp.key()) * s));
        default -> {}
      }
    }
    return b == null ? p : b.build();
  }

  private static OpContext progressFor(OpContext ctx, int step, int steps) {
    if (ctx.progress() == Progress.NONE) {
      return ctx;
    }
    Progress outer = ctx.progress();
    long scale = PROGRESS_SCALE;
    Progress p =
        (done, total) ->
            outer.update(step * scale + (total > 0 ? done * scale / total : 0), steps * scale);
    return new OpContext(ctx.executor(), ctx.token(), p);
  }
}
