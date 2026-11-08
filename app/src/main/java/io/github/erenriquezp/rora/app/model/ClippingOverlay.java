package io.github.erenriquezp.rora.app.model;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.image.Img;

/**
 * Máscara de zonas recortadas, para superponer en el visor. Lleva color y trama: luces en rojo con
 * diagonales y sombras en azul con puntos, para no depender solo de distinguir colores.
 */
public final class ClippingOverlay {

  private static final int HIGHLIGHT = 0xE6FF5050;
  private static final int HIGHLIGHT_BACK = 0x80FF5050;
  private static final int SHADOW = 0xE650A0FF;
  private static final int SHADOW_BACK = 0x8050A0FF;
  private static final int PATTERN = 6;

  private ClippingOverlay() {}

  public static Img render(Img src, CancellationToken token) {
    int w = src.width();
    int h = src.height();
    int[] row = new int[w];
    int[] out = new int[w * h];
    for (int y = 0; y < h; y++) {
      if (y % 64 == 0) {
        token.throwIfCancelled();
      }
      src.copyRow(y, row, 0);
      for (int x = 0, i = y * w; x < w; x++, i++) {
        int p = row[x];
        if ((p >>> 24) == 0) {
          continue;
        }
        int r = (p >> 16) & 0xFF;
        int g = (p >> 8) & 0xFF;
        int b = p & 0xFF;
        if (r == 255 || g == 255 || b == 255) {
          out[i] = (x + y) % PATTERN < 2 ? HIGHLIGHT : HIGHLIGHT_BACK;
        } else if (r == 0 && g == 0 && b == 0) {
          out[i] = x % PATTERN < 2 && y % PATTERN < 2 ? SHADOW : SHADOW_BACK;
        }
      }
    }
    return Img.adopt(w, h, out);
  }
}
