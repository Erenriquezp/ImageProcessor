package io.github.erenriquezp.rora.imaging.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.DecodeOptions;
import io.github.erenriquezp.rora.core.io.DecodedImage;
import io.github.erenriquezp.rora.core.io.ExportSettings;
import io.github.erenriquezp.rora.core.io.FormatId;
import io.github.erenriquezp.rora.core.io.ImageInfo;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import io.github.erenriquezp.rora.core.io.ImagingException;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Decodificación: política de color de entrada. */
@Tag("property")
class DecodeTest {

  @TempDir Path dir;
  private final ImageIoDecoder decoder = new ImageIoDecoder();

  private Path file(String name, byte[] data) throws Exception {
    Path p = dir.resolve(name);
    Files.write(p, data);
    return p;
  }

  private DecodedImage decode(Path p) throws Exception {
    return decoder.decode(p, DecodeOptions.DEFAULT, CancellationToken.none(), Progress.NONE);
  }

  private static List<String> keys(DecodedImage d) {
    return d.notes().stream().map(DecodedImage.Note::key).toList();
  }

  private static void assertNear(int argb, int[] expected, int tolerance) {
    assertThat(argb >> 16 & 255)
        .isCloseTo(expected[0], org.assertj.core.data.Offset.offset(tolerance));
    assertThat(argb >> 8 & 255)
        .isCloseTo(expected[1], org.assertj.core.data.Offset.offset(tolerance));
    assertThat(argb & 255).isCloseTo(expected[2], org.assertj.core.data.Offset.offset(tolerance));
  }

  private static BufferedImage grayRamp() {
    BufferedImage g = new BufferedImage(256, 8, BufferedImage.TYPE_BYTE_GRAY);
    for (int y = 0; y < 8; y++) {
      for (int x = 0; x < 256; x++) {
        g.getRaster().setSample(x, y, 0, x);
      }
    }
    return g;
  }

  @ParameterizedTest
  @ValueSource(strings = {"png", "tiff", "bmp"})
  void grayLosslessKeepsEverySampleExactly(String fmt) throws Exception {
    Img out = decode(file("g." + fmt, Fixtures.encode(grayRamp(), fmt))).image();
    for (int x = 0; x < 256; x++) {
      assertThat(out.argb(x, 3)).isEqualTo(0xFF000000 | x << 16 | x << 8 | x);
    }
  }

  @Test
  void grayJpegIsNotBrightened() throws Exception {
    Img out = decode(file("g.jpg", Fixtures.encode(grayRamp(), "jpg"))).image();
    for (int x = 8; x < 248; x += 8) {
      assertThat(out.argb(x, 3) & 255).isCloseTo(x, org.assertj.core.data.Offset.offset(12));
    }
  }

  private static BufferedImage rgb16() {
    ColorModel cm =
        new ComponentColorModel(
            ColorSpace.getInstance(ColorSpace.CS_sRGB),
            false,
            false,
            Transparency.OPAQUE,
            DataBuffer.TYPE_USHORT);
    WritableRaster r = cm.createCompatibleWritableRaster(256, 256);
    for (int y = 0; y < 256; y++) {
      for (int x = 0; x < 256; x++) {
        int v = y * 256 + x;
        r.setPixel(x, y, new int[] {v, v, v});
      }
    }
    return new BufferedImage(cm, r, false, null);
  }

  @ParameterizedTest
  @ValueSource(strings = {"png", "tiff"})
  void sixteenBitRgbRoundsEverySample(String fmt) throws Exception {
    DecodedImage d = decode(file("c." + fmt, Fixtures.encode(rgb16(), fmt)));
    for (int y = 0; y < 256; y++) {
      for (int x = 0; x < 256; x++) {
        int v = y * 256 + x;
        assertThat(d.image().argb(x, y) & 255).isEqualTo((v * 255 + 32767) / 65535);
      }
    }
    assertThat(keys(d)).contains("io.note.16bit");
    assertThat(d.info().bitsPerSample()).isEqualTo(16);
  }

  @ParameterizedTest
  @ValueSource(strings = {"png", "tiff"})
  void sixteenBitGrayRoundsWithoutBrightening(String fmt) throws Exception {
    BufferedImage g = new BufferedImage(256, 256, BufferedImage.TYPE_USHORT_GRAY);
    for (int y = 0; y < 256; y++) {
      for (int x = 0; x < 256; x++) {
        g.getRaster().setSample(x, y, 0, y * 256 + x);
      }
    }
    DecodedImage d = decode(file("g16." + fmt, Fixtures.encode(g, fmt)));
    assertThat(d.image().argb(0x00, 0x80) & 255).isEqualTo(128);
    assertThat(d.image().argb(0xFF, 0xFF) & 255).isEqualTo(255);
    for (int y = 0; y < 256; y++) {
      for (int x = 0; x < 256; x++) {
        assertThat(d.image().argb(x, y) & 255).isEqualTo(((y * 256 + x) * 255 + 32767) / 65535);
      }
    }
  }

  @Test
  void sixteenBitRgbaRoundsColourAndAlpha() throws Exception {
    ColorModel cm =
        new ComponentColorModel(
            ColorSpace.getInstance(ColorSpace.CS_sRGB),
            true,
            false,
            Transparency.TRANSLUCENT,
            DataBuffer.TYPE_USHORT);
    WritableRaster r = cm.createCompatibleWritableRaster(2, 1);
    r.setPixel(0, 0, new int[] {0x80FF, 0x0080, 0xFF00, 0xFFFF});
    r.setPixel(1, 0, new int[] {0x1234, 0x5678, 0x9ABC, 0x8000});
    Img out =
        decode(file("a16.png", Fixtures.encode(new BufferedImage(cm, r, false, null), "png")))
            .image();
    assertThat(out.argb(0, 0)).isEqualTo(0xFF80_00_FE);
    assertThat(out.argb(1, 0) >>> 24).isEqualTo(128);
  }

  @Test
  void paletteKeepsItsTransparency() throws Exception {
    byte[] r = {10, 20, 30};
    byte[] g = {40, 50, 60};
    byte[] b = {70, 80, 90};
    byte[] a = {(byte) 255, 0, (byte) 128};
    IndexColorModel icm = new IndexColorModel(8, 3, r, g, b, a);
    BufferedImage img = new BufferedImage(3, 1, BufferedImage.TYPE_BYTE_INDEXED, icm);
    for (int x = 0; x < 3; x++) {
      img.getRaster().setSample(x, 0, 0, x);
    }
    DecodedImage d = decode(file("p.png", Fixtures.encode(img, "png")));
    assertThat(d.info().kind()).isEqualTo(ImageInfo.Kind.INDEXED);
    assertThat(d.image().argb(0, 0)).isEqualTo(0xFF0A2846);
    assertThat(d.image().argb(1, 0) >>> 24).isZero();
    assertThat(d.image().argb(2, 0) >>> 24).isEqualTo(128);
  }

  private static byte[] inks(int w, int h, int[][] cells, boolean ycck) {
    byte[] p = new byte[w * h * 4];
    int cell = w / cells.length;
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int[] k = cells[Math.min(cells.length - 1, x / cell)];
        int i = (y * w + x) * 4;
        if (ycck) {
          double rr = k[0];
          double gg = k[1];
          double bb = k[2];
          p[i] = clamp(0.299 * rr + 0.587 * gg + 0.114 * bb);
          p[i + 1] = clamp(-0.168736 * rr - 0.331264 * gg + 0.5 * bb + 128);
          p[i + 2] = clamp(0.5 * rr - 0.418688 * gg - 0.081312 * bb + 128);
          p[i + 3] = (byte) (255 - k[3]);
        } else {
          for (int c = 0; c < 4; c++) {
            p[i + c] = (byte) (255 - k[c]);
          }
        }
      }
    }
    return p;
  }

  private static byte clamp(double v) {
    return (byte) Math.max(0, Math.min(255, Math.round(v)));
  }

  private static final int[][] CELLS = {
    {0, 0, 0, 0}, {0, 255, 255, 0}, {0, 0, 0, 128}, {255, 0, 0, 0}
  };

  private static int[] formula(int[] k) {
    int inv = 255 - k[3];
    return new int[] {
      ((255 - k[0]) * inv + 127) / 255,
      ((255 - k[1]) * inv + 127) / 255,
      ((255 - k[2]) * inv + 127) / 255
    };
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void cmykWithoutProfileFollowsTheFormula(int transform) throws Exception {
    byte[] raw = inks(64, 64, CELLS, transform == 2);
    DecodedImage d = decode(file("c.jpg", Fixtures.fourBandJpeg(raw, 64, 64, transform)));
    for (int i = 0; i < CELLS.length; i++) {
      assertNear(d.image().argb(i * 16 + 8, 32), formula(CELLS[i]), 3);
    }
    assertThat(keys(d)).containsExactly("io.note.cmyk_naive");
    assertThat(d.info().kind()).isEqualTo(ImageInfo.Kind.CMYK);
  }

  @Test
  void cmykUsesTheEmbeddedProfile() throws Exception {
    byte[] raw = inks(64, 64, CELLS, false);
    byte[] icc = Fixtures.cmykProfile("Prueba CMYK");
    byte[] jpg = Fixtures.fourBandJpeg(raw, 64, 64, 0);
    DecodedImage d = decode(file("cp.jpg", Fixtures.insertJpeg(jpg, Fixtures.iccSeg(icc))));
    assertThat(keys(d)).containsExactly("io.note.converted");
    assertThat(d.notes().get(0).args()).containsExactly("Prueba CMYK");
    assertNear(d.image().argb(3 * 16 + 8, 32), new int[] {10, 130, 200}, 12);
    assertNear(d.image().argb(8, 32), new int[] {255, 255, 255}, 3);
  }

  private static final int[][] P3_COLOURS = {
    {200, 100, 50}, {30, 180, 90}, {128, 128, 128}, {250, 20, 20}, {20, 250, 20}, {60, 80, 220}
  };

  private static Img patches() {
    int cell = 32;
    int[] d = new int[P3_COLOURS.length * cell * cell];
    for (int y = 0; y < cell; y++) {
      for (int x = 0; x < P3_COLOURS.length * cell; x++) {
        int[] c = P3_COLOURS[x / cell];
        d[y * P3_COLOURS.length * cell + x] = 0xFF000000 | c[0] << 16 | c[1] << 8 | c[2];
      }
    }
    return Img.adopt(P3_COLOURS.length * cell, cell, d);
  }

  private void assertP3(DecodedImage d, int tolerance) {
    for (int i = 0; i < P3_COLOURS.length; i++) {
      int[] c = P3_COLOURS[i];
      assertNear(d.image().argb(i * 32 + 16, 16), Fixtures.analytic(c[0], c[1], c[2]), tolerance);
    }
    assertThat(keys(d)).containsExactly("io.note.converted");
    assertThat(d.notes().get(0).args()).containsExactly("Pantalla P3");
  }

  @Test
  void pngProfileIsConvertedWithinTwoLevelsOfTheAnalyticResult() throws Exception {
    byte[] icc = Fixtures.rgbProfile("Pantalla P3", Fixtures.P3_D50);
    byte[] png =
        Fixtures.insertPng(Fixtures.encode(Fixtures.toRgb(patches()), "png"), Fixtures.iccp(icc));
    assertP3(decode(file("p3.png", png)), 2);
  }

  @Test
  void jpegProfileIsConvertedOnce() throws Exception {
    byte[] icc = Fixtures.rgbProfile("Pantalla P3", Fixtures.P3_D50);
    byte[] jpg = Fixtures.insertJpeg(Fixtures.bestJpeg(dir, patches()), Fixtures.iccSeg(icc));
    assertP3(decode(file("p3.jpg", jpg)), 2);
  }

  @Test
  void tiffProfileIsConvertedWithinTwoLevelsOfTheAnalyticResult() throws Exception {
    byte[] icc = Fixtures.rgbProfile("Pantalla P3", Fixtures.P3_D50);
    assertP3(decode(file("p3.tif", Fixtures.tiffWithProfile(patches(), icc))), 2);
  }

  @Test
  void srgbProfileChangesNothingAndIsNotReported() throws Exception {
    byte[] icc = Fixtures.rgbProfile("Otro sRGB", Fixtures.SRGB_D50);
    byte[] png =
        Fixtures.insertPng(Fixtures.encode(Fixtures.toRgb(patches()), "png"), Fixtures.iccp(icc));
    DecodedImage d = decode(file("s.png", png));
    assertThat(d.image().sameContent(patches())).isTrue();
    assertThat(d.notes()).isEmpty();
  }

  @Test
  void corruptProfileOpensAsSrgbWithANote() throws Exception {
    byte[] png =
        Fixtures.insertPng(
            Fixtures.encode(Fixtures.toRgb(patches()), "png"), Fixtures.iccp(new byte[300]));
    DecodedImage d = decode(file("b.png", png));
    assertThat(d.image().sameContent(patches())).isTrue();
    assertThat(keys(d)).containsExactly("io.note.bad_profile");
  }

  @Test
  void grayProfileUsesItsToneCurve() throws Exception {
    byte[] icc = Fixtures.grayProfile("Gris 1.0", 1.0);
    byte[] png = Fixtures.insertPng(Fixtures.encode(grayRamp(), "png"), Fixtures.iccp(icc));
    DecodedImage d = decode(file("gg.png", png));
    int expected = (int) Math.round(Fixtures.oetf(128 / 255.0) * 255);
    assertThat(d.image().argb(128, 3) & 255)
        .isCloseTo(expected, org.assertj.core.data.Offset.offset(2));
    assertThat(keys(d)).containsExactly("io.note.converted");
  }

  /** Marca cada esquina con un color propio. */
  private static Img corners(int w, int h) {
    int[] d = new int[w * h];
    java.util.Arrays.fill(d, 0xFF808080);
    d[0] = 0xFFFF0000;
    d[w - 1] = 0xFF00FF00;
    d[(h - 1) * w] = 0xFF0000FF;
    d[w * h - 1] = 0xFFFFFF00;
    return Img.adopt(w, h, d);
  }

  private static final int[][] CORNER_INDEX = {
    {0, 1, 2, 3},
    {1, 0, 3, 2},
    {3, 2, 1, 0},
    {2, 3, 0, 1},
    {0, 2, 1, 3},
    {2, 0, 3, 1},
    {3, 1, 2, 0},
    {1, 3, 0, 2}
  };
  private static final int[] CORNER_COLOUR = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00};

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
  void orientationIsBakedInPng(int o) throws Exception {
    byte[] png =
        Fixtures.insertPng(
            Fixtures.encode(Fixtures.toRgb(corners(5, 3)), "png"),
            Fixtures.pngChunk("eXIf", Fixtures.exifBlob(o)));
    DecodedImage d = decode(file("o.png", png));
    boolean swap = o >= 5;
    assertThat(d.image().width()).isEqualTo(swap ? 3 : 5);
    assertThat(d.image().height()).isEqualTo(swap ? 5 : 3);
    assertThat(d.info().orientation()).isEqualTo(o);
    assertThat(d.info().displayWidth()).isEqualTo(d.image().width());
    int w = d.image().width();
    int h = d.image().height();
    int[] want = CORNER_INDEX[o - 1];
    assertThat(d.image().argb(0, 0)).isEqualTo(CORNER_COLOUR[want[0]]);
    assertThat(d.image().argb(w - 1, 0)).isEqualTo(CORNER_COLOUR[want[1]]);
    assertThat(d.image().argb(0, h - 1)).isEqualTo(CORNER_COLOUR[want[2]]);
    assertThat(d.image().argb(w - 1, h - 1)).isEqualTo(CORNER_COLOUR[want[3]]);
  }

  /** Bloques de color de 16×16 en las esquinas: sobreviven a la compresión JPEG. */
  private static Img blockCorners() {
    int w = 64;
    int h = 48;
    int[] d = new int[w * h];
    for (int y = 0, i = 0; y < h; y++) {
      for (int x = 0; x < w; x++, i++) {
        boolean left = x < 16;
        boolean right = x >= w - 16;
        boolean top = y < 16;
        boolean bottom = y >= h - 16;
        d[i] =
            left && top
                ? 0xFFFF0000
                : right && top
                    ? 0xFF00FF00
                    : left && bottom ? 0xFF0000FF : right && bottom ? 0xFFFFFF00 : 0xFF808080;
      }
    }
    return Img.adopt(w, h, d);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
  void orientationIsBakedInJpeg(int o) throws Exception {
    byte[] jpg = Fixtures.insertJpeg(Fixtures.bestJpeg(dir, blockCorners()), Fixtures.exifSeg(o));
    DecodedImage d = decode(file("o.jpg", jpg));
    int w = d.image().width();
    int h = d.image().height();
    assertThat(w).isEqualTo(o >= 5 ? 48 : 64);
    int[] want = CORNER_INDEX[o - 1];
    assertNear(d.image().argb(3, 3), rgb(CORNER_COLOUR[want[0]]), 8);
    assertNear(d.image().argb(w - 4, 3), rgb(CORNER_COLOUR[want[1]]), 8);
    assertNear(d.image().argb(3, h - 4), rgb(CORNER_COLOUR[want[2]]), 8);
    assertNear(d.image().argb(w - 4, h - 4), rgb(CORNER_COLOUR[want[3]]), 8);
  }

  private static int[] rgb(int argb) {
    return new int[] {argb >> 16 & 255, argb >> 8 & 255, argb & 255};
  }

  @Test
  void tiffOrientationAndGpsAreRead() throws Exception {
    DecodedImage d = decode(file("o.tif", Fixtures.tiffWithExif(6)));
    assertThat(d.image().width()).isEqualTo(3);
    assertThat(d.image().height()).isEqualTo(4);
    assertThat(d.info().present()).contains(ImageInfo.MetadataKind.GPS);
    assertThat(d.metadata().exif().orElseThrow().gps()).hasSize(2);
  }

  @Test
  void webpLosslessIsExact() throws Exception {
    Path p = copyResource("gradient-lossless.webp");
    DecodedImage d = decode(p);
    assertThat(d.info().format()).isEqualTo(FormatId.WEBP);
    for (int y = 0; y < 24; y++) {
      for (int x = 0; x < 32; x++) {
        assertThat(d.image().argb(x, y)).isEqualTo(0xFF000000 | (x * 8) << 16 | (y * 10) << 8 | 90);
      }
    }
  }

  @Test
  void webpLossyOpens() throws Exception {
    DecodedImage d = decode(copyResource("gradient-lossy.webp"));
    assertThat(d.image().width()).isEqualTo(32);
    assertNear(d.image().argb(16, 12), new int[] {128, 120, 90}, 16);
  }

  private Path copyResource(String name) throws Exception {
    try (var in = DecodeTest.class.getResourceAsStream("/io/" + name)) {
      assertThat(in).as(name).isNotNull();
      return file(name, in.readAllBytes());
    }
  }

  @Test
  void webpProfileIsAppliedExactlyOnce() throws Exception {
    byte[] webp = Files.readAllBytes(copyResource("gradient-lossless.webp"));
    byte[] vp8l = java.util.Arrays.copyOfRange(webp, 12, webp.length);
    byte[] icc = Fixtures.rgbProfile("Pantalla P3", Fixtures.P3_D50);
    java.nio.ByteBuffer vp8x =
        java.nio.ByteBuffer.allocate(18).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    vp8x.put("VP8X".getBytes()).putInt(10).put((byte) 0x20).put(new byte[3]);
    vp8x.put((byte) 31).put((byte) 0).put((byte) 0).put((byte) 23).put((byte) 0).put((byte) 0);
    java.nio.ByteBuffer iccp =
        java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    iccp.put("ICCP".getBytes()).putInt(icc.length);
    byte[] pad = new byte[icc.length & 1];
    byte[] body = Fixtures.concat("WEBP".getBytes(), vp8x.array(), iccp.array(), icc, pad, vp8l);
    java.nio.ByteBuffer riff =
        java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    riff.put("RIFF".getBytes()).putInt(body.length);
    DecodedImage d = decode(file("icc.webp", Fixtures.concat(riff.array(), body)));
    assertThat(keys(d)).containsExactly("io.note.converted");
    int x = 25;
    int y = 10;
    assertNear(d.image().argb(x, y), Fixtures.analytic(x * 8, y * 10, 90), 2);
  }

  @Test
  void animatedGifOpensItsFirstFrameWithANote() throws Exception {
    javax.imageio.ImageWriter w = javax.imageio.ImageIO.getImageWritersByFormatName("gif").next();
    Path gif = dir.resolve("a.gif");
    try (var ios = javax.imageio.ImageIO.createImageOutputStream(gif.toFile())) {
      w.setOutput(ios);
      w.prepareWriteSequence(null);
      for (int i = 0; i < 3; i++) {
        BufferedImage b = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_INDEXED);
        w.writeToSequence(new javax.imageio.IIOImage(b, null, null), null);
      }
      w.endWriteSequence();
    } finally {
      w.dispose();
    }
    DecodedImage d = decode(gif);
    assertThat(d.info().frames()).isEqualTo(3);
    assertThat(keys(d)).containsExactly("io.note.frames");
    assertThat(d.notes().get(0).args()).containsExactly("3");
  }

  @Test
  void multiPageTiffOpensItsFirstPageWithANote() throws Exception {
    javax.imageio.ImageWriter w = javax.imageio.ImageIO.getImageWritersByFormatName("tiff").next();
    Path t = dir.resolve("m.tif");
    try (var ios = javax.imageio.ImageIO.createImageOutputStream(t.toFile())) {
      w.setOutput(ios);
      w.prepareWriteSequence(null);
      for (int i = 0; i < 2; i++) {
        w.writeToSequence(
            new javax.imageio.IIOImage(
                new BufferedImage(8 + i, 8, BufferedImage.TYPE_INT_RGB), null, null),
            null);
      }
      w.endWriteSequence();
    } finally {
      w.dispose();
    }
    DecodedImage d = decode(t);
    assertThat(d.image().width()).isEqualTo(8);
    assertThat(d.notes().get(0).args()).containsExactly("2");
  }

  @Test
  void inspectDoesNotNeedThePixels() throws Exception {
    Path p =
        file(
            "i.jpg",
            Fixtures.insertJpeg(
                Fixtures.bestJpeg(dir, Fixtures.photo(64, 32)), Fixtures.exifSeg(6)));
    ImageInfo info = decoder.inspect(p, CancellationToken.none());
    assertThat(info.format()).isEqualTo(FormatId.JPEG);
    assertThat(info.width()).isEqualTo(64);
    assertThat(info.orientation()).isEqualTo(6);
    assertThat(info.displayWidth()).isEqualTo(32);
    assertThat(info.present()).contains(ImageInfo.MetadataKind.EXIF, ImageInfo.MetadataKind.GPS);
  }

  @Test
  void extensionDoesNotDecideTheFormat() throws Exception {
    byte[] png = Fixtures.encode(Fixtures.toRgb(patches()), "png");
    DecodedImage d = decode(file("mentira.jpg", png));
    assertThat(d.info().format()).isEqualTo(FormatId.PNG);
    assertThat(d.image().sameContent(patches())).isTrue();
  }

  @Test
  void pathsWithAccentsSpacesAndEmojisWork() throws Exception {
    Path sub = dir.resolve("carpeta con tildes á 🌄");
    Files.createDirectories(sub);
    Path p = sub.resolve("fotó ñandú 😀 日本.png");
    Files.write(p, Fixtures.encode(Fixtures.toRgb(patches()), "png"));
    assertThat(decode(p).image().sameContent(patches())).isTrue();
  }

  @Test
  void cancellingDuringTheReadStopsIt() throws Exception {
    Path p = file("big.png", Fixtures.encode(Fixtures.toRgb(Fixtures.photo(1500, 1000)), "png"));
    CancellationToken token = CancellationToken.create();
    Progress cancelSoon =
        (done, total) -> {
          if (done > 5) {
            token.cancel();
          }
        };
    assertThatThrownBy(() -> decoder.decode(p, DecodeOptions.DEFAULT, token, cancelSoon))
        .isInstanceOf(ImagingException.Cancelled.class);
  }

  @Test
  void aCancelledTokenStopsBeforeReading() throws Exception {
    Path p = file("x.png", Fixtures.encode(Fixtures.toRgb(patches()), "png"));
    CancellationToken token = CancellationToken.create();
    token.cancel();
    assertThatThrownBy(() -> decoder.decode(p, DecodeOptions.DEFAULT, token, Progress.NONE))
        .isInstanceOf(ImagingException.Cancelled.class);
  }

  @Test
  void codecsAreChosenByClassAndFoundThroughTheServiceLoader() throws Exception {
    for (FormatId f : FormatId.values()) {
      var r = Codecs.reader(f);
      assertThat(r.getClass().getName())
          .startsWith(f == FormatId.WEBP ? "com.twelvemonkeys." : "com.sun.imageio.");
      r.dispose();
      assertThat(Codecs.decoder(f)).isInstanceOf(ImageIoDecoder.class);
    }
    for (FormatId f : List.of(FormatId.JPEG, FormatId.PNG, FormatId.TIFF)) {
      var w = Codecs.writer(f);
      assertThat(w.getClass().getName()).startsWith("com.sun.imageio.");
      w.dispose();
      assertThat(Codecs.encoder(f).format()).isEqualTo(f);
    }
    assertThatThrownBy(() -> Codecs.encoder(FormatId.WEBP))
        .isInstanceOf(ImagingException.UnsupportedFormat.class);
    assertThat(ImageMetadata.EMPTY.orientation()).isEqualTo(1);
    assertThat(ExportSettings.defaults(FormatId.PNG).deflateLevel()).isEqualTo(6);
  }
}
