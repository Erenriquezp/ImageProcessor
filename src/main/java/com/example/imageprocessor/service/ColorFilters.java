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
}

