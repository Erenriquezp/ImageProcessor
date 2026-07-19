package com.example.imageprocessor.service;

import java.awt.image.BufferedImage;

public class BufferAcumulacion_LOAD {

    public enum Mode {
        ADD, ACUM, MULT
    }

    /**
     * Aplica el filtro de buffer de acumulación según el modo seleccionado.
     *
     * @param original     imagen de entrada
     * @param mode         tipo de acumulación (ADD, ACUM, MULT)
     * @param samples      número de muestras (capas desplazadas a acumular)
     * @param displacement desplazamiento en píxeles entre muestras sucesivas
     * @return imagen procesada
     */
    public static BufferedImage applyAccumulation(BufferedImage original, Mode mode, int samples, int displacement) {
        return switch (mode) {
            case ADD -> aplicarADD(original, samples, displacement);
            case ACUM -> aplicarACUM(original, samples, displacement);
            case MULT -> aplicarMULT(original, samples, displacement);
        };
    }

    /**
     * Acumulación aditiva: suma la imagen con copias desplazadas horizontalmente,
     * promediando uniformemente. Produce un efecto de motion blur lineal.
     * Cada muestra contribuye con peso igual (1/N).
     */
    public static BufferedImage aplicarADD(BufferedImage imagen, int samples, int displacement) {
        int ancho = imagen.getWidth();
        int alto = imagen.getHeight();
        int muestras = Math.max(2, samples);
        int desp = Math.max(1, displacement);

        float[] bufferR = new float[ancho * alto];
        float[] bufferG = new float[ancho * alto];
        float[] bufferB = new float[ancho * alto];
        float[] bufferA = new float[ancho * alto];

        BufferedImage resultado = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);

        for (int i = 0; i < muestras; i++) {
            int offset = i * desp;

            for (int y = 0; y < alto; y++) {
                for (int x = 0; x < ancho; x++) {
                    int origenX = Math.min(ancho - 1, Math.max(0, x - offset));
                    int index = y * ancho + x;

                    int pixel = imagen.getRGB(origenX, y);

                    bufferA[index] += (pixel >> 24) & 0xFF;
                    bufferR[index] += (pixel >> 16) & 0xFF;
                    bufferG[index] += (pixel >> 8) & 0xFF;
                    bufferB[index] += pixel & 0xFF;
                }
            }
        }

        // Normalizar dividiendo entre el número de muestras (peso uniforme)
        float invN = 1.0f / muestras;
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                int index = y * ancho + x;

                int a = PixelMath.clamp((int) (bufferA[index] * invN));
                int r = PixelMath.clamp((int) (bufferR[index] * invN));
                int g = PixelMath.clamp((int) (bufferG[index] * invN));
                int b = PixelMath.clamp((int) (bufferB[index] * invN));

                resultado.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        return resultado;
    }

    /**
     * Acumulación ponderada: suma copias desplazadas con pesos exponencialmente
     * decrecientes (0.85^i), simulando un trail o estela con desvanecimiento.
     * La primera muestra (i=0) tiene peso 1.0, las siguientes se atenúan.
     * El resultado se normaliza por la suma total de pesos.
     */
    public static BufferedImage aplicarACUM(BufferedImage imagen, int samples, int displacement) {
        int ancho = imagen.getWidth();
        int alto = imagen.getHeight();
        int muestras = Math.max(2, samples);
        int desp = Math.max(1, displacement);

        float[] bufferR = new float[ancho * alto];
        float[] bufferG = new float[ancho * alto];
        float[] bufferB = new float[ancho * alto];
        float[] bufferA = new float[ancho * alto];

        BufferedImage resultado = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);

        // Acumular pesos para normalización
        float sumaPesos = 0f;

        for (int i = 0; i < muestras; i++) {
            int offset = i * desp;
            float peso = (float) Math.pow(0.85, i);
            sumaPesos += peso;

            for (int y = 0; y < alto; y++) {
                for (int x = 0; x < ancho; x++) {
                    int origenX = Math.min(ancho - 1, Math.max(0, x - offset));
                    int index = y * ancho + x;

                    int pixel = imagen.getRGB(origenX, y);

                    bufferA[index] += ((pixel >> 24) & 0xFF) * peso;
                    bufferR[index] += ((pixel >> 16) & 0xFF) * peso;
                    bufferG[index] += ((pixel >> 8) & 0xFF) * peso;
                    bufferB[index] += (pixel & 0xFF) * peso;
                }
            }
        }

        // Normalizar dividiendo entre la suma de pesos
        float invPesos = 1.0f / sumaPesos;
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                int index = y * ancho + x;

                int a = PixelMath.clamp((int) (bufferA[index] * invPesos));
                int r = PixelMath.clamp((int) (bufferR[index] * invPesos));
                int g = PixelMath.clamp((int) (bufferG[index] * invPesos));
                int b = PixelMath.clamp((int) (bufferB[index] * invPesos));

                resultado.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        return resultado;
    }

    /**
     * Acumulación multiplicativa: combina copias desplazadas multiplicando sus
     * valores normalizados [0,1]. Cada muestra adicional oscurece las zonas
     * donde los píxeles desplazados no son blancos, creando un efecto de
     * viñeteo/sombra acumulativo.
     */
    public static BufferedImage aplicarMULT(BufferedImage imagen, int samples, int displacement) {
        int ancho = imagen.getWidth();
        int alto = imagen.getHeight();
        int muestras = Math.max(2, samples);
        int desp = Math.max(1, displacement);

        // Inicializar buffers con 1.0 (identidad multiplicativa)
        float[] bufferR = new float[ancho * alto];
        float[] bufferG = new float[ancho * alto];
        float[] bufferB = new float[ancho * alto];
        float[] bufferA = new float[ancho * alto];

        for (int i = 0; i < ancho * alto; i++) {
            bufferR[i] = 1.0f;
            bufferG[i] = 1.0f;
            bufferB[i] = 1.0f;
            bufferA[i] = 1.0f;
        }

        BufferedImage resultado = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);

        for (int i = 0; i < muestras; i++) {
            int offset = i * desp;

            for (int y = 0; y < alto; y++) {
                for (int x = 0; x < ancho; x++) {
                    int origenX = Math.min(ancho - 1, Math.max(0, x - offset));
                    int index = y * ancho + x;

                    int pixel = imagen.getRGB(origenX, y);

                    // Multiplicar valores normalizados [0,1]
                    bufferA[index] *= ((pixel >> 24) & 0xFF) / 255.0f;
                    bufferR[index] *= ((pixel >> 16) & 0xFF) / 255.0f;
                    bufferG[index] *= ((pixel >> 8) & 0xFF) / 255.0f;
                    bufferB[index] *= (pixel & 0xFF) / 255.0f;
                }
            }
        }

        // Reconvertir de [0,1] a [0,255]
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                int index = y * ancho + x;

                int a = PixelMath.clamp((int) (bufferA[index] * 255));
                int r = PixelMath.clamp((int) (bufferR[index] * 255));
                int g = PixelMath.clamp((int) (bufferG[index] * 255));
                int b = PixelMath.clamp((int) (bufferB[index] * 255));

                resultado.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        return resultado;
    }
}