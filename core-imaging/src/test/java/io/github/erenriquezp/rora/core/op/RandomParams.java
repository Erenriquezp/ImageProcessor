package io.github.erenriquezp.rora.core.op;

import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.ParamDef.BoolParam;
import io.github.erenriquezp.rora.core.op.ParamDef.ColorParam;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamDef.EnumParam;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import io.github.erenriquezp.rora.core.op.ParamDef.SelectionParam;
import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;

/** Parámetros e imágenes aleatorios y deterministas (semilla) para los tests de la fase 2. */
public final class RandomParams {

  private RandomParams() {}

  /**
   * Valores dentro del rango de la UI (decimales en pasos del slider, como {@code float}) y, con
   * probabilidad baja, fuera de rango.
   */
  public static Params random(ParamSchema schema, SplittableRandom rnd) {
    Params.Builder b = Params.builder(schema);
    for (ParamDef d : schema.params()) {
      Object v =
          switch (d) {
            case IntParam p -> {
              int span = p.max() - p.min();
              if (rnd.nextInt(20) == 0) {
                yield p.min() - 1 - rnd.nextInt(3);
              }
              yield p.min() + rnd.nextInt(span + 1);
            }
            case DecimalParam p -> {
              long steps = Math.max(1, Math.round((p.max() - p.min()) / p.step()));
              double x = p.min() + rnd.nextLong(steps + 1) * p.step();
              if (rnd.nextInt(20) == 0) {
                x = p.max() + p.step() * (1 + rnd.nextInt(10));
              }
              yield (double) (float) x;
            }
            case BoolParam p -> rnd.nextBoolean();
            case EnumParam p -> p.values().get(rnd.nextInt(p.values().size()));
            case ColorParam p -> 0xFF000000 | rnd.nextInt(1 << 24);
            case SelectionParam p -> {
              Set<String> s = new HashSet<>();
              for (String o : p.options()) {
                if (rnd.nextBoolean()) {
                  s.add(o);
                }
              }
              yield s;
            }
          };
      b.set(d.key(), v);
    }
    return b.build();
  }

  /** Ruido ARGB con alpha parcial. */
  public static Img noise(int w, int h, long seed) {
    SplittableRandom rnd = new SplittableRandom(seed);
    int[] px = new int[w * h];
    for (int i = 0; i < px.length; i++) {
      px[i] = rnd.nextInt();
    }
    return Img.adopt(w, h, px);
  }

  /** 4096×4096 con los 2^24 colores RGB y alpha pseudoaleatorio. */
  public static Img allColors(long alphaSeed) {
    SplittableRandom rnd = new SplittableRandom(alphaSeed);
    int[] px = new int[1 << 24];
    for (int i = 0; i < px.length; i++) {
      px[i] = (rnd.nextInt(256) << 24) | i;
    }
    return Img.adopt(4096, 4096, px);
  }
}
