package com.example.imageprocessor.service;

final class PixelMath {

    private PixelMath() {
    }

    static int quantize(int value, int n) {
        if (n <= 1) {
            return 0;
        }
        if (n >= 256) {
            return value;
        }
        double step = 255.0 / (n - 1);
        int level = (int) Math.round(value / step);
        return (int) Math.round(level * step);
    }

    static int lerp(int start, int end, double t) {
        return clamp((int) Math.round(start + t * (end - start)));
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}

