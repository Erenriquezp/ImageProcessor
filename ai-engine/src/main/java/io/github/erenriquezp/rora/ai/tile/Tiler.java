package io.github.erenriquezp.rora.ai.tile;

import io.github.erenriquezp.rora.ai.api.TileRunner;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure.Reason;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.Operation;
import java.util.ArrayList;
import java.util.List;

/**
 * Amplía una imagen por tiles de tamaño fijo. Cada tile aporta una zona rectangular que acaba en el
 * centro del solape, así que las zonas son disjuntas y no hay mezcla. El modelo ve siempre tiles
 * completos: una imagen menor que un tile se refleja hasta ese tamaño y luego se recorta.
 *
 * <p>Para una escala menor que la del modelo, cada tile se reduce por área antes de escribirse: el
 * lienzo a escala del modelo no llega a existir.
 */
public final class Tiler {

  private static final int MAX_DILATION = 32;

  private final int tile;
  private final int overlap;
  private final int modelScale;

  /**
   * @param tile lado del tile, en píxeles de entrada
   * @param overlap solape entre tiles consecutivos
   * @param modelScale ampliación del modelo
   */
  public Tiler(int tile, int overlap, int modelScale) {
    if (tile < 1 || overlap < 0 || overlap >= tile || modelScale < 1) {
      throw new IllegalArgumentException(tile + "/" + overlap + "/" + modelScale);
    }
    this.tile = tile;
    this.overlap = overlap;
    this.modelScale = modelScale;
  }

  /** Número de tiles que necesita una imagen, sea cual sea la escala de salida. */
  public int tileCount(int width, int height) {
    return positions(width).length * positions(height).length;
  }

  /** Inicio de cada tile en un eje; el último queda alineado con el borde. */
  int[] positions(int size) {
    if (size <= tile) {
      return new int[] {0};
    }
    int stride = tile - overlap;
    List<Integer> out = new ArrayList<>();
    for (int p = 0; p + tile < size; p += stride) {
      out.add(p);
    }
    out.add(size - tile);
    return out.stream().mapToInt(Integer::intValue).toArray();
  }

  /** Primer píxel de entrada que aporta el tile {@code i}. */
  private int coreStart(int[] pos, int i) {
    return i == 0 ? 0 : (pos[i - 1] + tile + pos[i]) / 2;
  }

  /** Fin (excluido) de lo que aporta el tile {@code i}. */
  private int coreEnd(int[] pos, int i, int size) {
    return i == pos.length - 1 ? size : (pos[i] + tile + pos[i + 1]) / 2;
  }

  /**
   * Amplía {@code src}.
   *
   * @param outScale escala de salida: la del modelo o la mitad
   * @param progress un paso por tile
   * @throws io.github.erenriquezp.rora.core.exec.CancelledException si se cancela el token
   */
  public Img enhance(
      Img src, int outScale, TileRunner runner, CancellationToken token, Progress progress)
      throws AiFailure {
    if (outScale != modelScale && outScale * 2 != modelScale) {
      throw new IllegalArgumentException("Escala " + outScale + " con modelo x" + modelScale);
    }
    int w = src.width();
    int h = src.height();
    int outW = Math.multiplyExact(w, outScale);
    int outH = Math.multiplyExact(h, outScale);
    int[] out = new int[Math.multiplyExact(outW, outH)];
    int[] xs = positions(w);
    int[] ys = positions(h);
    int total = xs.length * ys.length;

    boolean translucent = hasAlpha(src);
    boolean gray = isGray(src);
    Img colors = translucent ? dilate(src) : src;
    int tw = Math.min(tile, w);
    int th = Math.min(tile, h);
    float[] in = new float[3 * tile * tile];
    int expected = 3 * tile * modelScale * tile * modelScale;
    int done = 0;
    for (int yi = 0; yi < ys.length; yi++) {
      int y0 = coreStart(ys, yi);
      int y1 = coreEnd(ys, yi, h);
      for (int xi = 0; xi < xs.length; xi++) {
        token.throwIfCancelled();
        fill(colors, xs[xi], ys[yi], tw, th, in);
        float[] result = runner.run(in, tile, tile, token);
        if (result.length != expected) {
          throw new AiFailure(Reason.SHAPE_MISMATCH, "Salida de " + result.length, null);
        }
        int x0 = coreStart(xs, xi);
        int x1 = coreEnd(xs, xi, w);
        write(result, xs[xi], ys[yi], x0, x1, y0, y1, outScale, gray, out, outW);
        progress.update(++done, total);
      }
    }
    if (translucent) {
      mergeAlpha(src, outScale, out, token);
    } else {
      for (int i = 0; i < out.length; i++) {
        out[i] |= 0xFF000000;
      }
    }
    return Img.adopt(outW, outH, out);
  }

  /** Copia el tile en NCHW, reflejando si la imagen es menor que un tile. */
  private void fill(Img src, int px, int py, int tw, int th, float[] in) {
    int plane = tile * tile;
    for (int y = 0, i = 0; y < tile; y++) {
      int sy = py + reflect(y, th);
      for (int x = 0; x < tile; x++, i++) {
        int p = src.argb(px + reflect(x, tw), sy);
        in[i] = ((p >> 16) & 0xFF) / 255f;
        in[i + plane] = ((p >> 8) & 0xFF) / 255f;
        in[i + 2 * plane] = (p & 0xFF) / 255f;
      }
    }
  }

  static int reflect(int i, int n) {
    int m = i % (2 * n);
    return m < n ? m : 2 * n - 1 - m;
  }

  private void write(
      float[] r,
      int px,
      int py,
      int x0,
      int x1,
      int y0,
      int y1,
      int outScale,
      boolean gray,
      int[] out,
      int outW)
      throws AiFailure {
    int side = tile * modelScale;
    int plane = side * side;
    int factor = modelScale / outScale;
    for (int oy = y0 * outScale; oy < y1 * outScale; oy++) {
      int ty = (oy - py * outScale) * factor;
      for (int ox = x0 * outScale; ox < x1 * outScale; ox++) {
        int tx = (ox - px * outScale) * factor;
        int at = ty * side + tx;
        float cr = sample(r, at, side, factor);
        float cg = sample(r, at + plane, side, factor);
        float cb = sample(r, at + 2 * plane, side, factor);
        int ir = to8(cr);
        int ig = to8(cg);
        int ib = to8(cb);
        if (gray) {
          int l = Math.round(0.2126f * ir + 0.7152f * ig + 0.0722f * ib);
          ir = l;
          ig = l;
          ib = l;
        }
        out[oy * outW + ox] = (ir << 16) | (ig << 8) | ib;
      }
    }
  }

  private static float sample(float[] r, int at, int side, int factor) {
    if (factor == 1) {
      return r[at];
    }
    return (r[at] + r[at + 1] + r[at + side] + r[at + side + 1]) / 4f;
  }

  /** De [0,1] a 8 bits; un valor no finito indica un fallo del modelo. */
  static int to8(float v) throws AiFailure {
    if (!Float.isFinite(v)) {
      throw new AiFailure(Reason.FAILURE, "El modelo produjo un valor no finito", null);
    }
    return (int) (Math.max(0f, Math.min(1f, v)) * 255f + 0.5f);
  }

  private static boolean hasAlpha(Img src) {
    for (int y = 0; y < src.height(); y++) {
      for (int x = 0; x < src.width(); x++) {
        if (src.argb(x, y) >>> 24 != 255) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isGray(Img src) {
    for (int y = 0; y < src.height(); y++) {
      for (int x = 0; x < src.width(); x++) {
        int p = src.argb(x, y);
        int r = (p >> 16) & 0xFF;
        if (r != ((p >> 8) & 0xFF) || r != (p & 0xFF)) {
          return false;
        }
      }
    }
    return true;
  }

  /** Rellena el color de los píxeles sin alfa con la media de sus vecinos, para evitar halos. */
  private static Img dilate(Img src) {
    int w = src.width();
    int h = src.height();
    int[] cur = src.toArray();
    boolean[] known = new boolean[cur.length];
    for (int i = 0; i < cur.length; i++) {
      known[i] = cur[i] >>> 24 != 0;
    }
    for (int pass = 0; pass < MAX_DILATION; pass++) {
      int[] next = cur.clone();
      boolean[] nextKnown = known.clone();
      boolean changed = false;
      for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
          int i = y * w + x;
          if (known[i]) {
            continue;
          }
          int r = 0;
          int g = 0;
          int b = 0;
          int n = 0;
          for (int dy = Math.max(0, y - 1); dy <= Math.min(h - 1, y + 1); dy++) {
            for (int dx = Math.max(0, x - 1); dx <= Math.min(w - 1, x + 1); dx++) {
              int j = dy * w + dx;
              if (known[j]) {
                r += (cur[j] >> 16) & 0xFF;
                g += (cur[j] >> 8) & 0xFF;
                b += cur[j] & 0xFF;
                n++;
              }
            }
          }
          if (n > 0) {
            next[i] = (cur[i] & 0xFF000000) | (r / n) << 16 | (g / n) << 8 | (b / n);
            nextKnown[i] = true;
            changed = true;
          }
        }
      }
      cur = next;
      known = nextKnown;
      if (!changed) {
        break;
      }
    }
    return Img.adopt(w, h, cur);
  }

  private static void mergeAlpha(Img src, int outScale, int[] out, CancellationToken token) {
    Operation resize = OpRegistry.load().require("transform.resize");
    int longSide = Math.max(src.width(), src.height()) * outScale;
    Img alpha =
        resize
            .apply(
                src,
                io.github.erenriquezp.rora.core.op.Params.builder(resize.schema())
                    .set("longSide", longSide)
                    .build(),
                new OpContext(
                    TileExecutor.sequential(),
                    token,
                    io.github.erenriquezp.rora.core.exec.Progress.NONE))
            .image();
    int[] a = alpha.toArray();
    for (int i = 0; i < out.length; i++) {
      out[i] = (a[i] & 0xFF000000) | (out[i] & 0xFFFFFF);
    }
  }
}
