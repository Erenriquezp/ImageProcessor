package io.github.erenriquezp.rora.lab.session;

import io.github.erenriquezp.rora.app.session.*;
import io.github.erenriquezp.rora.domain.ColorSpaceType;
import io.github.erenriquezp.rora.domain.ConvolutionKernel;
import io.github.erenriquezp.rora.domain.DepthSource;
import io.github.erenriquezp.rora.domain.EqualizeMode;
import io.github.erenriquezp.rora.domain.FragmentBlendMode;
import io.github.erenriquezp.rora.domain.LogicOpType;
import io.github.erenriquezp.rora.domain.StencilPattern;
import io.github.erenriquezp.rora.domain.StretchMode;
import io.github.erenriquezp.rora.domain.TextureFilterMode;

/**
 * Instantánea inmutable de los parámetros de la UI (los mismos campos y tipos que recibía {@code
 * processFilter}). La lee {@code ImageProcessorApp} en el hilo FX y la traduce a un paso de receta
 * {@link FilterSpecs#of}.
 */
public record FilterParams(
    int brightness,
    float saturation,
    float valueFactor,
    int retroLevels,
    int retro2Levels,
    boolean r2r,
    boolean r2g,
    boolean r2b,
    int grayQuantLevels,
    int threshold,
    int alpha,
    int tintR,
    int tintG,
    int tintB,
    StretchMode stretchMode,
    ConvolutionKernel kernel,
    DepthSource depthSource,
    int depthThreshold,
    int pixelBlockSize,
    float textureScale,
    TextureFilterMode textureFilter,
    int alphaTestThreshold,
    boolean alphaTestLuminance,
    int multisampleSize,
    StencilPattern stencilPattern,
    FragmentBlendMode fragmentBlendMode,
    float fragmentBlendAlpha,
    LogicOpType logicOp,
    int logicMask,
    int planeR,
    int planeG,
    int planeB,
    int fogR,
    int fogG,
    int fogB,
    EqualizeMode equalizeMode,
    float gainR,
    float gainG,
    float gainB,
    float lerpFactor,
    float extrapolateFactor,
    float scale,
    float bias,
    int pointThreshold,
    int softThreshold,
    float pointSaturation,
    float hueDegrees,
    ColorSpaceType colorSpace,
    float equalizeIntensity,
    boolean equalizeMarkBurned,
    boolean equalizeMarkDark) {

  /** Los mismos parámetros sin las marcas de la ecualización ("Aplicar" no las consolida). */
  public FilterParams withNoHighlights() {
    return new FilterParams(
        brightness,
        saturation,
        valueFactor,
        retroLevels,
        retro2Levels,
        r2r,
        r2g,
        r2b,
        grayQuantLevels,
        threshold,
        alpha,
        tintR,
        tintG,
        tintB,
        stretchMode,
        kernel,
        depthSource,
        depthThreshold,
        pixelBlockSize,
        textureScale,
        textureFilter,
        alphaTestThreshold,
        alphaTestLuminance,
        multisampleSize,
        stencilPattern,
        fragmentBlendMode,
        fragmentBlendAlpha,
        logicOp,
        logicMask,
        planeR,
        planeG,
        planeB,
        fogR,
        fogG,
        fogB,
        equalizeMode,
        gainR,
        gainG,
        gainB,
        lerpFactor,
        extrapolateFactor,
        scale,
        bias,
        pointThreshold,
        softThreshold,
        pointSaturation,
        hueDegrees,
        colorSpace,
        equalizeIntensity,
        false,
        false);
  }
}
