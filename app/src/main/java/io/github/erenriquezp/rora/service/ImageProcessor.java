package io.github.erenriquezp.rora.service;

import java.awt.image.BufferedImage;

import io.github.erenriquezp.rora.domain.ColorSpaceType;
import io.github.erenriquezp.rora.domain.ConvolutionKernel;
import io.github.erenriquezp.rora.domain.DepthSource;
import io.github.erenriquezp.rora.domain.EqualizeMode;
import io.github.erenriquezp.rora.domain.ExposureDiagnostic;
import io.github.erenriquezp.rora.domain.FragmentBlendMode;
import io.github.erenriquezp.rora.domain.GradientType;
import io.github.erenriquezp.rora.domain.LogicOpType;
import io.github.erenriquezp.rora.domain.StencilPattern;
import io.github.erenriquezp.rora.domain.StretchMode;
import io.github.erenriquezp.rora.domain.TextureFilterMode;

public final class ImageProcessor {

    private ImageProcessor() {
    }

    public static BufferedImage grayscale(BufferedImage original) {
        return CoreBridge.apply("color.grayscale-average", original, p -> p);
    }

    public static BufferedImage negative(BufferedImage original) {
        return CoreBridge.apply("color.negative", original, p -> p);
    }

    public static BufferedImage brightness(BufferedImage original, int value) {
        return CoreBridge.apply("color.brightness", original, p -> p.set("value", value));
    }

    public static BufferedImage hsv(BufferedImage original, float factorS, float factorV) {
        return CoreBridge.apply("color.hsv", original, p -> p.set("saturation", factorS).set("value", factorV));
    }

    public static BufferedImage frosted(BufferedImage original) {
        return ArtisticFilters.frosted(original);
    }

    public static BufferedImage circularFade(BufferedImage original) {
        return CoreBridge.apply("artistic.circular-fade", original, p -> p);
    }

    public static BufferedImage retro1(BufferedImage original, int levels) {
        return CoreBridge.apply("tone.posterize", original, p -> p.set("levels", levels));
    }

    public static BufferedImage retro2(BufferedImage original, int levels,
            boolean quantR, boolean quantG, boolean quantB) {
        java.util.List<String> channels = new java.util.ArrayList<>(3);
        if (quantR) channels.add("r");
        if (quantG) channels.add("g");
        if (quantB) channels.add("b");
        return CoreBridge.apply("tone.posterize-channels", original,
                p -> p.set("levels", levels).set("channels", channels));
    }

    public static BufferedImage grayscaleQuantized(BufferedImage original, int levels) {
        return CoreBridge.apply("color.grayscale-quantized", original, p -> p.set("levels", levels));
    }

    public static BufferedImage alphaGlobal(BufferedImage original, int alpha) {
        return ColorFilters.alphaGlobal(original, alpha);
    }

    public static BufferedImage bwThreshold(BufferedImage original, int threshold) {
        return CoreBridge.apply("color.threshold-bw", original, p -> p.set("threshold", threshold));
    }

    public static BufferedImage recolor(BufferedImage original, int toneR, int toneG, int toneB) {
        // Un ColorParam solo representa canales 0-255; fuera de ese rango (la UI nunca lo envia) se
        // mantiene el legacy para no cambiar el resultado.
        if (!CoreBridge.isRgb8(toneR, toneG, toneB)) {
            return ArtisticFilters.recolor(original, toneR, toneG, toneB);
        }
        return CoreBridge.apply("color.recolor", original,
                p -> p.set("tone", CoreBridge.rgb(toneR, toneG, toneB)));
    }

    public static BufferedImage stretch4Bits(BufferedImage original, StretchMode mode) {
        return ArtisticFilters.stretch4Bits(original, mode);
    }

    public static BufferedImage convolution(BufferedImage original, ConvolutionKernel kernelType) {
        return ConvolutionFilters.convolution(original, kernelType);
    }

    // ── Color Matrix ──────────────────────────────────────────────────────────

    public static BufferedImage sepia(BufferedImage original) {
        return CoreBridge.apply("color.matrix-preset", original, p -> p.set("preset", "sepia"));
    }

    public static BufferedImage coolTone(BufferedImage original) {
        return CoreBridge.apply("color.matrix-preset", original, p -> p.set("preset", "cool"));
    }

    public static BufferedImage warmTone(BufferedImage original) {
        return CoreBridge.apply("color.matrix-preset", original, p -> p.set("preset", "warm"));
    }

    public static BufferedImage polaroid(BufferedImage original) {
        return CoreBridge.apply("color.matrix-preset", original, p -> p.set("preset", "polaroid"));
    }

    public static BufferedImage kodachrome(BufferedImage original) {
        return CoreBridge.apply("color.matrix-preset", original, p -> p.set("preset", "kodachrome"));
    }

    public static BufferedImage bufferAccumulation(BufferedImage original, BufferAcumulacion_LOAD.Mode mode) {
        // Como el legacy: un modo null lanza NullPointerException en el switch.
        String modeId = switch (mode) {
            case ADD -> "add";
            case ACUM -> "accum";
            case MULT -> "mult";
        };
        return CoreBridge.apply("accum.motion", original,
                p -> p.set("mode", modeId).set("samples", 25).set("displacement", 8));
    }

    // ── Histogram ─────────────────────────────────────────────────────────────

    public static BufferedImage generateHistogram(BufferedImage original) {
        return HistogramService.generateHistogram(original);
    }

    // ── Blending ──────────────────────────────────────────────────────────────

    /**
     * Alpha-blends {@code original} (foreground) with {@code background}.
     *
     * @param alpha blend factor [0, 1]: 0 → pure foreground, 1 → pure background
     */
    public static BufferedImage blend(BufferedImage original, BufferedImage background, float alpha) {
        return BlendingService.blend(original, background, alpha);
    }

    /**
     * Blends three images with independent per-image weight factors.
     * {@code result_channel = clamp(ch1·a1 + ch2·a2 + ch3·a3)}
     *
     * @param alpha1 weight for {@code img1} (foreground / editor image)
     * @param alpha2 weight for {@code img2} (first background)
     * @param alpha3 weight for {@code img3} (second background)
     */
    public static BufferedImage tripleBlend(BufferedImage img1, BufferedImage img2, BufferedImage img3,
            float alpha1, float alpha2, float alpha3) {
        return BlendingService.tripleBlend(img1, img2, img3, alpha1, alpha2, alpha3);
    }

    public static BufferedImage generateGradient(GradientType type, int width, int height, int startRgb, int endRgb) {
        return GradientGenerator.generateGradient(type, width, height, startRgb, endRgb);
    }

    // ── Raster / Depth / Texturas ─────────────────────────────────────────────

    public static BufferedImage depthMap(BufferedImage original, DepthSource source) {
        return CoreBridge.apply("raster.depth-map", original, p -> p.set("source", CoreBridge.enumId(source)));
    }

    public static BufferedImage zBuffer(BufferedImage original, DepthSource source,
            int threshold, int planeR, int planeG, int planeB) {
        return RasterPipelineFilters.zBuffer(original, source, threshold, planeR, planeG, planeB);
    }

    public static BufferedImage bitmapPixelate(BufferedImage original, int blockSize) {
        return CoreBridge.apply("raster.pixelate", original, p -> p.set("block", blockSize));
    }

    public static BufferedImage rasterGrid(BufferedImage original, int blockSize) {
        return RasterPipelineFilters.rasterGrid(original, blockSize);
    }

    public static BufferedImage textureSample(BufferedImage original, float scale, TextureFilterMode mode) {
        // Como el legacy: cualquier modo distinto de BILINEAR (incluido null) es vecino mas cercano.
        String filter = mode == TextureFilterMode.BILINEAR ? "bilinear" : "nearest";
        return CoreBridge.apply("raster.texture-sample", original,
                p -> p.set("scale", scale).set("filter", filter));
    }

    public static BufferedImage depthInterpolate(BufferedImage original, DepthSource source,
            int nearR, int nearG, int nearB, int farR, int farG, int farB) {
        return RasterPipelineFilters.depthInterpolate(original, source, nearR, nearG, nearB, farR, farG, farB);
    }

    public static BufferedImage wBuffer(BufferedImage original, DepthSource source,
            int threshold, int planeR, int planeG, int planeB) {
        return RasterPipelineFilters.wBuffer(original, source, threshold, planeR, planeG, planeB);
    }

    // ── Operaciones con fragmentos ────────────────────────────────────────────

    public static BufferedImage multisample(BufferedImage original, int samples) {
        return FragmentOpsFilters.multisample(original, samples);
    }

    public static BufferedImage alphaTest(BufferedImage original, int threshold, boolean useLuminance) {
        return FragmentOpsFilters.alphaTest(original, threshold, useLuminance);
    }

    public static BufferedImage stencilTest(BufferedImage original, StencilPattern pattern,
            int threshold, int cellSize) {
        return FragmentOpsFilters.stencilTest(original, pattern, threshold, cellSize);
    }

    public static BufferedImage fragmentBlend(BufferedImage original, FragmentBlendMode mode,
            int dstR, int dstG, int dstB, float alpha) {
        // Ver recolor: canales fuera de 0-255 siguen en el legacy.
        if (!CoreBridge.isRgb8(dstR, dstG, dstB)) {
            return FragmentOpsFilters.fragmentBlend(original, mode, dstR, dstG, dstB, alpha);
        }
        return CoreBridge.apply("fragment.blend-color", original, p -> p
                .set("mode", CoreBridge.enumId(mode))
                .set("color", CoreBridge.rgb(dstR, dstG, dstB))
                .set("alpha", alpha));
    }

    public static BufferedImage logicOp(BufferedImage original, LogicOpType op, int mask) {
        return FragmentOpsFilters.logicOp(original, op, mask);
    }

    // ── Histograma / operaciones por punto ────────────────────────────────────

    public static BufferedImage histogramEqualize(BufferedImage original, EqualizeMode mode, float factor, boolean markBurned, boolean markDark) {
        // Como el legacy: cualquier modo distinto de LUMINANCE (incluido null) es RGB.
        String modeId = mode == EqualizeMode.LUMINANCE ? "luminance" : "rgb";
        return CoreBridge.apply("tone.equalize", original, p -> p
                .set("mode", modeId)
                .set("factor", factor)
                .set("markBurned", markBurned)
                .set("markDark", markDark));
    }

    public static ExposureDiagnostic diagnoseExposure(BufferedImage original) {
        return PointOpsFilters.diagnoseExposure(original);
    }

    public static BufferedImage colorAdjust(BufferedImage original, float gainR, float gainG, float gainB) {
        return CoreBridge.apply("point.color-gain", original, p -> p.set("gainR", gainR).set("gainG", gainG).set("gainB", gainB));
    }

    public static BufferedImage pointInterpolate(BufferedImage original, float t,
            int targetR, int targetG, int targetB) {
        return PointOpsFilters.pointLerp(original, t, targetR, targetG, targetB);
    }

    public static BufferedImage pointExtrapolate(BufferedImage original, float t,
            int targetR, int targetG, int targetB) {
        return PointOpsFilters.pointLerp(original, t, targetR, targetG, targetB);
    }

    public static BufferedImage scaleBias(BufferedImage original, float scale, float bias) {
        return CoreBridge.apply("point.scale-bias", original, p -> p.set("scale", scale).set("bias", bias));
    }

    public static BufferedImage pointThreshold(BufferedImage original, int threshold, int softWidth) {
        return CoreBridge.apply("point.soft-threshold", original, p -> p.set("threshold", threshold).set("softWidth", softWidth));
    }

    public static BufferedImage toLuminance(BufferedImage original) {
        return CoreBridge.apply("point.luminance", original, p -> p);
    }

    public static BufferedImage pointSaturation(BufferedImage original, float factor) {
        return CoreBridge.apply("point.saturation", original, p -> p.set("factor", factor));
    }

    public static BufferedImage hueRotate(BufferedImage original, float degrees) {
        return CoreBridge.apply("point.hue-rotate", original, p -> p.set("degrees", degrees));
    }

    public static BufferedImage colorSpaceConvert(BufferedImage original, ColorSpaceType type) {
        return PointOpsFilters.colorSpaceConvert(original, type);
    }
}
