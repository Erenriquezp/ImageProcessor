package com.example.imageprocessor.service;

import java.awt.image.BufferedImage;

import com.example.imageprocessor.domain.ColorSpaceType;
import com.example.imageprocessor.domain.ConvolutionKernel;
import com.example.imageprocessor.domain.DepthSource;
import com.example.imageprocessor.domain.EqualizeMode;
import com.example.imageprocessor.domain.FragmentBlendMode;
import com.example.imageprocessor.domain.GradientType;
import com.example.imageprocessor.domain.LogicOpType;
import com.example.imageprocessor.domain.StencilPattern;
import com.example.imageprocessor.domain.StretchMode;
import com.example.imageprocessor.domain.TextureFilterMode;

public final class ImageProcessor {

    private ImageProcessor() {
    }

    public static BufferedImage grayscale(BufferedImage original) {
        return ColorFilters.grayscale(original);
    }

    public static BufferedImage negative(BufferedImage original) {
        return ColorFilters.negative(original);
    }

    public static BufferedImage brightness(BufferedImage original, int value) {
        return ColorFilters.brightness(original, value);
    }

    public static BufferedImage hsv(BufferedImage original, float factorS, float factorV) {
        return ColorFilters.hsv(original, factorS, factorV);
    }

    public static BufferedImage frosted(BufferedImage original) {
        return ArtisticFilters.frosted(original);
    }

    public static BufferedImage circularFade(BufferedImage original) {
        return ArtisticFilters.circularFade(original);
    }

    public static BufferedImage retro1(BufferedImage original, int levels) {
        return ArtisticFilters.retro1(original, levels);
    }

    public static BufferedImage retro2(BufferedImage original, int levels,
            boolean quantR, boolean quantG, boolean quantB) {
        return ArtisticFilters.retro2(original, levels, quantR, quantG, quantB);
    }

    public static BufferedImage grayscaleQuantized(BufferedImage original, int levels) {
        return ColorFilters.grayscaleQuantized(original, levels);
    }

    public static BufferedImage alphaGlobal(BufferedImage original, int alpha) {
        return ColorFilters.alphaGlobal(original, alpha);
    }

    public static BufferedImage bwThreshold(BufferedImage original, int threshold) {
        return ColorFilters.bwThreshold(original, threshold);
    }

    public static BufferedImage recolor(BufferedImage original, int toneR, int toneG, int toneB) {
        return ArtisticFilters.recolor(original, toneR, toneG, toneB);
    }

    public static BufferedImage stretch4Bits(BufferedImage original, StretchMode mode) {
        return ArtisticFilters.stretch4Bits(original, mode);
    }

    public static BufferedImage convolution(BufferedImage original, ConvolutionKernel kernelType) {
        return ConvolutionFilters.convolution(original, kernelType);
    }

    // ── Color Matrix ──────────────────────────────────────────────────────────

    public static BufferedImage sepia(BufferedImage original) {
        return ColorFilters.sepia(original);
    }

    public static BufferedImage coolTone(BufferedImage original) {
        return ColorFilters.coolTone(original);
    }

    public static BufferedImage warmTone(BufferedImage original) {
        return ColorFilters.warmTone(original);
    }

    public static BufferedImage polaroid(BufferedImage original) {
        return ColorFilters.polaroid(original);
    }

    public static BufferedImage kodachrome(BufferedImage original) {
        return ColorFilters.kodachrome(original);
    }

    public static BufferedImage bufferAccumulation(BufferedImage original, BufferAcumulacion_LOAD.Mode mode) {
        return BufferAcumulacion_LOAD.applyAccumulation(original, mode, 25, 8);
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
        return RasterPipelineFilters.depthMap(original, source);
    }

    public static BufferedImage zBuffer(BufferedImage original, DepthSource source,
            int threshold, int planeR, int planeG, int planeB) {
        return RasterPipelineFilters.zBuffer(original, source, threshold, planeR, planeG, planeB);
    }

    public static BufferedImage bitmapPixelate(BufferedImage original, int blockSize) {
        return RasterPipelineFilters.bitmapPixelate(original, blockSize);
    }

    public static BufferedImage rasterGrid(BufferedImage original, int blockSize) {
        return RasterPipelineFilters.rasterGrid(original, blockSize);
    }

    public static BufferedImage textureSample(BufferedImage original, float scale, TextureFilterMode mode) {
        return RasterPipelineFilters.textureSample(original, scale, mode);
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
        return FragmentOpsFilters.fragmentBlend(original, mode, dstR, dstG, dstB, alpha);
    }

    public static BufferedImage logicOp(BufferedImage original, LogicOpType op, int mask) {
        return FragmentOpsFilters.logicOp(original, op, mask);
    }

    // ── Histograma / operaciones por punto ────────────────────────────────────

    public static BufferedImage histogramEqualize(BufferedImage original, EqualizeMode mode) {
        return PointOpsFilters.histogramEqualize(original, mode);
    }

    public static BufferedImage colorAdjust(BufferedImage original, float gainR, float gainG, float gainB) {
        return PointOpsFilters.colorAdjust(original, gainR, gainG, gainB);
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
        return PointOpsFilters.scaleBias(original, scale, bias);
    }

    public static BufferedImage pointThreshold(BufferedImage original, int threshold, int softWidth) {
        return PointOpsFilters.pointThreshold(original, threshold, softWidth);
    }

    public static BufferedImage toLuminance(BufferedImage original) {
        return PointOpsFilters.toLuminance(original);
    }

    public static BufferedImage pointSaturation(BufferedImage original, float factor) {
        return PointOpsFilters.pointSaturation(original, factor);
    }

    public static BufferedImage hueRotate(BufferedImage original, float degrees) {
        return PointOpsFilters.hueRotate(original, degrees);
    }

    public static BufferedImage colorSpaceConvert(BufferedImage original, ColorSpaceType type) {
        return PointOpsFilters.colorSpaceConvert(original, type);
    }
}
