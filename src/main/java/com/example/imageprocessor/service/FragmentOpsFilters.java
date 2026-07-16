package com.example.imageprocessor.service;

import java.awt.image.BufferedImage;

import com.example.imageprocessor.domain.FragmentBlendMode;
import com.example.imageprocessor.domain.LogicOpType;
import com.example.imageprocessor.domain.StencilPattern;

/**
 * Operaciones sobre fragmentos: multisample, alpha test, stencil, blending y
 * logic ops — siempre pixel a pixel.
 */
final class FragmentOpsFilters {

    private FragmentOpsFilters() {
    }

    // ── 3. Multisample (MSAA simulado) ───────────────────────────────────────

    /**
     * Promedia un vecindario NxN por píxel (supersampling / multisample),
     * suavizando bordes como un MSAA simplificado.
     */
    static BufferedImage multisample(BufferedImage original, int samples) {
        int w = original.getWidth();
        int h = original.getHeight();
        int n = Math.max(2, Math.min(8, samples));
        int half = n / 2;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                long sumA = 0, sumR = 0, sumG = 0, sumB = 0;
                int count = 0;
                for (int dy = -half; dy <= half; dy++) {
                    for (int dx = -half; dx <= half; dx++) {
                        int sx = Math.max(0, Math.min(w - 1, x + dx));
                        int sy = Math.max(0, Math.min(h - 1, y + dy));
                        int p = original.getRGB(sx, sy);
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
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    // ── 3. Alpha Test ────────────────────────────────────────────────────────

    /**
     * Descarta fragmentos cuyo alpha (o luminancia si {@code useLuminance}) es
     * menor que el umbral — equivalente a {@code glAlphaFunc(GL_GEQUAL, ref)}.
     */
    static BufferedImage alphaTest(BufferedImage original, int threshold, boolean useLuminance) {
        int w = original.getWidth();
        int h = original.getHeight();
        int thr = PixelMath.clamp(threshold);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int a = (p >> 24) & 0xFF;
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                int test = useLuminance ? (r + g + b) / 3 : a;

                if (test >= thr) {
                    out.setRGB(x, y, p);
                } else {
                    // Fragmento descartado → transparente
                    out.setRGB(x, y, 0x00000000);
                }
            }
        }
        return out;
    }

    // ── 4. Stencil Test ──────────────────────────────────────────────────────

    /**
     * Solo conserva fragmentos donde el stencil pasa (máscara 1). Los que fallan
     * se descartan (alpha=0).
     */
    static BufferedImage stencilTest(BufferedImage original, StencilPattern pattern,
            int threshold, int cellSize) {
        int w = original.getWidth();
        int h = original.getHeight();
        int cell = Math.max(2, cellSize);
        int thr = PixelMath.clamp(threshold);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        double cx = (w - 1) / 2.0;
        double cy = (h - 1) / 2.0;
        double maxR = Math.min(cx, cy);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                boolean pass = stencilPass(original, x, y, w, h, pattern, thr, cell, cx, cy, maxR);
                if (pass) {
                    out.setRGB(x, y, p);
                } else {
                    out.setRGB(x, y, 0x00000000);
                }
            }
        }
        return out;
    }

    // ── 4. Fragment Blending ─────────────────────────────────────────────────

    /**
     * Mezcla cada fragmento de la imagen (src) con un color destino (dst)
     * según la ecuación de blending elegida.
     */
    static BufferedImage fragmentBlend(BufferedImage original, FragmentBlendMode mode,
            int dstR, int dstG, int dstB, float alpha) {
        int w = original.getWidth();
        int h = original.getHeight();
        float a = PixelMath.clamp01(alpha);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int srcA = (p >> 24) & 0xFF;
                int srcR = (p >> 16) & 0xFF;
                int srcG = (p >> 8) & 0xFF;
                int srcB = p & 0xFF;

                int r;
                int g;
                int b;
                switch (mode) {
                    case MULTIPLY -> {
                        r = (srcR * dstR) / 255;
                        g = (srcG * dstG) / 255;
                        b = (srcB * dstB) / 255;
                    }
                    case ADD -> {
                        r = PixelMath.clamp(srcR + dstR);
                        g = PixelMath.clamp(srcG + dstG);
                        b = PixelMath.clamp(srcB + dstB);
                    }
                    case SCREEN -> {
                        r = 255 - ((255 - srcR) * (255 - dstR)) / 255;
                        g = 255 - ((255 - srcG) * (255 - dstG)) / 255;
                        b = 255 - ((255 - srcB) * (255 - dstB)) / 255;
                    }
                    case SUBTRACT -> {
                        r = PixelMath.clamp(srcR - dstR);
                        g = PixelMath.clamp(srcG - dstG);
                        b = PixelMath.clamp(srcB - dstB);
                    }
                    case SRC_OVER -> {
                        r = PixelMath.lerp(dstR, srcR, a);
                        g = PixelMath.lerp(dstG, srcG, a);
                        b = PixelMath.lerp(dstB, srcB, a);
                    }
                    default -> {
                        r = srcR;
                        g = srcG;
                        b = srcB;
                    }
                }
                out.setRGB(x, y, (srcA << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    // ── 4. Logic Op ──────────────────────────────────────────────────────────

    /**
     * Aplica una operación lógica bit a bit entre cada canal RGB del fragmento
     * y una máscara constante (simula {@code glLogicOp}).
     */
    static BufferedImage logicOp(BufferedImage original, LogicOpType op, int mask) {
        int w = original.getWidth();
        int h = original.getHeight();
        int m = PixelMath.clamp(mask);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int a = (p >> 24) & 0xFF;
                int r = applyOp((p >> 16) & 0xFF, m, op);
                int g = applyOp((p >> 8) & 0xFF, m, op);
                int b = applyOp(p & 0xFF, m, op);
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static boolean stencilPass(BufferedImage img, int x, int y, int w, int h,
            StencilPattern pattern, int thr, int cell, double cx, double cy, double maxR) {
        return switch (pattern) {
            case CHECKER -> ((x / cell) + (y / cell)) % 2 == 0;
            case CIRCLE -> Math.hypot(x - cx, y - cy) <= maxR * (thr / 255.0);
            case LUMINANCE -> {
                int p = img.getRGB(x, y);
                int lum = (((p >> 16) & 0xFF) + ((p >> 8) & 0xFF) + (p & 0xFF)) / 3;
                yield lum >= thr;
            }
            case DIAMOND -> {
                double nx = Math.abs(x - cx) / cx;
                double ny = Math.abs(y - cy) / cy;
                yield (nx + ny) <= (thr / 255.0) * 2.0;
            }
            case STRIPES -> (x / cell) % 2 == 0;
        };
    }

    private static int applyOp(int value, int mask, LogicOpType op) {
        int result = switch (op) {
            case AND -> value & mask;
            case OR -> value | mask;
            case XOR -> value ^ mask;
            case NAND -> ~(value & mask);
            case NOR -> ~(value | mask);
            case INVERT -> ~value;
            case COPY_INVERTED -> ~mask;
        };
        return result & 0xFF;
    }
}
