package com.example.imageprocessor.service;

import java.awt.image.BufferedImage;

public class BufferAcumulacion_LOAD {

    public enum Mode {
        ADD, ACUM, MULT
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /**
     * Aplica el filtro de buffer de acumulación según el modo seleccionado.
     * 
     * @param original     imagen de entrada
     * @param mode         tipo de acumulación (ADD, ACUM, MULT)
     * @param samples      número de muestras (usado en ACUM)
     * @param displacement desplazamiento entre muestras (usado en ACUM)
     * @return imagen procesada
     */
    public static BufferedImage applyAccumulation(BufferedImage original, Mode mode, int samples, int displacement) {
        return switch (mode) {
            case ADD -> aplicarADD(original);
            case ACUM -> aplicarACUM(original);
            case MULT -> aplicarMULT(original);
        };
    }

    public static BufferedImage aplicarADD(BufferedImage imagen) {
        int ancho = imagen.getWidth();
        int alto = imagen.getHeight();

        BufferedImage resultado = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);

        float valorAdd = 1.5f;

        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {

                int pixel = imagen.getRGB(x, y);

                int r = (int) (((pixel >> 16) & 0xFF) * valorAdd);
                int g = (int) (((pixel >> 8) & 0xFF) * valorAdd);
                int b = (int) ((pixel & 0xFF) * valorAdd);

                resultado.setRGB(x, y, (clamp(r) << 16) | (clamp(g) << 8) | clamp(b));
            }
        }

        return resultado;
    }

    public static BufferedImage aplicarACUM(BufferedImage imagen) {
        int ancho = imagen.getWidth();
        int alto = imagen.getHeight();

        int muestras = 25;
        int desplazamiento = 8;

        float[] bufferR = new float[ancho * alto];
        float[] bufferG = new float[ancho * alto];
        float[] bufferB = new float[ancho * alto];

        BufferedImage resultado = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);

        for (int i = 0; i < muestras; i++) {
            int offset = i * desplazamiento;
            float peso = (i == 0) ? 1.0f : (float) Math.pow(0.85, i);

            for (int y = 0; y < alto; y++) {
                for (int x = offset; x < ancho; x++) {

                    int origenX = x - offset;
                    int index = y * ancho + x;

                    int pixel = imagen.getRGB(origenX, y);

                    bufferR[index] += ((pixel >> 16) & 0xFF) * peso;
                    bufferG[index] += ((pixel >> 8) & 0xFF) * peso;
                    bufferB[index] += (pixel & 0xFF) * peso;
                }
            }
        }

        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {

                int index = y * ancho + x;

                int r = clamp((int) bufferR[index]);
                int g = clamp((int) bufferG[index]);
                int b = clamp((int) bufferB[index]);

                resultado.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        return resultado;
    }

    public static BufferedImage aplicarMULT(BufferedImage imagen) {
        int ancho = imagen.getWidth();
        int alto = imagen.getHeight();

        BufferedImage resultado = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);

        float factor = 0.6f;

        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {

                int pixel = imagen.getRGB(x, y);

                int r = (int) (((pixel >> 16) & 0xFF) * factor);
                int g = (int) (((pixel >> 8) & 0xFF) * factor);
                int b = (int) ((pixel & 0xFF) * factor);

                resultado.setRGB(x, y, (clamp(r) << 16) | (clamp(g) << 8) | clamp(b));
            }
        }

        return resultado;
    }
}