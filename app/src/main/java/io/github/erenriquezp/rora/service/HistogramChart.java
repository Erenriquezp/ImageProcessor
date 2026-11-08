package io.github.erenriquezp.rora.service;

import io.github.erenriquezp.rora.core.op.ChannelHistogram;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Dibuja el gráfico de histograma RGB de 800×600 a partir de los recuentos del núcleo. Es una
 * <b>copia literal</b> del dibujo de {@code HistogramService.generateHistogram} (que se conserva
 * intacto para el test diferencial): el resultado es idéntico porque las mismas llamadas Java2D
 * reciben los mismos valores.
 */
final class HistogramChart {

  private HistogramChart() {}

  static BufferedImage draw(ChannelHistogram h) {
    int[] histoR = h.red();
    int[] histoG = h.green();
    int[] histoB = h.blue();

    int anchoH = 800;
    int altoH = 600;
    BufferedImage imgHisto = new BufferedImage(anchoH, altoH, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2d = imgHisto.createGraphics();
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    g2d.setColor(Color.BLACK);
    g2d.fillRect(0, 0, anchoH, altoH);

    int maxGlobal = Math.max(arrayMax(histoR), Math.max(arrayMax(histoG), arrayMax(histoB)));
    if (maxGlobal == 0) {
      maxGlobal = 1;
    }

    float escalaX = (float) anchoH / 256f;
    float escalaY = (float) altoH / maxGlobal;
    g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

    for (int i = 1; i < 256; i++) {
      int x1 = (int) (escalaX * (i - 1));
      int x2 = (int) (escalaX * i);

      int rY1 = altoH - (int) (histoR[i - 1] * escalaY);
      int rY2 = altoH - (int) (histoR[i] * escalaY);
      g2d.setColor(Color.RED);
      g2d.drawLine(x1, rY1, x2, rY2);

      int gY1 = altoH - (int) (histoG[i - 1] * escalaY);
      int gY2 = altoH - (int) (histoG[i] * escalaY);
      g2d.setColor(Color.GREEN);
      g2d.drawLine(x1, gY1, x2, gY2);

      int bY1 = altoH - (int) (histoB[i - 1] * escalaY);
      int bY2 = altoH - (int) (histoB[i] * escalaY);
      g2d.setColor(Color.BLUE);
      g2d.drawLine(x1, bY1, x2, bY2);
    }

    g2d.dispose();
    return imgHisto;
  }

  private static int arrayMax(int[] array) {
    int max = 0;
    for (int v : array) {
      if (v > max) {
        max = v;
      }
    }
    return max;
  }
}
