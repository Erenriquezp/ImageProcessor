package io.github.erenriquezp.rora.app.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.app.session.PipelineSupport.Step;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.domain.ConvolutionKernel;
import io.github.erenriquezp.rora.domain.FilterType;
import io.github.erenriquezp.rora.service.ImageBridge;
import io.github.erenriquezp.rora.service.SourceFormat;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * La tabla de §3.1 del diseño de la fase 2 como test: la convolución depende del tipo de imagen y
 * el formato de origen la reproduce. La cadena convolución→convolución sobre gris es una desviación
 * conocida (la salida gris intermedia no se reconstruye sin pérdida); se fija su tamaño actual.
 */
class ConvolutionFormatTest {

  private static FilterParams kernel(ConvolutionKernel k) {
    FilterParams p = SessionFixtures.randomParams(new SplittableRandom(2));
    return new FilterParams(
        p.brightness(),
        p.saturation(),
        p.valueFactor(),
        p.retroLevels(),
        p.retro2Levels(),
        p.r2r(),
        p.r2g(),
        p.r2b(),
        p.grayQuantLevels(),
        p.threshold(),
        p.alpha(),
        p.tintR(),
        p.tintG(),
        p.tintB(),
        p.stretchMode(),
        k,
        p.depthSource(),
        p.depthThreshold(),
        p.pixelBlockSize(),
        p.textureScale(),
        p.textureFilter(),
        p.alphaTestThreshold(),
        p.alphaTestLuminance(),
        p.multisampleSize(),
        p.stencilPattern(),
        p.fragmentBlendMode(),
        p.fragmentBlendAlpha(),
        p.logicOp(),
        p.logicMask(),
        p.planeR(),
        p.planeG(),
        p.planeB(),
        p.fogR(),
        p.fogG(),
        p.fogB(),
        p.equalizeMode(),
        p.gainR(),
        p.gainG(),
        p.gainB(),
        p.lerpFactor(),
        p.extrapolateFactor(),
        p.scale(),
        p.bias(),
        p.pointThreshold(),
        p.softThreshold(),
        p.pointSaturation(),
        p.hueDegrees(),
        p.colorSpace(),
        p.equalizeIntensity(),
        false,
        false);
  }

  @Test
  @Tag("equivalence")
  void formatOfLoadedTypes() {
    assertThat(SourceFormat.of(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)))
        .isEqualTo("argb");
    assertThat(SourceFormat.of(new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR)))
        .isEqualTo("argb");
    assertThat(SourceFormat.of(new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR)))
        .isEqualTo("3byte-bgr");
    assertThat(SourceFormat.of(new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_INDEXED)))
        .isEqualTo("byte-indexed-default");
    assertThat(SourceFormat.of(SessionFixtures.image("BYTE_INDEXED_CUSTOM", 2, 2, 1)))
        .isEqualTo("native");
    assertThat(SourceFormat.of(new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY)))
        .isEqualTo("native");
    assertThat(SourceFormat.afterConvolution("3byte-bgr", Optional.empty())).isEqualTo("3byte-bgr");
    assertThat(
            SourceFormat.afterConvolution(
                "native", Optional.of(new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY))))
        .isEqualTo("byte-gray");
    assertThat(
            SourceFormat.afterConvolution(
                "native", Optional.of(SessionFixtures.image("BYTE_INDEXED_CUSTOM", 2, 2, 1))))
        .isIn("int-rgb", "argb");
  }

  /** Sin el formato, la convolución de bordes sobre un JPEG (3BYTE_BGR) saldría transparente. */
  @Test
  @Tag("equivalence")
  void edgesOnAnOpaqueSourceStayOpaque() {
    BufferedImage jpegLike = SessionFixtures.image("3BYTE_BGR", 30, 20, 3);
    List<Step> steps = List.of(new Step(FilterType.CONVOLUTION, kernel(ConvolutionKernel.EDGES)));
    PipelineSupport.Run run =
        PipelineSupport.pipeline(jpegLike, steps, true, OpContext.sequential());
    assertThat(run.image().argb(10, 10) >>> 24).isEqualTo(255);
    BufferedImage expected = PipelineSupport.reference(jpegLike, steps);
    assertThat(PipelineSupport.same(expected, run.image())).isTrue();
    // Lo que daría normalizar a ARGB (descartado en el diseño):
    BufferedImage argb = ImageBridge.toBufferedImage(ImageBridge.toImg(jpegLike));
    assertThat(SessionFixtures.sameRgb(expected, PipelineSupport.reference(argb, steps))).isFalse();
  }

  @Test
  @Tag("equivalence")
  void convolutionChainsAreExactOnReconstructibleTypes() {
    for (String kind :
        new String[] {"INT_ARGB", "3BYTE_BGR", "4BYTE_ABGR", "INT_RGB", "BYTE_INDEXED_CUSTOM"}) {
      for (ConvolutionKernel k : ConvolutionKernel.values()) {
        BufferedImage img = SessionFixtures.image(kind, 25, 19, 4);
        List<Step> steps =
            List.of(
                new Step(FilterType.CONVOLUTION, kernel(k)),
                new Step(FilterType.CONVOLUTION, kernel(ConvolutionKernel.SHARPEN)),
                new Step(FilterType.CONVOLUTION, kernel(ConvolutionKernel.EDGES)));
        PipelineSupport.Run run =
            PipelineSupport.pipeline(img, steps, false, OpContext.sequential());
        assertThat(PipelineSupport.same(PipelineSupport.reference(img, steps), run.image()))
            .as(kind + " " + k + " " + run.effective())
            .isTrue();
      }
    }
  }

  /**
   * Desviación conocida (fase 2, decisión 1b): convolución→convolución sobre gris de 8 bits. Se
   * corrige al reescribir la convolución en el núcleo (fase 3). Fija el número de píxeles
   * distintos.
   */
  @Test
  @Tag("known-deviation")
  void convolutionAfterConvolutionOnGrayDeviates() {
    BufferedImage gray = SessionFixtures.image("BYTE_GRAY", 25, 19, 5);
    List<Step> steps =
        List.of(
            new Step(FilterType.CONVOLUTION, kernel(ConvolutionKernel.BLUR)),
            new Step(FilterType.CONVOLUTION, kernel(ConvolutionKernel.SHARPEN)));
    PipelineSupport.Run run = PipelineSupport.pipeline(gray, steps, false, OpContext.sequential());
    BufferedImage expected = PipelineSupport.reference(gray, steps);
    BufferedImage actual = ImageBridge.toBufferedImage(run.image());
    int differing = 0;
    for (int y = 0; y < 19; y++) {
      for (int x = 0; x < 25; x++) {
        if (expected.getRGB(x, y) != actual.getRGB(x, y)) {
          differing++;
        }
      }
    }
    assertThat(run.effective().get(1).params().getEnum("sourceFormat")).isEqualTo("byte-gray");
    // El primer paso sí es exacto (usa el original): solo difiere la segunda convolución.
    assertThat(differing).isEqualTo(KNOWN_GRAY_CHAIN_DIFF);
  }

  /** Medido el 2026-11-08 con JDK 25.0.5 (25×19, semilla 5, blur → sharpen). */
  static final int KNOWN_GRAY_CHAIN_DIFF = 223;
}
