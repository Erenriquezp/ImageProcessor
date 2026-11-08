package io.github.erenriquezp.rora.core.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Lógica pura de E/S: sin archivos ni AWT. */
@Tag("property")
class PureIoTest {

  /** 3×2 con valores distintos: {@code 1 2 3 / 4 5 6}. */
  private static final int[] SRC = {1, 2, 3, 4, 5, 6};

  @ParameterizedTest
  @CsvSource({
    "1, 3, 2, 1 2 3 4 5 6",
    "2, 3, 2, 3 2 1 6 5 4",
    "3, 3, 2, 6 5 4 3 2 1",
    "4, 3, 2, 4 5 6 1 2 3",
    "5, 2, 3, 1 4 2 5 3 6",
    "6, 2, 3, 4 1 5 2 6 3",
    "7, 2, 3, 6 3 5 2 4 1",
    "8, 2, 3, 3 6 2 5 1 4",
  })
  void orientationMovesEveryPixelWhereExifSays(int o, int w, int h, String expected) {
    int[] out = Orientation.apply(o, SRC.clone(), 3, 2);
    List<Integer> want = new ArrayList<>();
    for (String s : expected.split(" ")) {
      want.add(Integer.valueOf(s));
    }
    assertThat(out).containsExactly(want.stream().mapToInt(i -> i).toArray());
    assertThat(Orientation.swapsAxes(o) ? 2 : 3).isEqualTo(w);
    assertThat(Orientation.swapsAxes(o) ? 3 : 2).isEqualTo(h);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 9, 65535})
  void orientationOutOfRangeIsUpright(int value) {
    assertThat(Orientation.normalize(value)).isEqualTo(1);
  }

  @Test
  void flattenFollowsTheIntegerFormulaForEveryAlphaAndColour() {
    for (int bg : new int[] {0, 128, 255}) {
      int background = bg << 16 | bg << 8 | bg;
      for (int a = 0; a < 256; a++) {
        for (int c = 0; c < 256; c++) {
          int out = AlphaFlatten.over(a << 24 | c << 16 | c << 8 | c, background);
          int want = a == 255 ? c : (a * c + (255 - a) * bg + 127) / 255;
          assertThat(out & 0xFF).isEqualTo(want);
          assertThat(out >>> 24).isEqualTo(255);
        }
      }
      assertThat(AlphaFlatten.over(0x00123456, background) & 0xFFFFFF).isEqualTo(background);
      assertThat(AlphaFlatten.over(0xFF123456, background)).isEqualTo(0xFF123456);
    }
  }

  @Test
  void sixteenBitSamplesRoundInsteadOfTruncating() {
    for (int v = 0; v < 65536; v++) {
      assertThat(SampleScale.to8(v)).isEqualTo((int) Math.round(v * 255.0 / 65535.0));
    }
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "foto|foto",
        "a<b>c:d|a_b_c_d",
        "ruta/con\\barras|ruta_con_barras",
        "..|imagen",
        "../../etc/passwd|_.._etc_passwd",
        "nombre. |nombre",
        "  con espacios  |con espacios",
        "CON|_CON",
        "con.txt|_con.txt",
        "Nul|_Nul",
        "COM1|_COM1",
        "lpt9.tar|_lpt9.tar",
        "fotó ñandú|fotó ñandú",
      })
  void sanitizeProducesPortableNames(String in, String expected) {
    assertThat(FileNames.sanitize(in)).isEqualTo(expected);
  }

  @Test
  void sanitizeTruncatesOnCodePointsAndKeepsTheExtension() {
    String emoji = "😀";
    String name = FileNames.withExtension(emoji.repeat(200), "jpg");
    assertThat(name).endsWith(".jpg");
    assertThat(name.getBytes(StandardCharsets.UTF_8).length)
        .isLessThanOrEqualTo(FileNames.MAX_BYTES);
    assertThat(name.codePoints().filter(cp -> Character.isSurrogate((char) cp))).isEmpty();
    assertThat(FileNames.sanitize("")).isEqualTo("imagen");
    assertThat(FileNames.sanitize("a\u0001b")).isEqualTo("a_b");
  }

  @Test
  void templateExpandsTokensWithoutReadingTheClock() {
    NameTemplate.Values v =
        new NameTemplate.Values(
            "playa", LocalDateTime.of(2026, 3, 9, 7, 5, 1), 12, 4000, 3000, FormatId.JPEG);
    assertThat(
            NameTemplate.fileName("{nombre}_{fecha}_{hora}_{n}_{ancho}x{alto}.{formato}{otro}", v))
        .isEqualTo("playa_2026-03-09_07-05-01_12_4000x3000.jpg{otro}.jpg");
    assertThat(NameTemplate.fileName("{nombre}:/{n}", v)).isEqualTo("playa__12.jpg");
  }

  @Test
  void memoryBudgetRejectsBeforeDecoding() throws Exception {
    long heap = 1536L << 20;
    MemoryBudget.require(6000, 4000, 4, 0, heap, 0);
    assertThatThrownBy(() -> MemoryBudget.require(60000, 60000, 4, 0, heap, 0))
        .isInstanceOf(ImagingException.TooLarge.class);
    assertThatThrownBy(() -> MemoryBudget.require(6000, 4000, 0, heap, heap, 0))
        .isInstanceOf(ImagingException.TooLarge.class);
    assertThatThrownBy(() -> MemoryBudget.require(6000, 4000, 0, 0, heap, 1000))
        .isInstanceOf(ImagingException.TooLarge.class);
    assertThatThrownBy(() -> MemoryBudget.require(65535, 65535, 0, 0, Long.MAX_VALUE, 0))
        .isInstanceOf(ImagingException.TooLarge.class);
  }

  private static long sizeModel(int q) {
    return 1000L + q * q * 10L;
  }

  @Test
  void qualitySearchFindsTheBestQualityDeterministically() throws Exception {
    List<Integer> tried = new ArrayList<>();
    QualitySearch.Result r =
        QualitySearch.search(
            95,
            30_000,
            q -> {
              tried.add(q);
              return sizeModel(q);
            });
    assertThat(r.quality()).isEqualTo(53);
    assertThat(sizeModel(r.quality())).isLessThanOrEqualTo(30_000);
    assertThat(sizeModel(r.quality() + 1)).isGreaterThan(30_000);
    assertThat(tried.size()).isLessThanOrEqualTo(2 + QualitySearch.MAX_ITERATIONS);
    List<Integer> again = new ArrayList<>();
    QualitySearch.search(
        95,
        30_000,
        q -> {
          again.add(q);
          return sizeModel(q);
        });
    assertThat(again).isEqualTo(tried);
  }

  @Test
  void qualitySearchKeepsTheRequestedQualityWhenItFits() throws Exception {
    assertThat(QualitySearch.search(80, 1_000_000, q -> 10).quality()).isEqualTo(80);
  }

  @Test
  void qualitySearchFailsWhenUnreachable() {
    assertThatThrownBy(() -> QualitySearch.search(90, 10, q -> 1000))
        .isInstanceOfSatisfying(
            ImagingException.EncodingFailure.class,
            e -> assertThat(e.messageKey()).isEqualTo("io.error.targetsize"));
  }

  private static byte[] header(String s) {
    return s.getBytes(StandardCharsets.ISO_8859_1);
  }

  @ParameterizedTest
  @CsvSource({
    "JPEG, a.png, ÿØÿà",
    "GIF, a.jpg, GIF89a",
    "BMP, a.gif, BMxxxx",
    "TIFF, a.png, II*\u0000xxxxxxxx",
    "WEBP, a.png, RIFFxxxxWEBPVP8L",
  })
  void formatComesFromTheContentNotTheName(FormatId expected, String name, String head)
      throws Exception {
    byte[] h = header(head);
    assertThat(FormatSniffer.detect(h, h.length, name)).isEqualTo(expected);
  }

  @Test
  void pngIsDetectedByItsSignature() throws Exception {
    byte[] h = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    assertThat(FormatSniffer.detect(h, h.length, "a.jpg")).isEqualTo(FormatId.PNG);
  }

  private static String keyOf(String head, String name) {
    byte[] h = header(head);
    try {
      FormatSniffer.detect(h, h.length, name);
      return "ok";
    } catch (ImagingException e) {
      return e.messageKey();
    }
  }

  @Test
  void unsupportedFamiliesGetTheirOwnMessage() {
    assertThat(keyOf("xxxxftypheic", "a.heic")).isEqualTo("io.error.heif");
    assertThat(keyOf("xxxxftypavif", "a.avif")).isEqualTo("io.error.heif");
    assertThat(keyOf("xxxxftypcrx ", "a.bin")).isEqualTo("io.error.raw");
    assertThat(keyOf("II*\u0000xxxxCR", "a.bin")).isEqualTo("io.error.raw");
    assertThat(keyOf("II*\u0000xxxx", "foto.NEF")).isEqualTo("io.error.raw");
    assertThat(keyOf("II+\u0000", "a.tif")).isEqualTo("io.error.bigtiff");
    assertThat(keyOf("nada", "a.png")).isEqualTo("io.error.unsupported");
    assertThat(keyOf("", "a.png")).isEqualTo("io.error.unsupported");
  }

  @Test
  void exportSettingsRejectInvalidCombinations() {
    ExportSettings ok = ExportSettings.defaults(FormatId.JPEG);
    assertThatThrownBy(() -> ok.withQuality(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ok.withQuality(101)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ok.withDeflateLevel(10)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ok.withBackground(0x1000000))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ExportSettings.defaults(FormatId.PNG).withMaxBytes(10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ExportSettings.defaults(FormatId.WEBP))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(ok.metadata()).isEqualTo(ExportSettings.MetadataPolicy.REMOVE_LOCATION);
  }
}
