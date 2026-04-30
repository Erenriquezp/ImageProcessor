package com.example.imageprocessor.service;

import com.example.imageprocessor.domain.GradientType;

import java.awt.image.BufferedImage;

final class GradientGenerator {

    private GradientGenerator() {
    }

    static BufferedImage generateGradient(GradientType type, int width, int height, int startRgb, int endRgb) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int sr = (startRgb >> 16) & 0xFF;
        int sg = (startRgb >> 8) & 0xFF;
        int sb = startRgb & 0xFF;

        int er = (endRgb >> 16) & 0xFF;
        int eg = (endRgb >> 8) & 0xFF;
        int eb = endRgb & 0xFF;

        double cx = width / 2.0;
        double cy = height / 2.0;
        double distMax = Math.sqrt(cx * cx + cy * cy);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double t;
                switch (type) {
                    case LEFT_TO_RIGHT:
                        t = (double) x / (width - 1);
                        break;
                    case RIGHT_TO_LEFT:
                        t = 1.0 - ((double) x / (width - 1));
                        break;
                    case TOP_TO_BOTTOM:
                        t = (double) y / (height - 1);
                        break;
                    case BOTTOM_TO_TOP:
                        t = 1.0 - ((double) y / (height - 1));
                        break;
                    case RADIAL:
                        double dx = x - cx;
                        double dy = y - cy;
                        t = Math.sqrt(dx * dx + dy * dy) / distMax;
                        t = PixelMath.clamp01((float) t);
                        break;
                    default:
                        t = 0;
                }

                int r = PixelMath.lerp(sr, er, t);
                int g = PixelMath.lerp(sg, eg, t);
                int b = PixelMath.lerp(sb, eb, t);
                out.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }
}

