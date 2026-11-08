package io.github.erenriquezp.rora.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.OpContext;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Optional;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** {@link Analysis} y {@link SourceFormat} (puentes de la fase 2 en {@code service}). */
@Tag("equivalence")
class SessionServicesTest {

  private static BufferedImage noise(int type, int w, int h, long seed) {
    SplittableRandom r = new SplittableRandom(seed);
    BufferedImage bi = new BufferedImage(w, h, type);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        bi.setRGB(x, y, r.nextInt());
      }
    }
    return bi;
  }

  private static int[] rgb(BufferedImage b) {
    return b.getRGB(0, 0, b.getWidth(), b.getHeight(), null, 0, b.getWidth());
  }

  @Test
  void histogramChartOnImgEqualsTheFacade() {
    BufferedImage bi = noise(BufferedImage.TYPE_INT_ARGB, 50, 40, 1);
    BufferedImage viaFacade = ImageProcessor.generateHistogram(bi);
    BufferedImage viaImg = Analysis.histogramChart(AwtImg.toImg(bi), OpContext.sequential());
    assertThat(rgb(viaImg)).isEqualTo(rgb(viaFacade));
  }

  @Test
  void materializeRebuildsEveryReconstructibleTypeExactly() {
    for (String fmt : SourceFormat.IDS) {
      if (fmt.equals(SourceFormat.NATIVE) || fmt.endsWith("-gray") || fmt.endsWith("-default")) {
        continue;
      }
      int type =
          fmt.equals(SourceFormat.ARGB) ? BufferedImage.TYPE_INT_ARGB : SourceFormat.typeOf(fmt);
      BufferedImage src = noise(type, 9, 7, 2);
      Img img = AwtImg.toImg(src);
      BufferedImage back = SourceFormat.materialize(img, fmt, SourceFormat.NativeSource.NONE);
      assertThat(back.getType()).as(fmt).isEqualTo(type);
      assertThat(rgb(back)).as(fmt).isEqualTo(rgb(src));
      assertThat(SourceFormat.of(back)).isEqualTo(fmt);
    }
  }

  @Test
  void nativeUsesTheOriginalForTheSourceAndItsColorModelOtherwise() {
    BufferedImage gray = noise(BufferedImage.TYPE_BYTE_GRAY, 8, 6, 3);
    Img source = AwtImg.toImg(gray);
    SourceFormat.NativeSource ns =
        new SourceFormat.NativeSource() {
          @Override
          public Optional<BufferedImage> nativeFor(Img img) {
            return img == source ? Optional.of(gray) : Optional.empty();
          }

          @Override
          public Optional<BufferedImage> template() {
            return Optional.of(gray);
          }
        };
    assertThat(SourceFormat.materialize(source, SourceFormat.NATIVE, ns)).isSameAs(gray);
    Img other = Img.copyOf(8, 6, source.toArray());
    BufferedImage approx = SourceFormat.materialize(other, SourceFormat.NATIVE, ns);
    assertThat(approx).isNotSameAs(gray);
    assertThat(approx.getColorModel()).isEqualTo(gray.getColorModel());
    assertThatThrownBy(
            () ->
                SourceFormat.materialize(
                    other, SourceFormat.NATIVE, SourceFormat.NativeSource.NONE))
        .isInstanceOf(IllegalStateException.class);
    assertThat(SourceFormat.NativeSource.NONE.nativeFor(source)).isEmpty();
    assertThatThrownBy(() -> SourceFormat.typeOf("argb"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void formatsOfEveryAwtType() {
    assertThat(SourceFormat.of(new BufferedImage(1, 1, BufferedImage.TYPE_INT_BGR)))
        .isEqualTo("int-bgr");
    assertThat(SourceFormat.of(new BufferedImage(1, 1, BufferedImage.TYPE_USHORT_565_RGB)))
        .isEqualTo("ushort-565");
    assertThat(SourceFormat.of(new BufferedImage(1, 1, BufferedImage.TYPE_USHORT_555_RGB)))
        .isEqualTo("ushort-555");
    assertThat(SourceFormat.of(new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR_PRE)))
        .isEqualTo("4byte-abgr-pre");
    assertThat(SourceFormat.of(new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_BINARY)))
        .isEqualTo("byte-binary-default");
    assertThat(SourceFormat.of(new BufferedImage(1, 1, BufferedImage.TYPE_USHORT_GRAY)))
        .isEqualTo("native");
    assertThat(SourceFormat.needsNative("native")).isTrue();
    assertThat(SourceFormat.afterConvolution("argb", Optional.empty())).isEqualTo("argb");
    assertThat(SourceFormat.afterConvolution("byte-binary-default", Optional.empty()))
        .isIn("int-rgb", "argb");
    assertThat(Arrays.asList(SourceFormat.IDS.toArray())).hasSize(13);
  }
}
