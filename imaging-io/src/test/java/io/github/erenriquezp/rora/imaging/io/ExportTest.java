package io.github.erenriquezp.rora.imaging.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twelvemonkeys.imageio.metadata.CompoundDirectory;
import com.twelvemonkeys.imageio.metadata.Directory;
import com.twelvemonkeys.imageio.metadata.Entry;
import com.twelvemonkeys.imageio.metadata.tiff.TIFFReader;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ContainerScanner;
import io.github.erenriquezp.rora.core.io.DecodeOptions;
import io.github.erenriquezp.rora.core.io.DecodedImage;
import io.github.erenriquezp.rora.core.io.ExportSettings;
import io.github.erenriquezp.rora.core.io.ExportSettings.MetadataPolicy;
import io.github.erenriquezp.rora.core.io.ExportSettings.ProfilePolicy;
import io.github.erenriquezp.rora.core.io.ExportSettings.Subsampling;
import io.github.erenriquezp.rora.core.io.ExportSettings.TiffCompression;
import io.github.erenriquezp.rora.core.io.FormatId;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import io.github.erenriquezp.rora.core.io.ImagingException;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** Codificadores, privacidad y escritura atómica. */
@Tag("property")
class ExportTest {

  @TempDir Path dir;
  private final Exporter exporter = new Exporter();

  private Path export(Img img, ImageMetadata m, ExportSettings s, String name) throws Exception {
    return exporter
        .export(
            img,
            m,
            s,
            Exporter.Request.ask(dir, name),
            TileExecutor.sequential(),
            CancellationToken.none(),
            Progress.NONE)
        .file();
  }

  private Img read(Path p) throws Exception {
    return new ImageIoDecoder()
        .decode(p, DecodeOptions.DEFAULT, CancellationToken.none(), Progress.NONE)
        .image();
  }

  private DecodedImage decode(Path p) throws Exception {
    return new ImageIoDecoder()
        .decode(p, DecodeOptions.DEFAULT, CancellationToken.none(), Progress.NONE);
  }

  private List<String> folder() throws IOException {
    try (Stream<Path> s = Files.list(dir)) {
      return s.map(p -> p.getFileName().toString()).sorted().toList();
    }
  }

  @Test
  void pngRoundTripIsExactIncludingInvisibleColours() throws Exception {
    Img src = Fixtures.noiseArgb(128, 128, 11);
    Path p = export(src, ImageMetadata.EMPTY, ExportSettings.defaults(FormatId.PNG), "a.png");
    assertThat(read(p).sameContent(src)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(TiffCompression.class)
  void tiffRoundTripIsExactWithEveryCompression(TiffCompression c) throws Exception {
    Img src = Fixtures.noiseArgb(96, 64, 12);
    Path p =
        export(
            src, ImageMetadata.EMPTY, ExportSettings.defaults(FormatId.TIFF).withTiff(c), "a.tif");
    assertThat(read(p).sameContent(src)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = FormatId.class,
      names = {"PNG", "TIFF"})
  void opaqueImagesStayOpaque(FormatId f) throws Exception {
    Img src = Fixtures.photo(60, 40);
    Path p = export(src, ImageMetadata.EMPTY, ExportSettings.defaults(f), "o." + f.extension());
    assertThat(read(p).sameContent(src)).isTrue();
  }

  private static double psnr(Img a, Img b) {
    long se = 0;
    long n = 0;
    for (int y = 0; y < a.height(); y++) {
      for (int x = 0; x < a.width(); x++) {
        int p = a.argb(x, y);
        int q = b.argb(x, y);
        for (int sh = 0; sh <= 16; sh += 8) {
          int d = (p >> sh & 255) - (q >> sh & 255);
          se += d * d;
          n++;
        }
      }
    }
    return se == 0 ? 99 : 10 * Math.log10(255.0 * 255.0 / ((double) se / n));
  }

  @Test
  void jpegAtHighQualityMeetsTheMinimumPsnr() throws Exception {
    Img src = Fixtures.photo(800, 600);
    Path p =
        export(
            src,
            ImageMetadata.EMPTY,
            ExportSettings.defaults(FormatId.JPEG)
                .withQuality(90)
                .withJpeg(false, Subsampling.S444, true),
            "q.jpg");
    assertThat(psnr(src, read(p))).isGreaterThan(40.0);
  }

  private static int sofSampling(byte[] j, int comp) {
    for (int i = 2; i + 10 < j.length; ) {
      int m = j[i + 1] & 255;
      int len = (j[i + 2] & 255) << 8 | (j[i + 3] & 255);
      if (m == 0xC0 || m == 0xC2) {
        return j[i + 11 + 3 * comp] & 255;
      }
      i += 2 + len;
    }
    throw new AssertionError("sin SOF");
  }

  @Test
  void jpegOptionsReachTheFile() throws Exception {
    Img src = Fixtures.photo(320, 240);
    ExportSettings base = ExportSettings.defaults(FormatId.JPEG);
    byte[] s420 =
        Files.readAllBytes(
            export(
                src,
                ImageMetadata.EMPTY,
                base.withJpeg(false, Subsampling.S420, true),
                "s420.jpg"));
    byte[] s444 =
        Files.readAllBytes(
            export(
                src,
                ImageMetadata.EMPTY,
                base.withJpeg(false, Subsampling.S444, true),
                "s444.jpg"));
    byte[] prog =
        Files.readAllBytes(
            export(src, ImageMetadata.EMPTY, base.withJpeg(true, Subsampling.S420, true), "p.jpg"));
    byte[] noHuff =
        Files.readAllBytes(
            export(
                src, ImageMetadata.EMPTY, base.withJpeg(false, Subsampling.S420, false), "nh.jpg"));
    assertThat(sofSampling(s420, 0)).isEqualTo(0x22);
    assertThat(sofSampling(s444, 0)).isEqualTo(0x11);
    assertThat(Fixtures.count(prog, new byte[] {(byte) 0xFF, (byte) 0xC2})).isPositive();
    assertThat(noHuff.length).isGreaterThan(s420.length);
    byte[] lowQ =
        Files.readAllBytes(export(src, ImageMetadata.EMPTY, base.withQuality(20), "l.jpg"));
    assertThat(lowQ.length).isLessThan(s420.length);
  }

  @Test
  void jpegFlattensTransparencyOverTheChosenColour() throws Exception {
    int[] px = new int[64 * 64];
    Arrays.fill(px, 0x00123456);
    Img src = Img.adopt(64, 64, px);
    Path p =
        export(
            src,
            ImageMetadata.EMPTY,
            ExportSettings.defaults(FormatId.JPEG).withBackground(0xFF8000),
            "f.jpg");
    int c = read(p).argb(32, 32);
    assertThat(c >>> 24).isEqualTo(255);
    assertThat(c >> 16 & 255).isBetween(250, 255);
    assertThat(c >> 8 & 255).isBetween(124, 132);
    assertThat(c & 255).isBetween(0, 5);
  }

  @Test
  void profilePolicyControlsTheTag() throws Exception {
    Img src = Fixtures.photo(40, 30);
    for (FormatId f : List.of(FormatId.JPEG, FormatId.PNG, FormatId.TIFF)) {
      ExportSettings s = ExportSettings.defaults(f).withMetadata(MetadataPolicy.REMOVE_ALL);
      byte[] tagged = Files.readAllBytes(export(src, ImageMetadata.EMPTY, s, "t." + f.extension()));
      byte[] plain =
          Files.readAllBytes(
              export(
                  src,
                  ImageMetadata.EMPTY,
                  s.withProfile(ProfilePolicy.UNTAGGED),
                  "u." + f.extension()));
      String mark = f == FormatId.PNG ? "sRGB" : "acsp";
      assertThat(Fixtures.count(tagged, mark)).as(f + " etiquetado").isPositive();
      assertThat(Fixtures.count(plain, mark)).as(f + " sin etiquetar").isZero();
    }
  }

  @Test
  void generatedProfileConvertsLikeTheJdkSrgb() throws Exception {
    assertThat(
            ColorPipeline.equivalentToSrgb(
                ICC_Profile.getInstance(io.github.erenriquezp.rora.core.io.SrgbProfile.bytes())))
        .isTrue();
  }

  private byte[] sourceWithEverything(String kind) throws Exception {
    Img img = Fixtures.photo(80, 60);
    return switch (kind) {
      case "jpeg" ->
          Fixtures.insertJpeg(
              Fixtures.bestJpeg(dir, img),
              Fixtures.exifSeg(1),
              Fixtures.xmpSeg(Fixtures.xmpPacket()),
              Fixtures.iptcSeg());
      case "png" ->
          Fixtures.insertPng(
              Fixtures.encode(Fixtures.toRgb(img), "png"),
              Fixtures.pngChunk("eXIf", Fixtures.exifBlob(1)),
              Fixtures.itxtXmp(Fixtures.xmpPacket()),
              Fixtures.pngChunk("tEXt", ("Comment\0" + Fixtures.PNG_COMMENT).getBytes()),
              Fixtures.pngChunk("tEXt", "Title\0Titulo".getBytes()));
      default -> throw new IllegalArgumentException(kind);
    };
  }

  static Stream<Arguments> privacyMatrix() {
    return Stream.of("jpeg", "png")
        .flatMap(
            src ->
                Stream.of(FormatId.JPEG, FormatId.PNG, FormatId.TIFF)
                    .flatMap(
                        f ->
                            Arrays.stream(MetadataPolicy.values())
                                .map(p -> Arguments.of(src, f, p))));
  }

  @ParameterizedTest
  @MethodSource("privacyMatrix")
  void exportedBytesContainOnlyWhatThePolicyAllows(String src, FormatId f, MetadataPolicy policy)
      throws Exception {
    Path in = dir.resolve("in." + (src.equals("jpeg") ? "jpg" : "png"));
    Files.write(in, sourceWithEverything(src));
    DecodedImage d = decode(in);
    assertThat(d.metadata().exif().orElseThrow().gps()).as("el fixture tiene GPS").hasSize(2);

    byte[] out =
        Files.readAllBytes(
            export(
                d.image(),
                d.metadata(),
                ExportSettings.defaults(f).withMetadata(policy),
                "out." + f.extension()));

    // Nunca, con ninguna política:
    assertThat(Fixtures.count(out, Fixtures.THUMBNAIL)).as("miniatura").isZero();
    assertThat(Fixtures.count(out, Fixtures.MAKER_NOTE)).as("MakerNote").isZero();
    assertThat(Fixtures.count(out, Fixtures.PNG_COMMENT)).as("texto PNG no admitido").isZero();

    boolean keepsAll = policy == MetadataPolicy.KEEP && f != FormatId.TIFF;
    assertThat(Fixtures.count(out, Fixtures.GPS_RATIONALS) > 0).as("GPS EXIF").isEqualTo(keepsAll);
    assertThat(Fixtures.count(out, Fixtures.XMP_GPS) > 0).as("GPS XMP").isEqualTo(keepsAll);
    assertThat(Fixtures.count(out, Fixtures.SERIAL) > 0).as("nº de serie").isEqualTo(keepsAll);
    assertThat(Fixtures.count(out, Fixtures.COMMENT) > 0).as("UserComment").isEqualTo(keepsAll);
    if (src.equals("jpeg")) {
      assertThat(Fixtures.count(out, Fixtures.IPTC_CITY) > 0)
          .as("IPTC")
          .isEqualTo(policy == MetadataPolicy.KEEP && f == FormatId.JPEG);
    }
    boolean camera = policy != MetadataPolicy.REMOVE_ALL;
    assertThat(Fixtures.count(out, "Modelo-Cam") > 0).as("cámara").isEqualTo(camera);
    if (src.equals("png") && f == FormatId.PNG) {
      assertThat(Fixtures.count(out, "Titulo") > 0)
          .as("título")
          .isEqualTo(policy == MetadataPolicy.KEEP);
    }
  }

  @Test
  void tiffSourceLosesItsGpsWhenAsked() throws Exception {
    Path in = dir.resolve("in.tif");
    Files.write(in, Fixtures.tiffWithExif(1));
    DecodedImage d = decode(in);
    byte[] out =
        Files.readAllBytes(
            export(d.image(), d.metadata(), ExportSettings.defaults(FormatId.JPEG), "o.jpg"));
    assertThat(Fixtures.count(out, Fixtures.GPS_RATIONALS)).isZero();
  }

  @ParameterizedTest
  @EnumSource(
      value = FormatId.class,
      names = {"JPEG", "PNG"})
  void outputExifIsUprightAndReadableByAnIndependentParser(FormatId f) throws Exception {
    Path in = dir.resolve("in.jpg");
    Files.write(
        in,
        Fixtures.insertJpeg(Fixtures.bestJpeg(dir, Fixtures.photo(80, 60)), Fixtures.exifSeg(6)));
    DecodedImage d = decode(in);
    assertThat(d.image().width()).isEqualTo(60);
    Path out =
        export(
            d.image(),
            d.metadata(),
            ExportSettings.defaults(f).withMetadata(MetadataPolicy.KEEP),
            "o." + f.extension());
    ImageMetadata again = ContainerScanner.scan(f, out);
    assertThat(again.orientation()).isEqualTo(1);

    byte[] exif = rawExif(f, Files.readAllBytes(out));
    try (var s = new MemoryCacheImageInputStream(new ByteArrayInputStream(exif))) {
      Directory dirs = new TIFFReader().read(s);
      Directory first = dirs instanceof CompoundDirectory c ? c.getDirectory(0) : dirs;
      assertThat(dirs instanceof CompoundDirectory c ? c.directoryCount() : 1)
          .as("sin IFD1")
          .isEqualTo(1);
      Entry orientation = first.getEntryById(274);
      assertThat(((Number) orientation.getValue()).intValue()).isEqualTo(1);
      assertThat(first.getEntryById(34853)).as("GPS").isNotNull();
      Directory exifIfd = (Directory) first.getEntryById(34665).getValue();
      assertThat(((Number) exifIfd.getEntryById(0xA002).getValue()).intValue()).isEqualTo(60);
      assertThat(((Number) exifIfd.getEntryById(0xA003).getValue()).intValue()).isEqualTo(80);
    }
  }

  private static byte[] rawExif(FormatId f, byte[] file) {
    if (f == FormatId.JPEG) {
      for (int i = 2; i + 10 < file.length; ) {
        int m = file[i + 1] & 255;
        int len = (file[i + 2] & 255) << 8 | (file[i + 3] & 255);
        if (m == 0xE1 && file[i + 4] == 'E' && file[i + 5] == 'x') {
          return Arrays.copyOfRange(file, i + 10, i + 2 + len);
        }
        i += 2 + len;
      }
    } else {
      for (int i = 8; i + 12 <= file.length; ) {
        int len = java.nio.ByteBuffer.wrap(file, i, 4).getInt();
        if (file[i + 4] == 'e' && file[i + 5] == 'X') {
          return Arrays.copyOfRange(file, i + 8, i + 8 + len);
        }
        i += 12 + len;
      }
    }
    throw new AssertionError("sin EXIF");
  }

  @Test
  void aLongSideLimitReducesButNeverEnlarges() throws Exception {
    Img src = Fixtures.photo(400, 200);
    ExportSettings s = ExportSettings.defaults(FormatId.PNG);
    assertThat(read(export(src, ImageMetadata.EMPTY, s.withMaxLongSide(100), "a.png")).width())
        .isEqualTo(100);
    assertThat(read(export(src, ImageMetadata.EMPTY, s.withMaxLongSide(1000), "b.png")).width())
        .isEqualTo(400);
  }

  @Test
  void aTargetSizeIsMetWithTheBestQualityThatFits() throws Exception {
    Img src = Fixtures.photo(640, 480);
    ExportSettings s = ExportSettings.defaults(FormatId.JPEG).withMaxBytes(30_000).withQuality(95);
    Exporter.Result r =
        exporter.export(
            src,
            ImageMetadata.EMPTY,
            s,
            Exporter.Request.ask(dir, "t.jpg"),
            TileExecutor.sequential(),
            CancellationToken.none(),
            Progress.NONE);
    assertThat(r.bytes()).isLessThanOrEqualTo(30_000);
    assertThat(r.quality()).isLessThan(95);
    Exporter.Result again =
        exporter.export(
            src,
            ImageMetadata.EMPTY,
            s,
            Exporter.Request.ask(dir, "t2.jpg"),
            TileExecutor.sequential(),
            CancellationToken.none(),
            Progress.NONE);
    assertThat(again.quality()).isEqualTo(r.quality());
    assertThat(Files.readAllBytes(again.file())).isEqualTo(Files.readAllBytes(r.file()));
  }

  @Test
  void anUnreachableSizeFailsAndLeavesNothing() throws Exception {
    Img src = Fixtures.noiseArgb(640, 480, 1);
    ExportSettings s = ExportSettings.defaults(FormatId.JPEG).withMaxBytes(100);
    assertThatThrownBy(() -> export(src, ImageMetadata.EMPTY, s, "x.jpg"))
        .isInstanceOfSatisfying(
            ImagingException.EncodingFailure.class,
            e -> assertThat(e.messageKey()).isEqualTo("io.error.targetsize"));
    assertThat(folder()).isEmpty();
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"encoded", "forced"})
  void aFailureAtAnyStageKeepsThePreviousFileAndLeavesNoTemporary(String failAt) throws Exception {
    Path target = dir.resolve("f.png");
    Files.writeString(target, "previo");
    Exporter failing =
        new Exporter(
            name -> {
              if (name.equals(failAt)) {
                throw new IOException("fallo inyectado");
              }
            });
    assertThatThrownBy(
            () ->
                failing.export(
                    Fixtures.photo(32, 32),
                    ImageMetadata.EMPTY,
                    ExportSettings.defaults(FormatId.PNG),
                    new Exporter.Request(
                        dir, "f.png", Exporter.Collision.OVERWRITE, true, false, Optional.empty()),
                    TileExecutor.sequential(),
                    CancellationToken.none(),
                    Progress.NONE))
        .isInstanceOf(ImagingException.IoFailure.class);
    assertThat(Files.readString(target)).isEqualTo("previo");
    assertThat(folder()).containsExactly("f.png");
  }

  @ParameterizedTest
  @EnumSource(
      value = FormatId.class,
      names = {"JPEG", "PNG", "TIFF"})
  void cancellingDuringTheWriteLeavesNothing(FormatId f) throws Exception {
    Path target = dir.resolve("c." + f.extension());
    Files.writeString(target, "previo");
    CancellationToken token = CancellationToken.create();
    Progress cancel = (done, total) -> token.cancel();
    assertThatThrownBy(
            () ->
                exporter.export(
                    Fixtures.photo(800, 600),
                    ImageMetadata.EMPTY,
                    ExportSettings.defaults(f),
                    new Exporter.Request(
                        dir,
                        target.getFileName().toString(),
                        Exporter.Collision.OVERWRITE,
                        true,
                        false,
                        Optional.empty()),
                    TileExecutor.sequential(),
                    token,
                    cancel))
        .isInstanceOf(ImagingException.Cancelled.class);
    assertThat(Files.readString(target)).isEqualTo("previo");
    assertThat(folder()).containsExactly(target.getFileName().toString());
  }

  private Exporter.Result collide(
      Exporter.Collision c, boolean confirmed, boolean originalConfirmed, Optional<Path> original)
      throws Exception {
    return exporter.export(
        Fixtures.photo(16, 16),
        ImageMetadata.EMPTY,
        ExportSettings.defaults(FormatId.PNG),
        new Exporter.Request(dir, "n.png", c, confirmed, originalConfirmed, original),
        TileExecutor.sequential(),
        CancellationToken.none(),
        Progress.NONE);
  }

  private static String key(ThrowingCall c) {
    try {
      c.run();
      return "ok";
    } catch (ImagingException e) {
      return e.messageKey();
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private interface ThrowingCall {
    void run() throws Exception;
  }

  @Test
  void anExistingFileIsNeverReplacedWithoutConfirmation() throws Exception {
    Path n = dir.resolve("n.png");
    Files.writeString(n, "previo");
    assertThat(key(() -> collide(Exporter.Collision.ASK, false, false, Optional.empty())))
        .isEqualTo("io.error.exists");
    assertThat(Files.readString(n)).isEqualTo("previo");
    assertThat(key(() -> collide(Exporter.Collision.OVERWRITE, false, false, Optional.empty())))
        .isEqualTo("io.error.exists");
    assertThat(Files.readString(n)).isEqualTo("previo");
    assertThat(folder()).containsExactly("n.png");
  }

  @Test
  void overwritingTheOriginalNeedsItsOwnConfirmation() throws Exception {
    Path n = dir.resolve("n.png");
    Files.writeString(n, "original");
    assertThat(key(() -> collide(Exporter.Collision.OVERWRITE, true, false, Optional.of(n))))
        .isEqualTo("io.error.original");
    assertThat(Files.readString(n)).isEqualTo("original");
    assertThat(key(() -> collide(Exporter.Collision.OVERWRITE, true, true, Optional.of(n))))
        .isEqualTo("ok");
    assertThat(Files.size(n)).isGreaterThan(8);
    assertThat(folder()).containsExactly("n.png");
  }

  @Test
  void numberingFindsTheNextFreeName() throws Exception {
    Files.writeString(dir.resolve("n.png"), "a");
    Files.writeString(dir.resolve("n (2).png"), "b");
    assertThat(
            collide(Exporter.Collision.NUMBER, false, false, Optional.empty())
                .file()
                .getFileName()
                .toString())
        .isEqualTo("n (3).png");
    assertThat(Files.readString(dir.resolve("n.png"))).isEqualTo("a");
  }

  @Test
  void aMissingFolderIsAnIoFailureWithNoLeftovers() {
    Path missing = dir.resolve("no-existe");
    assertThat(
            key(
                () ->
                    exporter.export(
                        Fixtures.photo(8, 8),
                        ImageMetadata.EMPTY,
                        ExportSettings.defaults(FormatId.PNG),
                        Exporter.Request.ask(missing, "a.png"),
                        TileExecutor.sequential(),
                        CancellationToken.none(),
                        Progress.NONE)))
        .isEqualTo("io.error.write");
  }

  @Test
  void namesWithAccentsAndEmojisArePublished() throws Exception {
    String name = "fotó ñandú 😀.jpg";
    Path p =
        export(
            Fixtures.photo(16, 16),
            ImageMetadata.EMPTY,
            ExportSettings.defaults(FormatId.JPEG),
            name);
    assertThat(p.getFileName().toString()).isEqualTo(name);
    assertThat(Files.exists(p)).isTrue();
  }
}
