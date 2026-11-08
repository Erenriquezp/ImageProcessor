package io.github.erenriquezp.rora.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.testsupport.EquivalenceImages;
import io.github.erenriquezp.rora.testsupport.EquivalenceImages.Content;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * El puente {@link AwtImg} es exacto respecto a {@code getRGB}/{@code setRGB} en todos los tipos.
 */
@Tag("equivalence")
class AwtImgTest {

  static final List<Integer> ALL_TYPES =
      List.of(
          BufferedImage.TYPE_INT_ARGB,
          BufferedImage.TYPE_INT_RGB,
          BufferedImage.TYPE_INT_BGR,
          BufferedImage.TYPE_3BYTE_BGR,
          BufferedImage.TYPE_4BYTE_ABGR,
          BufferedImage.TYPE_4BYTE_ABGR_PRE,
          BufferedImage.TYPE_INT_ARGB_PRE,
          BufferedImage.TYPE_BYTE_GRAY,
          BufferedImage.TYPE_USHORT_GRAY,
          BufferedImage.TYPE_BYTE_INDEXED,
          BufferedImage.TYPE_BYTE_BINARY,
          BufferedImage.TYPE_USHORT_565_RGB,
          BufferedImage.TYPE_USHORT_555_RGB);

  static Stream<Object[]> typesAndSizes() {
    int[][] sizes = {{1, 1}, {3, 5}, {64, 48}};
    return ALL_TYPES.stream()
        .flatMap(
            t ->
                Stream.of(sizes)
                    .flatMap(
                        s ->
                            Stream.of(Content.values())
                                .map(
                                    c ->
                                        new Object[] {
                                          EquivalenceImages.typeName(t), t, s[0], s[1], c
                                        })));
  }

  private static void assertSameAsGetRgb(BufferedImage bi, Img img) {
    assertThat(img.width()).isEqualTo(bi.getWidth());
    assertThat(img.height()).isEqualTo(bi.getHeight());
    for (int y = 0; y < bi.getHeight(); y++) {
      for (int x = 0; x < bi.getWidth(); x++) {
        if (img.argb(x, y) != bi.getRGB(x, y)) {
          throw new AssertionError(
              String.format(
                  "(%d,%d): toImg=%08x getRGB=%08x", x, y, img.argb(x, y), bi.getRGB(x, y)));
        }
      }
    }
  }

  @ParameterizedTest(name = "{0} {2}x{3} {4}")
  @MethodSource("typesAndSizes")
  void toImgEqualsPerPixelGetRgb(String name, int type, int w, int h, Content c) {
    BufferedImage bi =
        EquivalenceImages.create(type, w, h, c, EquivalenceImages.seed(type, w, h, c));
    assertSameAsGetRgb(bi, AwtImg.toImg(bi));
  }

  @Test
  void toImgOfSubimageAndOfIntRgbWithGarbageInTheHighByte() {
    BufferedImage big =
        EquivalenceImages.create(BufferedImage.TYPE_INT_ARGB, 20, 10, Content.NOISE, 1);
    BufferedImage sub = big.getSubimage(3, 2, 11, 7);
    assertSameAsGetRgb(sub, AwtImg.toImg(sub));

    BufferedImage rgb = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
    int[] raw = ((DataBufferInt) rgb.getRaster().getDataBuffer()).getData();
    for (int i = 0; i < raw.length; i++) {
      raw[i] = 0x12000000 | (i * 0x0A0B0C); // byte alto distinto de 0 escrito a mano
    }
    assertSameAsGetRgb(rgb, AwtImg.toImg(rgb));
  }

  @Test
  void toImgDoesNotAliasTheSourceBuffer() {
    BufferedImage bi =
        EquivalenceImages.create(BufferedImage.TYPE_INT_ARGB, 4, 4, Content.NOISE, 2);
    Img img = AwtImg.toImg(bi);
    int before = img.argb(0, 0);
    bi.setRGB(0, 0, ~before);
    assertThat(img.argb(0, 0)).isEqualTo(before);
  }

  @Test
  void toBufferedImageEqualsLegacySetRgb() {
    for (Content c : Content.values()) {
      BufferedImage src = EquivalenceImages.create(BufferedImage.TYPE_INT_ARGB, 33, 17, c, 3);
      Img img = AwtImg.toImg(src);
      BufferedImage viaBridge = AwtImg.toBufferedImage(img);
      BufferedImage viaSetRgb = new BufferedImage(33, 17, BufferedImage.TYPE_INT_ARGB);
      for (int y = 0; y < 17; y++) {
        for (int x = 0; x < 33; x++) {
          viaSetRgb.setRGB(x, y, img.argb(x, y));
        }
      }
      assertThat(viaBridge.getType()).isEqualTo(BufferedImage.TYPE_INT_ARGB);
      assertThat(((DataBufferInt) viaBridge.getRaster().getDataBuffer()).getData())
          .isEqualTo(((DataBufferInt) viaSetRgb.getRaster().getDataBuffer()).getData());
      assertThat(EquivalenceImages.pixels(viaBridge)).isEqualTo(img.toArray());
    }
  }

  @Test
  void registryLoadedFromTheAppModuleSeesThePortedOperations() {
    assertThat(AwtImgTest.class.getModule().getName()).isEqualTo("io.github.erenriquezp.rora.lab");
    assertThat(CoreBridge.REGISTRY.ids())
        .contains("point.saturation", "raster.pixelate", "tone.equalize");
  }
}
