package com.example.imageprocessor.service;

import com.example.imageprocessor.domain.ConvolutionKernel;
import com.example.imageprocessor.domain.GradientType;
import com.example.imageprocessor.domain.StretchMode;

import java.awt.image.BufferedImage;

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

    public static BufferedImage generateGradient(GradientType type, int width, int height, int startRgb, int endRgb) {
        return GradientGenerator.generateGradient(type, width, height, startRgb, endRgb);
    }
}

