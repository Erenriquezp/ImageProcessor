package com.example.imageprocessor.domain;

public enum ConvolutionKernel {
    BLUR("Blur 3x3", new float[]{
            1f / 9f, 1f / 9f, 1f / 9f,
            1f / 9f, 1f / 9f, 1f / 9f,
            1f / 9f, 1f / 9f, 1f / 9f
    }),
    SHARPEN("Sharpen", new float[]{
            0f, -1f, 0f,
            -1f, 5f, -1f,
            0f, -1f, 0f
    }),
    EDGES("Bordes", new float[]{
            -0.5f, -0.5f, -0.5f,
            -0.5f, 4f, -0.5f,
            -0.5f, -0.5f, -0.5f
    }),
    EMBOSS("Emboss", new float[]{
            -2f, -1f, 0f,
            -1f, 1f, 1f,
            0f, 1f, 2f
    });

    private final String label;
    private final float[] kernelValues;

    ConvolutionKernel(String label, float[] kernelValues) {
        this.label = label;
        this.kernelValues = kernelValues;
    }

    public float[] kernelValues() {
        return kernelValues;
    }

    @Override
    public String toString() {
        return label;
    }
}
