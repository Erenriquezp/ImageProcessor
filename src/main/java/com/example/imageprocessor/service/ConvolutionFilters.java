package com.example.imageprocessor.service;

import com.example.imageprocessor.domain.ConvolutionKernel;

import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;

final class ConvolutionFilters {

    private ConvolutionFilters() {
    }

    static BufferedImage convolution(BufferedImage original, ConvolutionKernel kernelType) {
        Kernel kernel = new Kernel(3, 3, kernelType.kernelValues());
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        return op.filter(original, null);
    }
}

