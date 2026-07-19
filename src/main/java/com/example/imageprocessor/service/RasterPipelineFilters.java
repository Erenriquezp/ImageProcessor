package com.example.imageprocessor.service;

import java.awt.image.BufferedImage;

import com.example.imageprocessor.domain.DepthSource;
import com.example.imageprocessor.domain.TextureFilterMode;

/**
 * Operaciones del pipeline de rasterización: profundidad (Z/W), bitmaps y
 * muestreo de texturas — siempre pixel a pixel.
 */
final class RasterPipelineFilters {

    private RasterPipelineFilters() {
    }

    // ── 1. Mapa de profundidad ───────────────────────────────────────────────

    /** Visualiza el buffer de profundidad como escala de grises (0=cerca, 255=lejos). */
    static BufferedImage depthMap(BufferedImage original, DepthSource source) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int a = (p >> 24) & 0xFF;
                int d = depthAt(original, x, y, w, h, source);
                out.setRGB(x, y, (a << 24) | (d << 16) | (d << 8) | d);
            }
        }
        return out;
    }

    // ── 1. Z-Buffer (oclusión) ───────────────────────────────────────────────

    /**
     * Simula un z-buffer: una capa frontal sintética (plano diagonal) solo se
     * escribe donde su Z es menor que la profundidad del píxel de fondo.
     *
     * @param threshold profundidad constante del plano frontal [0–255]
     * @param planeR    color del fragmento frontal
     */
    static BufferedImage zBuffer(BufferedImage original, DepthSource source,
            int threshold, int planeR, int planeG, int planeB) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int thr = PixelMath.clamp(threshold);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int bg = original.getRGB(x, y);
                int a = (bg >> 24) & 0xFF;
                int zBg = depthAt(original, x, y, w, h, source);
                // Profundidad del plano frontal: varía levemente con (x+y) para
                // mostrar interpolación Z a lo largo de un triángulo/plano.
                int zFg = PixelMath.clamp(thr + ((x + y) % 17) - 8);

                if (zFg < zBg) {
                    out.setRGB(x, y, (a << 24) | (planeR << 16) | (planeG << 8) | planeB);
                } else {
                    out.setRGB(x, y, bg);
                }
            }
        }
        return out;
    }

    // ── 1. Bitmap / pixelación ───────────────────────────────────────────────

    /** Rasteriza la imagen en bloques NxN (bitmap discreto). */
    static BufferedImage bitmapPixelate(BufferedImage original, int blockSize) {
        int w = original.getWidth();
        int h = original.getHeight();
        int n = Math.max(1, blockSize);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int by = 0; by < h; by += n) {
            for (int bx = 0; bx < w; bx += n) {
                long sumR = 0, sumG = 0, sumB = 0, sumA = 0;
                int count = 0;
                int xMax = Math.min(bx + n, w);
                int yMax = Math.min(by + n, h);
                for (int y = by; y < yMax; y++) {
                    for (int x = bx; x < xMax; x++) {
                        int p = original.getRGB(x, y);
                        sumA += (p >> 24) & 0xFF;
                        sumR += (p >> 16) & 0xFF;
                        sumG += (p >> 8) & 0xFF;
                        sumB += p & 0xFF;
                        count++;
                    }
                }
                int a = (int) (sumA / count);
                int r = (int) (sumR / count);
                int g = (int) (sumG / count);
                int b = (int) (sumB / count);
                int rgb = (a << 24) | (r << 16) | (g << 8) | b;
                for (int y = by; y < yMax; y++) {
                    for (int x = bx; x < xMax; x++) {
                        out.setRGB(x, y, rgb);
                    }
                }
            }
        }
        return out;
    }

    // ── 1. Raster grid (píxeles discretos) ───────────────────────────────────

    /** Remarca la naturaleza discreta del framebuffer con una rejilla. */
    static BufferedImage rasterGrid(BufferedImage original, int blockSize) {
        int w = original.getWidth();
        int h = original.getHeight();
        int n = Math.max(2, blockSize);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                if (x % n == 0 || y % n == 0) {
                    int a = (p >> 24) & 0xFF;
                    int r = PixelMath.clamp(((p >> 16) & 0xFF) / 3);
                    int g = PixelMath.clamp(((p >> 8) & 0xFF) / 3);
                    int b = PixelMath.clamp((p & 0xFF) / 3);
                    out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                } else {
                    out.setRGB(x, y, p);
                }
            }
        }
        return out;
    }

    // ── 2. Muestreo de textura ───────────────────────────────────────────────

    /**
     * Trata la imagen como textura y la remuestrea con UV escaladas
     * (nearest o bilinear), pixel a pixel.
     */
    static BufferedImage textureSample(BufferedImage original, float scale, TextureFilterMode mode) {
        int w = original.getWidth();
        int h = original.getHeight();
        float s = Math.max(0.05f, scale);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float u = (x / s) % w;
                float v = (y / s) % h;
                if (u < 0)
                    u += w;
                if (v < 0)
                    v += h;

                int rgb = mode == TextureFilterMode.BILINEAR
                        ? sampleBilinear(original, u, v, w, h)
                        : sampleNearest(original, u, v, w, h);
                out.setRGB(x, y, rgb);
            }
        }
        return out;
    }

    // ── 2. Interpolación en profundidad ──────────────────────────────────────

    /**
     * Interpola linealmente entre color cercano y lejano según la profundidad
     * del píxel (simula shading por profundidad / fog).
     */
    static BufferedImage depthInterpolate(BufferedImage original, DepthSource source,
            int nearR, int nearG, int nearB, int farR, int farG, int farB) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int a = (p >> 24) & 0xFF;
                int r0 = (p >> 16) & 0xFF;
                int g0 = (p >> 8) & 0xFF;
                int b0 = p & 0xFF;
                double t = depthAt(original, x, y, w, h, source) / 255.0;

                int fogR = PixelMath.lerp(nearR, farR, t);
                int fogG = PixelMath.lerp(nearG, farG, t);
                int fogB = PixelMath.lerp(nearB, farB, t);
                // Mezcla el color original con el fog interpolado
                int r = PixelMath.lerp(r0, fogR, t);
                int g = PixelMath.lerp(g0, fogG, t);
                int b = PixelMath.lerp(b0, fogB, t);
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    // ── 2. W-Buffer ──────────────────────────────────────────────────────────

    /**
     * Igual que el z-buffer, pero el test usa w = 1/(z+ε). Favorece fragmentos
     * cercanos de forma no lineal (w-buffering).
     */
    static BufferedImage wBuffer(BufferedImage original, DepthSource source,
            int threshold, int planeR, int planeG, int planeB) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int thr = PixelMath.clamp(threshold);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int bg = original.getRGB(x, y);
                int a = (bg >> 24) & 0xFF;
                int zBg = depthAt(original, x, y, w, h, source);
                float wBg = 1f / (zBg + 1f);
                // w = 1/(z+ε): gana el fragmento con MAYOR w (más cercano)
                int zFg = PixelMath.clamp(thr + ((x * 3 + y * 2) % 23) - 11);
                float wFg = 1f / (zFg + 1f);

                if (wFg > wBg) {
                    out.setRGB(x, y, (a << 24) | (planeR << 16) | (planeG << 8) | planeB);
                } else {
                    out.setRGB(x, y, bg);
                }
            }
        }
        return out;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    static int depthAt(BufferedImage img, int x, int y, int w, int h, DepthSource source) {
        return switch (source) {
            case LUMINANCE -> {
                int p = img.getRGB(x, y);
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                yield (r + g + b) / 3;
            }
            case RADIAL -> {
                double cx = (w - 1) / 2.0;
                double cy = (h - 1) / 2.0;
                double maxD = Math.hypot(cx, cy);
                double d = Math.hypot(x - cx, y - cy);
                yield PixelMath.clamp((int) Math.round(d / maxD * 255));
            }
            case HORIZONTAL -> PixelMath.clamp((int) Math.round(x / (double) Math.max(1, w - 1) * 255));
            case DIAGONAL -> {
                double t = (x + y) / (double) Math.max(1, w + h - 2);
                yield PixelMath.clamp((int) Math.round(t * 255));
            }
        };
    }

    private static int sampleNearest(BufferedImage img, float u, float v, int w, int h) {
        int x = Math.max(0, Math.min(w - 1, (int) Math.floor(u)));
        int y = Math.max(0, Math.min(h - 1, (int) Math.floor(v)));
        return img.getRGB(x, y);
    }

    private static int sampleBilinear(BufferedImage img, float u, float v, int w, int h) {
        int x0 = Math.max(0, Math.min(w - 1, (int) Math.floor(u)));
        int y0 = Math.max(0, Math.min(h - 1, (int) Math.floor(v)));
        int x1 = Math.max(0, Math.min(w - 1, x0 + 1));
        int y1 = Math.max(0, Math.min(h - 1, y0 + 1));
        float fx = u - x0;
        float fy = v - y0;

        int c00 = img.getRGB(x0, y0);
        int c10 = img.getRGB(x1, y0);
        int c01 = img.getRGB(x0, y1);
        int c11 = img.getRGB(x1, y1);

        int a = bilerp((c00 >> 24) & 0xFF, (c10 >> 24) & 0xFF, (c01 >> 24) & 0xFF, (c11 >> 24) & 0xFF, fx, fy);
        int r = bilerp((c00 >> 16) & 0xFF, (c10 >> 16) & 0xFF, (c01 >> 16) & 0xFF, (c11 >> 16) & 0xFF, fx, fy);
        int g = bilerp((c00 >> 8) & 0xFF, (c10 >> 8) & 0xFF, (c01 >> 8) & 0xFF, (c11 >> 8) & 0xFF, fx, fy);
        int b = bilerp(c00 & 0xFF, c10 & 0xFF, c01 & 0xFF, c11 & 0xFF, fx, fy);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int bilerp(int c00, int c10, int c01, int c11, float fx, float fy) {
        float top = c00 + (c10 - c00) * fx;
        float bot = c01 + (c11 - c01) * fx;
        return PixelMath.clamp(Math.round(top + (bot - top) * fy));
    }
}
