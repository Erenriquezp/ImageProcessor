package io.github.erenriquezp.rora.core.image;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import java.util.Objects;

/**
 * Histograma para el panel en vivo: 256 niveles de R, G, B y luminancia (Rec. 709 sobre los valores
 * codificados), y porcentaje de píxeles recortados. Un píxel está recortado en luces si algún canal
 * vale 255 y en sombras si alguno vale 0. Los píxeles con alpha 0 no cuentan.
 *
 * @param highlightsPct porcentaje de píxeles contados con algún canal en 255
 * @param shadowsPct porcentaje de píxeles contados con algún canal en 0
 * @param counted píxeles con alpha mayor que 0
 */
public record LiveHistogram(
    int[] red,
    int[] green,
    int[] blue,
    int[] luma,
    double highlightsPct,
    double shadowsPct,
    long counted) {

  private static final int ROWS_PER_CANCEL_CHECK = 64;

  /** Calcula el histograma de {@code img}; comprueba {@code token} cada pocas filas. */
  public static LiveHistogram compute(Img img, CancellationToken token) {
    Objects.requireNonNull(img, "img");
    Objects.requireNonNull(token, "token");
    int[] r = new int[256];
    int[] g = new int[256];
    int[] b = new int[256];
    int[] y = new int[256];
    long counted = 0;
    long highlights = 0;
    long shadows = 0;
    int w = img.width();
    int[] row = new int[w];
    for (int j = 0; j < img.height(); j++) {
      if (j % ROWS_PER_CANCEL_CHECK == 0) {
        token.throwIfCancelled();
      }
      img.copyRow(j, row, 0);
      for (int i = 0; i < w; i++) {
        int p = row[i];
        if ((p >>> 24) == 0) {
          continue;
        }
        int red = (p >> 16) & 0xFF;
        int green = (p >> 8) & 0xFF;
        int blue = p & 0xFF;
        r[red]++;
        g[green]++;
        b[blue]++;
        y[(2126 * red + 7152 * green + 722 * blue + 5000) / 10000]++;
        counted++;
        if (red == 255 || green == 255 || blue == 255) {
          highlights++;
        }
        if (red == 0 || green == 0 || blue == 0) {
          shadows++;
        }
      }
    }
    double scale = counted == 0 ? 0 : 100.0 / counted;
    return new LiveHistogram(r, g, b, y, highlights * scale, shadows * scale, counted);
  }
}
