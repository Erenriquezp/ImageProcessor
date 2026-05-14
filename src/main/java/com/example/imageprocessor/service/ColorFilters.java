package com.example.imageprocessor.service;

import java.awt.Color;
import java.awt.image.BufferedImage;

final class ColorFilters {

    private ColorFilters() {
    }

    static BufferedImage grayscale(BufferedImage original) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int a = (p >> 24) & 0xFF;
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                int gray = (r + g + b) / 3;
                out.setRGB(x, y, (a << 24) | (gray << 16) | (gray << 8) | gray);
            }
        }
        return out;
    }

    static BufferedImage negative(BufferedImage original) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int a = (p >> 24) & 0xFF;
                int r = 255 - ((p >> 16) & 0xFF);
                int g = 255 - ((p >> 8) & 0xFF);
                int b = 255 - (p & 0xFF);
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    static BufferedImage brightness(BufferedImage original, int value) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int a = (p >> 24) & 0xFF;
                int r = PixelMath.clamp(((p >> 16) & 0xFF) + value);
                int g = PixelMath.clamp(((p >> 8) & 0xFF) + value);
                int b = PixelMath.clamp((p & 0xFF) + value);
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    static BufferedImage hsv(BufferedImage original, float factorS, float factorV) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int a = (p >> 24) & 0xFF;
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;

                float[] hsv = Color.RGBtoHSB(r, g, b, null);
                float nh = hsv[0];
                float ns = PixelMath.clamp01(hsv[1] * factorS);
                float nv = PixelMath.clamp01(hsv[2] * factorV);

                int rgbNew = Color.HSBtoRGB(nh, ns, nv);
                out.setRGB(x, y, (a << 24) | (rgbNew & 0x00FFFFFF));
            }
        }
        return out;
    }

    static BufferedImage grayscaleQuantized(BufferedImage original, int levels) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int a = (p >> 24) & 0xFF;
                int r = (p >> 16) & 0xFF;
                int g = (p >>  8) & 0xFF;
                int b =  p        & 0xFF;
                int gray = PixelMath.quantize((r + g + b) / 3, levels);
                out.setRGB(x, y, (a << 24) | (gray << 16) | (gray << 8) | gray);
            }
        }
        return out;
    }

    static BufferedImage alphaGlobal(BufferedImage original, int alpha) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int a = PixelMath.clamp(alpha);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int r = (p >> 16) & 0xFF;
                int g = (p >>  8) & 0xFF;
                int b =  p        & 0xFF;
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    static BufferedImage bwThreshold(BufferedImage original, int threshold) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int a = (p >> 24) & 0xFF;
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                int gray = (r + g + b) / 3;
                int c = gray > threshold ? 255 : 0;
                out.setRGB(x, y, (a << 24) | (c << 16) | (c << 8) | c);
            }
        }
        return out;
    }

    // ── Color Matrix filters ──────────────────────────────────────────────────

    /**
     * Applies a 3×3 color matrix to every pixel, preserving the alpha channel.
     * Each output channel is computed as the dot product of the matrix row with [R, G, B].
     *
     * @param original source image (never mutated)
     * @param m        3×3 matrix: m[row][col] → row 0=R, 1=G, 2=B; col 0=R, 1=G, 2=B
     * @return new {@link BufferedImage} with the matrix applied
     */
    private static BufferedImage colorMatrix(BufferedImage original, float[][] m) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);

                int a = (p >> 24) & 0xFF;
                int r = (p >> 16) & 0xFF;
                int g = (p >>  8) & 0xFF;
                int b =  p        & 0xFF;

                int nr = PixelMath.clamp((int)(m[0][0] * r + m[0][1] * g + m[0][2] * b));
                int ng = PixelMath.clamp((int)(m[1][0] * r + m[1][1] * g + m[1][2] * b));
                int nb = PixelMath.clamp((int)(m[2][0] * r + m[2][1] * g + m[2][2] * b));

                out.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        return out;
    }

    /**
     * Sepia tone — classic warm brown look (W3C standard matrix).
     * Each output channel mixes all three RGB inputs with fixed weights.
     */
    static BufferedImage sepia(BufferedImage original) {
        float[][] matrix = {
            { 0.393f,  0.769f,  0.189f },
            { 0.349f,  0.686f,  0.168f },
            { 0.272f,  0.534f,  0.131f }
        };
        return colorMatrix(original, matrix);
    }

    /**
     * Cool Tone — shifts the palette towards cold blue/cyan tones by
     * attenuating red and amplifying blue slightly.
     */
    static BufferedImage coolTone(BufferedImage original) {
        float[][] matrix = {
            { 0.80f,  0.10f,  0.10f },
            { 0.05f,  0.92f,  0.03f },
            { 0.00f,  0.15f,  1.10f }
        };
        return colorMatrix(original, matrix);
    }

    /**
     * Warm Tone — shifts the palette towards warm yellow/amber tones by
     * amplifying red, boosting green slightly and reducing blue.
     */
    static BufferedImage warmTone(BufferedImage original) {
        float[][] matrix = {
            { 1.10f,  0.10f,  0.00f },
            { 0.00f,  1.00f,  0.05f },
            { 0.00f,  0.00f,  0.80f }
        };
        return colorMatrix(original, matrix);
    }

    /**
     * Polaroid — punchy, slightly cross-processed look inspired by
     * instant-film photography (lifted blacks, shifted color balance).
     */
    static BufferedImage polaroid(BufferedImage original) {
        float[][] matrix = {
            {  1.438f, -0.062f, -0.062f },
            { -0.122f,  1.378f, -0.122f },
            { -0.016f,  0.016f,  0.984f }
        };
        return colorMatrix(original, matrix);
    }

    /**
     * Kodachrome — emulates the rich, saturated look of Kodak Kodachrome
     * film: warm highlights, deep shadows, and boosted blue-greens.
     */
    static BufferedImage kodachrome(BufferedImage original) {
        float[][] matrix = {
            {  1.128f, -0.397f, -0.040f },
            { -0.164f,  1.084f, -0.055f },
            { -0.168f, -0.560f,  1.601f }
        };
        return colorMatrix(original, matrix);
    }
}

