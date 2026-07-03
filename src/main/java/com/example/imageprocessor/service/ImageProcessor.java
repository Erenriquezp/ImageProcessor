package com.example.imageprocessor.service;

import java.awt.image.BufferedImage;

import com.example.imageprocessor.domain.ConvolutionKernel;
import com.example.imageprocessor.domain.GradientType;
import com.example.imageprocessor.domain.StretchMode;

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
}
