package com.example.imageprocessor.service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Generates an RGB histogram chart from a source image.
 * Pure stateless utility — all methods are static.
 */
final class HistogramService {

    private HistogramService() {
    }

    /**
     * Builds an 800×600 RGB histogram chart for {@code original}.
     * <p>
     * The three colour channels are counted independently and then drawn as
     * anti-aliased line curves (R=red, G=green, B=blue) on a black background.
     * The Y axis is normalised to the maximum bin across all three channels so
     * the tallest peak always reaches the top of the chart.
     *
     * @param original source image (never mutated)
     * @return new {@link BufferedImage} (800×600, TYPE_INT_RGB) containing the chart
     */
    static BufferedImage generateHistogram(BufferedImage original) {
        // ── 1. Count pixel frequencies per channel ────────────────────────────
        int[] histoR = new int[256];
        int[] histoG = new int[256];
        int[] histoB = new int[256];

        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                int p = original.getRGB(x, y);
                histoR[(p >> 16) & 0xFF]++;
                histoG[(p >>  8) & 0xFF]++;
                histoB[ p        & 0xFF]++;
            }
        }

        // ── 2. Prepare canvas ─────────────────────────────────────────────────
        int anchoH = 800;
        int altoH  = 600;
        BufferedImage imgHisto = new BufferedImage(anchoH, altoH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = imgHisto.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, anchoH, altoH);

        // ── 3. Normalise scale ────────────────────────────────────────────────
        int maxGlobal = Math.max(arrayMax(histoR), Math.max(arrayMax(histoG), arrayMax(histoB)));
        if (maxGlobal == 0) maxGlobal = 1;

        float escalaX = (float) anchoH / 256f;
        float escalaY = (float) altoH  / maxGlobal;
        g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // ── 4. Draw R, G, B curves ────────────────────────────────────────────
        for (int i = 1; i < 256; i++) {
            int x1 = (int)(escalaX * (i - 1));
            int x2 = (int)(escalaX * i);

            int rY1 = altoH - (int)(histoR[i - 1] * escalaY);
            int rY2 = altoH - (int)(histoR[i]     * escalaY);
            g2d.setColor(Color.RED);
            g2d.drawLine(x1, rY1, x2, rY2);

            int gY1 = altoH - (int)(histoG[i - 1] * escalaY);
            int gY2 = altoH - (int)(histoG[i]     * escalaY);
            g2d.setColor(Color.GREEN);
            g2d.drawLine(x1, gY1, x2, gY2);

            int bY1 = altoH - (int)(histoB[i - 1] * escalaY);
            int bY2 = altoH - (int)(histoB[i]     * escalaY);
            g2d.setColor(Color.BLUE);
            g2d.drawLine(x1, bY1, x2, bY2);
        }

        g2d.dispose();
        return imgHisto;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static int arrayMax(int[] array) {
        int max = 0;
        for (int v : array) if (v > max) max = v;
        return max;
    }
}

