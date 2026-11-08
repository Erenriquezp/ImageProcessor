package io.github.erenriquezp.rora.imaging.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.DecodeOptions;
import io.github.erenriquezp.rora.core.io.ExportSettings;
import io.github.erenriquezp.rora.core.io.FormatId;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import io.github.erenriquezp.rora.core.io.ImagingException;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Archivos defectuosos: fallan rápido, sin colgarse ni agotar la memoria. */
@Tag("property")
class RobustnessTest {

  private static final Duration LIMIT = Duration.ofSeconds(20);

  @TempDir Path dir;

  private String outcome(String name, byte[] data) throws Exception {
    Path p = dir.resolve(name);
    Files.write(p, data);
    return assertTimeoutPreemptively(
        LIMIT,
        () -> {
          try {
            new ImageIoDecoder()
                .decode(p, DecodeOptions.DEFAULT, CancellationToken.none(), Progress.NONE);
            return "ok";
          } catch (ImagingException e) {
            return e.messageKey();
          }
        });
  }

  private byte[] sample(String fmt) throws Exception {
    Img img = Fixtures.photo(600, 400);
    return switch (fmt) {
      case "jpg" -> Fixtures.bestJpeg(dir, img);
      case "png" -> Fixtures.encode(Fixtures.toRgb(img), "png");
      case "tif" -> Fixtures.encode(Fixtures.toRgb(img), "tiff");
      case "gif" -> Fixtures.encode(Fixtures.toRgb(img), "gif");
      case "bmp" -> Fixtures.encode(Fixtures.toRgb(img), "bmp");
      default -> throw new IllegalArgumentException(fmt);
    };
  }

  @ParameterizedTest
  @CsvSource({
    "jpg, 0.5",
    "jpg, 0.02",
    "png, 0.5",
    "png, 0.02",
    "tif, 0.5",
    "tif, 0.02",
    "bmp, 0.5",
    "gif, 0.5",
  })
  void truncatedFilesFailFastAsCorrupt(String fmt, double fraction) throws Exception {
    byte[] data = sample(fmt);
    byte[] cut = Arrays.copyOf(data, (int) (data.length * fraction));
    assertThat(outcome("t." + fmt, cut)).isEqualTo("io.error.corrupt");
  }

  private static byte[] chunk(String t, byte[] d) {
    return Fixtures.pngChunk(t, d);
  }

  @Test
  void aHugePngHeaderIsRejectedBeforeDecoding() throws Exception {
    ByteBuffer ih = ByteBuffer.allocate(13);
    ih.putInt(60000)
        .putInt(60000)
        .put((byte) 8)
        .put((byte) 6)
        .put((byte) 0)
        .put((byte) 0)
        .put((byte) 0);
    byte[] bomb =
        Fixtures.concat(
            new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10},
            chunk("IHDR", ih.array()),
            chunk("IDAT", new byte[] {0x78, 1}),
            chunk("IEND", new byte[0]));
    assertThat(outcome("b.png", bomb)).isEqualTo("io.error.toolarge");
  }

  @Test
  void aHugeJpegHeaderIsRejectedBeforeDecoding() throws Exception {
    byte[] j = sample("jpg");
    for (int i = 2; i + 10 < j.length; ) {
      int m = j[i + 1] & 255;
      int len = (j[i + 2] & 255) << 8 | (j[i + 3] & 255);
      if (m == 0xC0) {
        j[i + 5] = (byte) 0xEA;
        j[i + 6] = 0x60;
        j[i + 7] = (byte) 0xEA;
        j[i + 8] = 0x60;
        break;
      }
      i += 2 + len;
    }
    assertThat(outcome("b.jpg", j)).isEqualTo("io.error.toolarge");
  }

  @Test
  void aHugeTiffHeaderIsRejectedBeforeDecoding() throws Exception {
    byte[] t = sample("tif");
    ByteBuffer v = ByteBuffer.wrap(t);
    v.order(t[0] == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
    int ifd = v.getInt(4);
    int n = v.getShort(ifd) & 0xFFFF;
    for (int e = 0; e < n; e++) {
      int at = ifd + 2 + 12 * e;
      int tag = v.getShort(at) & 0xFFFF;
      int type = v.getShort(at + 2) & 0xFFFF;
      if (tag == 256 || tag == 257) {
        if (type == 3) {
          v.putShort(at + 8, (short) 60000);
        } else {
          v.putInt(at + 8, 60000);
        }
      }
    }
    assertThat(outcome("b.tif", t)).isEqualTo("io.error.toolarge");
  }

  @Test
  void garbageWithAValidSignatureIsCorrupt() throws Exception {
    byte[] garbage = new byte[4096];
    new Random(1).nextBytes(garbage);
    garbage[0] = (byte) 0xFF;
    garbage[1] = (byte) 0xD8;
    garbage[2] = (byte) 0xFF;
    assertThat(outcome("g.jpg", garbage)).isEqualTo("io.error.corrupt");
  }

  @Test
  void unknownFormatsAndEmptyFilesAreUnsupported() throws Exception {
    assertThat(outcome("x.png", "no es imagen".getBytes())).isEqualTo("io.error.unsupported");
    assertThat(outcome("v.png", new byte[0])).isEqualTo("io.error.unsupported");
  }

  @Test
  void heicAndRawHaveAClearMessage() throws Exception {
    byte[] heic = new byte[64];
    System.arraycopy("ftypheic".getBytes(), 0, heic, 4, 8);
    assertThat(outcome("a.heic", heic)).isEqualTo("io.error.heif");
    assertThat(outcome("a.nef", sample("tif"))).isEqualTo("io.error.raw");
  }

  @Test
  void malformedExifDoesNotStopTheImageAndIsDropped() throws Exception {
    byte[] exif = Fixtures.exifBlob(6);
    ByteBuffer.wrap(exif).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 0x7FFFFFF0);
    byte[] seg =
        Fixtures.seg(
            0xE1,
            Fixtures.concat(
                "Exif\0\0".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), exif));
    Path p = dir.resolve("m.jpg");
    Files.write(p, Fixtures.insertJpeg(Fixtures.bestJpeg(dir, Fixtures.photo(32, 16)), seg));
    var d =
        new ImageIoDecoder()
            .decode(p, DecodeOptions.DEFAULT, CancellationToken.none(), Progress.NONE);
    assertThat(d.image().width()).isEqualTo(32);
    assertThat(d.metadata().exif()).isEmpty();
    assertThat(d.notes().stream().map(n -> n.key())).contains("io.note.metadata_dropped");
  }

  @Test
  void invalidXmpIsDroppedWithoutBlockingTheImage() throws Exception {
    Path p = dir.resolve("x.jpg");
    Files.write(
        p,
        Fixtures.insertJpeg(
            Fixtures.bestJpeg(dir, Fixtures.photo(16, 16)),
            Fixtures.xmpSeg("<!DOCTYPE a [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><a>&x;</a>")));
    var d =
        new ImageIoDecoder()
            .decode(p, DecodeOptions.DEFAULT, CancellationToken.none(), Progress.NONE);
    assertThat(d.metadata().xmp()).isEmpty();
    assertThat(d.notes().stream().map(n -> n.key())).contains("io.note.metadata_dropped");
  }

  private static Set<String> imageIoTemporaries() {
    File[] files = new File(System.getProperty("java.io.tmpdir")).listFiles();
    Set<String> names = new HashSet<>();
    if (files != null) {
      for (File f : files) {
        if (f.getName().startsWith("imageio")) {
          names.add(f.getName());
        }
      }
    }
    return names;
  }

  @Test
  void decodingAndExportingLeaveNoImageIoCacheFiles() throws Exception {
    Set<String> before = imageIoTemporaries();
    Path in = dir.resolve("a.png");
    Files.write(in, sample("png"));
    var d =
        new ImageIoDecoder()
            .decode(in, DecodeOptions.DEFAULT, CancellationToken.none(), Progress.NONE);
    for (FormatId f : new FormatId[] {FormatId.JPEG, FormatId.PNG, FormatId.TIFF}) {
      new Exporter()
          .export(
              d.image(),
              ImageMetadata.EMPTY,
              ExportSettings.defaults(f),
              Exporter.Request.ask(dir, "o." + f.extension()),
              TileExecutor.sequential(),
              CancellationToken.none(),
              Progress.NONE);
    }
    assertThat(imageIoTemporaries()).isEqualTo(before);
  }
}
