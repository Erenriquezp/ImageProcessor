package com.example.imageprocessor.service;

import com.example.imageprocessor.domain.StretchMode;

import java.awt.image.BufferedImage;

final class ArtisticFilters {

    private ArtisticFilters() {
    }

    static BufferedImage frosted(BufferedImage original) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                int brightness = (r + g + b) / 3;
                int a = 50 + (int) ((brightness / 255.0) * 205);
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    static BufferedImage circularFade(BufferedImage original) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        double cx = w / 2.0;
        double cy = h / 2.0;
        double distMax = Math.sqrt(cx * cx + cy * cy);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;

                double dx = x - cx;
                double dy = y - cy;
                double dist = Math.sqrt(dx * dx + dy * dy);
                double factor = Math.max(0.0, 1.0 - (dist / distMax));
                int a = (int) (255 * factor);
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    static BufferedImage retro1(BufferedImage original, int levels) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int a = (p >> 24) & 0xFF;
                int r = PixelMath.quantize((p >> 16) & 0xFF, levels);
                int g = PixelMath.quantize((p >> 8) & 0xFF, levels);
                int b = PixelMath.quantize(p & 0xFF, levels);
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    static BufferedImage retro2(BufferedImage original, int levels,
                                boolean quantR, boolean quantG, boolean quantB) {
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

                int nr = quantR ? PixelMath.quantize(r, levels) : r;
                int ng = quantG ? PixelMath.quantize(g, levels) : g;
                int nb = quantB ? PixelMath.quantize(b, levels) : b;

                out.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        return out;
    }

    static BufferedImage recolor(BufferedImage original, int toneR, int toneG, int toneB) {
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

                double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                int nr = PixelMath.clamp((int) Math.round((lum * toneR) / 255.0));
                int ng = PixelMath.clamp((int) Math.round((lum * toneG) / 255.0));
                int nb = PixelMath.clamp((int) Math.round((lum * toneB) / 255.0));
                out.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        return out;
    }

    static BufferedImage stretch4Bits(BufferedImage original, StretchMode mode) {
        int w = original.getWidth();
        int h = original.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = original.getRGB(x, y);
                int a = (p >> 24) & 0xFF;
                int r = ((p >> 16) & 0xFF) >> 4;
                int g = ((p >> 8) & 0xFF) >> 4;
                int b = (p & 0xFF) >> 4;

                switch (mode) {
                    case BINARIO:
                        r = (r << 4) | r;
                        g = (g << 4) | g;
                        b = (b << 4) | b;
                        break;
                    case DECIMAL:
                        r = (r * 255) / 15;
                        g = (g * 255) / 15;
                        b = (b * 255) / 15;
                        break;
                    case HEXADECIMAL:
                        r = (r << 4) | (r & 0x0F);
                        g = (g << 4) | (g & 0x0F);
                        b = (b << 4) | (b & 0x0F);
                        break;
                    default:
                        break;
                }
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }
}

