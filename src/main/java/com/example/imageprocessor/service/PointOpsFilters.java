package com.example.imageprocessor.service;

import java.awt.Color;
import java.awt.image.BufferedImage;

import com.example.imageprocessor.domain.ColorSpaceType;
import com.example.imageprocessor.domain.EqualizeMode;
import com.example.imageprocessor.domain.ExposureDiagnostic;

/**
 * Operaciones por punto (point operations) y ecualización de histograma.
 * Todas recorren la imagen píxel a píxel.
 */
final class PointOpsFilters {

    private PointOpsFilters() {
    }

    // ── Ecualización del histograma ──────────────────────────────────────────

    /**
     * Ecualiza el histograma para expandir el contraste, mezclando con la imagen
     * original y opcionalmente marcando zonas quemadas u oscuras.
     * <ul>
     * <li>{@link EqualizeMode#RGB} — CDF independiente por canal</li>
     * <li>{@link EqualizeMode#LUMINANCE} — ecualiza Y (BT.709) y reescala RGB</li>
     * </ul>
     */
    static BufferedImage histogramEqualize(BufferedImage original, EqualizeMode mode, float factor, boolean markBurned, boolean markDark) {
        BufferedImage equalized = mode == EqualizeMode.LUMINANCE
                ? equalizeLuminance(original)
                : equalizeRgb(original);

        BufferedImage blended = mix(original, equalized, factor);

        if (markBurned || markDark) {
            return markZones(blended, markBurned, markDark);
        }
        return blended;
    }

    private static BufferedImage mix(BufferedImage original, BufferedImage equalized, float factor) {
        if (factor <= 0f) return original;
        if (factor >= 1f) return equalized;

        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pOrig = original.getRGB(x, y);
                int pEq = equalized.getRGB(x, y);

                int a = (pOrig >> 24) & 0xFF;
                int rOrig = (pOrig >> 16) & 0xFF;
                int gOrig = (pOrig >> 8) & 0xFF;
                int bOrig = pOrig & 0xFF;

                int rEq = (pEq >> 16) & 0xFF;
                int gEq = (pEq >> 8) & 0xFF;
                int bEq = pEq & 0xFF;

                int r = PixelMath.clamp(Math.round(rOrig * (1f - factor) + rEq * factor));
                int g = PixelMath.clamp(Math.round(gOrig * (1f - factor) + gEq * factor));
                int b = PixelMath.clamp(Math.round(bOrig * (1f - factor) + bEq * factor));

                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    private static BufferedImage markZones(BufferedImage image, boolean markBurned, boolean markDark) {
        int w = image.getWidth();
        int h = image.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = image.getRGB(x, y);
                int a = (pixel >> 24) & 0xFF;
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                if (markBurned && r >= 240 && g >= 240 && b >= 240) {
                    out.setRGB(x, y, (a << 24) | (255 << 16) | (0 << 8) | 0);
                } else if (markDark && r <= 15 && g <= 15 && b <= 15) {
                    out.setRGB(x, y, (a << 24) | (0 << 16) | (0 << 8) | 255);
                } else {
                    out.setRGB(x, y, pixel);
                }
            }
        }
        return out;
    }

    static ExposureDiagnostic diagnoseExposure(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int total = w * h;

        int burned = 0;
        int dark = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = image.getRGB(x, y);
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;

                if (r >= 240 && g >= 240 && b >= 240) burned++;
                if (r <= 15 && g <= 15 && b <= 15) dark++;
            }
        }

        double pctBurned = (burned * 100.0) / total;
        double pctDark = (dark * 100.0) / total;

        StringBuilder sb = new StringBuilder();
        if (pctBurned > 5) {
            sb.append(String.format("Imagen quemada (%.1f%% sobreexpuesto)", pctBurned));
        }
        if (pctDark > 5) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(String.format("Imagen oscura (%.1f%% subexpuesto)", pctDark));
        }
        if (sb.length() == 0) {
            sb.append("Exposición adecuada");
        }

        return new ExposureDiagnostic(pctBurned, pctDark, sb.toString());
    }

    private static BufferedImage equalizeRgb(BufferedImage original) {
        int w = original.getWidth();
        int h = original.getHeight();
        int n = w * h;
        int[] histR = new int[256];
        int[] histG = new int[256];
        int[] histB = new int[256];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                histR[(p >> 16) & 0xFF]++;
                histG[(p >> 8) & 0xFF]++;
                histB[p & 0xFF]++;
            }
        }

        int[] mapR = cdfLookup(histR, n);
        int[] mapG = cdfLookup(histG, n);
        int[] mapB = cdfLookup(histB, n);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int a = (p >> 24) & 0xFF;
                int r = mapR[(p >> 16) & 0xFF];
                int g = mapG[(p >> 8) & 0xFF];
                int b = mapB[p & 0xFF];
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    private static BufferedImage equalizeLuminance(BufferedImage original) {
        int w = original.getWidth();
        int h = original.getHeight();
        int n = w * h;
        int[] histY = new int[256];
        float[] ys = new float[n];
        int[] pixels = new int[n];
        int i = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                pixels[i] = p;
                float yy = luminance709((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF);
                ys[i] = yy;
                histY[PixelMath.clamp(Math.round(yy))]++;
                i++;
            }
        }

        int[] mapY = cdfLookup(histY, n);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int k = 0; k < n; k++) {
            int p = pixels[k];
            int a = (p >> 24) & 0xFF;
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            float y0 = Math.max(1e-3f, ys[k]);
            float y1 = mapY[PixelMath.clamp(Math.round(y0))];
            float s = y1 / y0;
            int nr = PixelMath.clamp(Math.round(r * s));
            int ng = PixelMath.clamp(Math.round(g * s));
            int nb = PixelMath.clamp(Math.round(b * s));
            int xx = k % w;
            int yy = k / w;
            out.setRGB(xx, yy, (a << 24) | (nr << 16) | (ng << 8) | nb);
        }
        return out;
    }

    /** LUT a partir de la CDF normalizada: {@code T(v) = round(255 · CDF(v) / n)}. */
    private static int[] cdfLookup(int[] hist, int n) {
        int[] map = new int[256];
        int cdf = 0;
        int cdfMin = 0;
        for (int v = 0; v < 256; v++) {
            cdf += hist[v];
            if (cdfMin == 0 && cdf > 0) {
                cdfMin = cdf;
            }
            if (n == cdfMin) {
                map[v] = 0;
            } else {
                map[v] = PixelMath.clamp(Math.round(255f * (cdf - cdfMin) / (n - cdfMin)));
            }
        }
        return map;
    }

    // ── Ajuste de color (ganancia por canal) ──────────────────────────────────

    /** {@code out = clamp(channel · gain)} con gain por canal. */
    static BufferedImage colorAdjust(BufferedImage original, float gainR, float gainG, float gainB) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int a = (p >> 24) & 0xFF;
                int r = PixelMath.clamp(Math.round(((p >> 16) & 0xFF) * gainR));
                int g = PixelMath.clamp(Math.round(((p >> 8) & 0xFF) * gainG));
                int b = PixelMath.clamp(Math.round((p & 0xFF) * gainB));
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    // ── Interpolación / extrapolación hacia un color ─────────────────────────

    /**
     * {@code out = lerp(src, target, t)}. Con {@code t ∈ [0,1]} es interpolación;
     * fuera de ese rango es extrapolación (luego clamp).
     */
    static BufferedImage pointLerp(BufferedImage original, float t,
            int targetR, int targetG, int targetB) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int a = (p >> 24) & 0xFF;
                int r = PixelMath.clamp(Math.round(((p >> 16) & 0xFF) + t * (targetR - ((p >> 16) & 0xFF))));
                int g = PixelMath.clamp(Math.round(((p >> 8) & 0xFF) + t * (targetG - ((p >> 8) & 0xFF))));
                int b = PixelMath.clamp(Math.round((p & 0xFF) + t * (targetB - (p & 0xFF))));
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    // ── Escala + bias ────────────────────────────────────────────────────────

    /** {@code out = clamp(scale · channel + bias)} en cada canal RGB. */
    static BufferedImage scaleBias(BufferedImage original, float scale, float bias) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int a = (p >> 24) & 0xFF;
                int r = PixelMath.clamp(Math.round(((p >> 16) & 0xFF) * scale + bias));
                int g = PixelMath.clamp(Math.round(((p >> 8) & 0xFF) * scale + bias));
                int b = PixelMath.clamp(Math.round((p & 0xFF) * scale + bias));
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    // ── Umbralización ────────────────────────────────────────────────────────

    /**
     * Umbralización por luminancia: por encima → blanco, por debajo → negro.
     * Si {@code softWidth > 0}, transición suave (soft threshold).
     */
    static BufferedImage pointThreshold(BufferedImage original, int threshold, int softWidth) {
        int w = original.getWidth();
        int h = original.getHeight();
        int thr = PixelMath.clamp(threshold);
        int soft = Math.max(0, softWidth);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int a = (p >> 24) & 0xFF;
                int lum = Math.round(luminance709((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF));
                int v;
                if (soft <= 0) {
                    v = lum >= thr ? 255 : 0;
                } else {
                    float t = (lum - (thr - soft)) / (float) (2 * soft);
                    v = PixelMath.clamp(Math.round(PixelMath.clamp01(t) * 255));
                }
                out.setRGB(x, y, (a << 24) | (v << 16) | (v << 8) | v);
            }
        }
        return out;
    }

    // ── Conversión a luminancia ──────────────────────────────────────────────

    /** Escala de grises BT.709: {@code Y = 0.2126 R + 0.7152 G + 0.0722 B}. */
    static BufferedImage toLuminance(BufferedImage original) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int a = (p >> 24) & 0xFF;
                int y709 = PixelMath.clamp(Math.round(
                        luminance709((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF)));
                out.setRGB(x, y, (a << 24) | (y709 << 16) | (y709 << 8) | y709);
            }
        }
        return out;
    }

    // ── Saturación ───────────────────────────────────────────────────────────

    /** Factor de saturación en HSV ({@code 0} = gris, {@code 1} = original, {@code >1} = más vivo). */
    static BufferedImage pointSaturation(BufferedImage original, float factor) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int a = (p >> 24) & 0xFF;
                float[] hsv = Color.RGBtoHSB((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF, null);
                hsv[1] = PixelMath.clamp01(hsv[1] * factor);
                int rgb = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);
                out.setRGB(x, y, (a << 24) | (rgb & 0x00FFFFFF));
            }
        }
        return out;
    }

    // ── Rotación de Hue ──────────────────────────────────────────────────────

    /** Desplaza el tono HSV en grados (−180…+180). */
    static BufferedImage hueRotate(BufferedImage original, float degrees) {
        int w = original.getWidth();
        int h = original.getHeight();
        float delta = degrees / 360f;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int a = (p >> 24) & 0xFF;
                float[] hsv = Color.RGBtoHSB((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF, null);
                float nh = hsv[0] + delta;
                nh = nh - (float) Math.floor(nh);
                int rgb = Color.HSBtoRGB(nh, hsv[1], hsv[2]);
                out.setRGB(x, y, (a << 24) | (rgb & 0x00FFFFFF));
            }
        }
        return out;
    }

    // ── Espacios de color ────────────────────────────────────────────────────

    static BufferedImage colorSpaceConvert(BufferedImage original, ColorSpaceType type) {
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
                int nr;
                int ng;
                int nb;

                switch (type) {
                    case RGB_TO_YCBCR -> {
                        float[] ycbcr = rgbToYCbCr(r, g, b);
                        nr = PixelMath.clamp(Math.round(ycbcr[0]));
                        ng = PixelMath.clamp(Math.round(ycbcr[1]));
                        nb = PixelMath.clamp(Math.round(ycbcr[2]));
                    }
                    case YCBCR_TO_RGB -> {
                        int[] rgb = yCbCrToRgb(r, g, b);
                        nr = rgb[0];
                        ng = rgb[1];
                        nb = rgb[2];
                    }
                    case RGB_TO_HSV -> {
                        float[] hsv = Color.RGBtoHSB(r, g, b, null);
                        nr = PixelMath.clamp(Math.round(hsv[0] * 255));
                        ng = PixelMath.clamp(Math.round(hsv[1] * 255));
                        nb = PixelMath.clamp(Math.round(hsv[2] * 255));
                    }
                    case HSV_TO_RGB -> {
                        int rgb = Color.HSBtoRGB(r / 255f, g / 255f, b / 255f);
                        nr = (rgb >> 16) & 0xFF;
                        ng = (rgb >> 8) & 0xFF;
                        nb = rgb & 0xFF;
                    }
                    case RGB_TO_HSL -> {
                        float[] hsl = rgbToHsl(r, g, b);
                        nr = PixelMath.clamp(Math.round(hsl[0] * 255));
                        ng = PixelMath.clamp(Math.round(hsl[1] * 255));
                        nb = PixelMath.clamp(Math.round(hsl[2] * 255));
                    }
                    case HSL_TO_RGB -> {
                        int[] rgb = hslToRgb(r / 255f, g / 255f, b / 255f);
                        nr = rgb[0];
                        ng = rgb[1];
                        nb = rgb[2];
                    }
                    default -> {
                        nr = r;
                        ng = g;
                        nb = b;
                    }
                }
                out.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        return out;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    static float luminance709(int r, int g, int b) {
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    /** ITU-R BT.601: Y∈[0,255], Cb/Cr∈[0,255] con offset 128. */
    private static float[] rgbToYCbCr(int r, int g, int b) {
        float y = 0.299f * r + 0.587f * g + 0.114f * b;
        float cb = 128f - 0.168736f * r - 0.331264f * g + 0.5f * b;
        float cr = 128f + 0.5f * r - 0.418688f * g - 0.081312f * b;
        return new float[] { y, cb, cr };
    }

    private static int[] yCbCrToRgb(int y, int cb, int cr) {
        float c = cb - 128f;
        float d = cr - 128f;
        int r = PixelMath.clamp(Math.round(y + 1.402f * d));
        int g = PixelMath.clamp(Math.round(y - 0.344136f * c - 0.714136f * d));
        int b = PixelMath.clamp(Math.round(y + 1.772f * c));
        return new int[] { r, g, b };
    }

    private static float[] rgbToHsl(int r, int g, int b) {
        float rf = r / 255f;
        float gf = g / 255f;
        float bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float l = (max + min) / 2f;
        float h;
        float s;
        if (max == min) {
            h = 0;
            s = 0;
        } else {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == rf) {
                h = ((gf - bf) / d + (gf < bf ? 6f : 0f)) / 6f;
            } else if (max == gf) {
                h = ((bf - rf) / d + 2f) / 6f;
            } else {
                h = ((rf - gf) / d + 4f) / 6f;
            }
        }
        return new float[] { h, s, l };
    }

    private static int[] hslToRgb(float h, float s, float l) {
        float r;
        float g;
        float b;
        if (s == 0f) {
            r = g = b = l;
        } else {
            float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
            float p = 2 * l - q;
            r = hueToRgb(p, q, h + 1f / 3f);
            g = hueToRgb(p, q, h);
            b = hueToRgb(p, q, h - 1f / 3f);
        }
        return new int[] {
                PixelMath.clamp(Math.round(r * 255)),
                PixelMath.clamp(Math.round(g * 255)),
                PixelMath.clamp(Math.round(b * 255))
        };
    }

    private static float hueToRgb(float p, float q, float t) {
        float tt = t;
        if (tt < 0)
            tt += 1;
        if (tt > 1)
            tt -= 1;
        if (tt < 1f / 6f)
            return p + (q - p) * 6f * tt;
        if (tt < 1f / 2f)
            return q;
        if (tt < 2f / 3f)
            return p + (q - p) * (2f / 3f - tt) * 6f;
        return p;
    }
}
