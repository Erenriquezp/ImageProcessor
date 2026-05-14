package com.example.imageprocessor.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Alpha-blending between a foreground and a background image.
 * Pure stateless utility — all methods are static.
 */
final class BlendingService {

    private BlendingService() {
    }

    /**
     * Blends {@code original} (foreground) with {@code background} using the
     * formula {@code result = (1 − alpha) × foreground + alpha × background}.
     *
     * <p>The background is scaled with bilinear interpolation to exactly match
     * the dimensions of the foreground image before blending.  The alpha channel
     * of each foreground pixel is preserved in the output.
     *
     * @param original   foreground image (never mutated)
     * @param background background image (never mutated)
     * @param alpha      blend factor in [0, 1]:  0 → pure foreground,
     *                   1 → pure background, 0.5 → 50/50 mix
     * @return new {@link BufferedImage} (TYPE_INT_ARGB) with the blend applied
     */
    static BufferedImage blend(BufferedImage original, BufferedImage background, float alpha) {
        int w = original.getWidth();
        int h = original.getHeight();

        // Scale background to the foreground dimensions with bilinear interpolation
        BufferedImage bg = scaleToFit(background, w, h);

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pOrig = original.getRGB(x, y);
                int pBg   = bg.getRGB(x, y);

                // Preserve original alpha channel
                int a  = (pOrig >> 24) & 0xFF;

                int rO = (pOrig >> 16) & 0xFF;
                int gO = (pOrig >>  8) & 0xFF;
                int bO =  pOrig        & 0xFF;

                int rB = (pBg >> 16) & 0xFF;
                int gB = (pBg >>  8) & 0xFF;
                int bB =  pBg        & 0xFF;

                // lerp(start, end, t) = start + t*(end-start) = (1-t)*start + t*end
                int nr = PixelMath.lerp(rO, rB, alpha);
                int ng = PixelMath.lerp(gO, gB, alpha);
                int nb = PixelMath.lerp(bO, bB, alpha);

                out.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        return out;
    }

    /**
     * Blends three images using independent per-image weight factors.
     * <p>
     * Each output channel is computed as:
     * <pre>  result = clamp(ch1·alpha1 + ch2·alpha2 + ch3·alpha3)</pre>
     * The weights do <em>not</em> need to sum to 1 — they are applied as raw
     * multipliers, identical to the original {@code TripleBlending} algorithm.
     * Both {@code img2} and {@code img3} are scaled to match {@code img1}'s
     * dimensions using bilinear interpolation before blending.
     * The alpha channel of {@code img1} is preserved in the output.
     *
     * @param img1   foreground / base image (never mutated)
     * @param img2   first background (never mutated)
     * @param img3   second background (never mutated)
     * @param alpha1 weight for {@code img1} channels, typically [0, 1]
     * @param alpha2 weight for {@code img2} channels, typically [0, 1]
     * @param alpha3 weight for {@code img3} channels, typically [0, 1]
     * @return new {@link BufferedImage} (TYPE_INT_ARGB) with the triple blend applied
     */
    static BufferedImage tripleBlend(BufferedImage img1, BufferedImage img2, BufferedImage img3,
                                     float alpha1, float alpha2, float alpha3) {
        int w = img1.getWidth();
        int h = img1.getHeight();

        BufferedImage s2 = scaleToFit(img2, w, h);
        BufferedImage s3 = scaleToFit(img3, w, h);

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p1 = img1.getRGB(x, y);
                int p2 = s2.getRGB(x, y);
                int p3 = s3.getRGB(x, y);

                int a  = (p1 >> 24) & 0xFF;   // preserve foreground alpha

                int r1 = (p1 >> 16) & 0xFF;
                int g1 = (p1 >>  8) & 0xFF;
                int b1 =  p1        & 0xFF;

                int r2 = (p2 >> 16) & 0xFF;
                int g2 = (p2 >>  8) & 0xFF;
                int b2 =  p2        & 0xFF;

                int r3 = (p3 >> 16) & 0xFF;
                int g3 = (p3 >>  8) & 0xFF;
                int b3 =  p3        & 0xFF;

                int nr = PixelMath.clamp((int)(r1 * alpha1 + r2 * alpha2 + r3 * alpha3));
                int ng = PixelMath.clamp((int)(g1 * alpha1 + g2 * alpha2 + g3 * alpha3));
                int nb = PixelMath.clamp((int)(b1 * alpha1 + b2 * alpha2 + b3 * alpha3));

                out.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        return out;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns a new image scaled to {@code w × h} using bilinear interpolation.
     * If the source already has the requested dimensions it is returned as-is
     * (no copy is made).
     */
    private static BufferedImage scaleToFit(BufferedImage source, int w, int h) {
        if (source.getWidth() == w && source.getHeight() == h) {
            return source;
        }
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                             RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                             RenderingHints.VALUE_RENDER_QUALITY);
        g2d.drawImage(source, 0, 0, w, h, null);
        g2d.dispose();
        return scaled;
    }
}

