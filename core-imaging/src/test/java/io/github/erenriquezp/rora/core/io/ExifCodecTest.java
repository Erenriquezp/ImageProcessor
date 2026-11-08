package io.github.erenriquezp.rora.core.io;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.io.ExportSettings.MetadataPolicy;
import io.github.erenriquezp.rora.core.io.ImageMetadata.ExifData;
import io.github.erenriquezp.rora.core.io.ImageMetadata.ExifTag;
import java.awt.color.ICC_Profile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Tag("property")
class ExifCodecTest {

  private record E(int tag, int type, int count, byte[] value) {}

  private static E ascii(int tag, String s) {
    byte[] v = (s + "\0").getBytes(StandardCharsets.US_ASCII);
    return new E(tag, 2, v.length, v);
  }

  private static byte[] shortValue(ByteOrder o, int v) {
    return ByteBuffer.allocate(2).order(o).putShort((short) v).array();
  }

  private static byte[] longValue(ByteOrder o, int v) {
    return ByteBuffer.allocate(4).order(o).putInt(v).array();
  }

  /** Bloque TIFF con IFD0 → (ExifIFD, GPS) y, si {@code thumbnail}, IFD1 con miniatura. */
  private static byte[] blob(ByteOrder o, int orientation, boolean gps, boolean thumbnail) {
    List<E> ifd0 = new ArrayList<>();
    ifd0.add(ascii(0x010E, "descripcion privada"));
    ifd0.add(ascii(0x010F, "Marca"));
    ifd0.add(ascii(0x0110, "Modelo X"));
    ifd0.add(new E(0x0112, 3, 1, shortValue(o, orientation)));
    ifd0.add(ascii(0x0131, "OtroPrograma 1.0"));
    int exifPointer = ifd0.size();
    ifd0.add(new E(0x8769, 4, 1, new byte[4]));
    int gpsPointer = gps ? ifd0.size() : -1;
    if (gps) {
      ifd0.add(new E(0x8825, 4, 1, new byte[4]));
    }
    List<E> exif = new ArrayList<>();
    exif.add(new E(0x8827, 3, 1, shortValue(o, 400)));
    exif.add(new E(0x927C, 7, 8, "MAKERNOT".getBytes(StandardCharsets.US_ASCII)));
    exif.add(ascii(0x9003, "2024:01:02 03:04:05"));
    exif.add(ascii(0x9011, "+02:00"));
    exif.add(ascii(0xA431, "SERIE123456"));
    List<E> gpsTags = new ArrayList<>();
    gpsTags.add(new E(1, 2, 2, "N\0".getBytes(StandardCharsets.US_ASCII)));
    ByteBuffer lat = ByteBuffer.allocate(24).order(o);
    lat.putInt(40).putInt(1).putInt(25).putInt(1).putInt(1234).putInt(100);
    gpsTags.add(new E(2, 5, 3, lat.array()));

    ByteBuffer b = ByteBuffer.allocate(2048).order(o);
    b.put((byte) (o == ByteOrder.LITTLE_ENDIAN ? 'I' : 'M'));
    b.put((byte) (o == ByteOrder.LITTLE_ENDIAN ? 'I' : 'M'));
    b.putShort((short) 42).putInt(8);
    int exifAt = 8 + 2 + 12 * ifd0.size() + 4;
    int gpsAt = exifAt + 2 + 12 * exif.size() + 4;
    int ifd1At = gpsAt + (gps ? 2 + 12 * gpsTags.size() + 4 : 0);
    int dataAt = ifd1At + (thumbnail ? 2 + 12 * 2 + 4 : 0);
    ifd0.set(exifPointer, new E(0x8769, 4, 1, longValue(o, exifAt)));
    if (gps) {
      ifd0.set(gpsPointer, new E(0x8825, 4, 1, longValue(o, gpsAt)));
    }
    int[] data = {dataAt};
    writeIfd(b, 8, ifd0, data, thumbnail ? ifd1At : 0);
    writeIfd(b, exifAt, exif, data, 0);
    if (gps) {
      writeIfd(b, gpsAt, gpsTags, data, 0);
    }
    if (thumbnail) {
      byte[] thumb = {(byte) 0xFF, (byte) 0xD8, 9, 8, 7};
      List<E> ifd1 = new ArrayList<>();
      ifd1.add(new E(0x0201, 4, 1, longValue(o, data[0])));
      ifd1.add(new E(0x0202, 4, 1, longValue(o, thumb.length)));
      b.position(data[0]);
      b.put(thumb);
      data[0] += thumb.length;
      writeIfd(b, ifd1At, ifd1, data, 0);
    }
    return java.util.Arrays.copyOf(b.array(), data[0]);
  }

  private static void writeIfd(ByteBuffer b, int at, List<E> tags, int[] data, int next) {
    b.position(at);
    b.putShort((short) tags.size());
    for (E t : tags) {
      b.putShort((short) t.tag()).putShort((short) t.type()).putInt(t.count());
      if (t.value().length <= 4) {
        b.put(java.util.Arrays.copyOf(t.value(), 4));
      } else {
        int resume = b.position();
        b.position(resume);
        b.putInt(data[0]);
        int after = b.position();
        b.position(data[0]);
        b.put(t.value());
        data[0] += (t.value().length + 1) & ~1;
        b.position(after);
      }
    }
    b.putInt(next);
  }

  private static List<Integer> ids(List<ExifTag> tags) {
    return tags.stream().map(ExifTag::id).toList();
  }

  @ParameterizedTest
  @EnumSource(names = {"LITTLE", "BIG"})
  void parsesOnlyTheWhitelistInBothByteOrders(Endian e) {
    ExifData d = ExifCodec.parse(blob(e.order, 6, true, true)).orElseThrow();
    assertThat(d.orientation()).isEqualTo(6);
    assertThat(ids(d.ifd0())).containsExactly(0x010E, 0x010F, 0x0110);
    assertThat(ids(d.exif())).containsExactly(0x8827, 0x9003, 0x9011, 0xA431);
    assertThat(ids(d.gps())).containsExactly(1, 2);
  }

  enum Endian {
    LITTLE(ByteOrder.LITTLE_ENDIAN),
    BIG(ByteOrder.BIG_ENDIAN);

    final ByteOrder order;

    Endian(ByteOrder o) {
      order = o;
    }
  }

  private static boolean contains(byte[] hay, String needle) {
    return new String(hay, StandardCharsets.ISO_8859_1).contains(needle);
  }

  @Test
  void writtenExifIsUprightHasNewDimensionsAndNoThumbnailOrMakerNote() {
    ExifData d = ExifCodec.parse(blob(ByteOrder.BIG_ENDIAN, 6, true, true)).orElseThrow();
    byte[] out = ExifCodec.write(d, 1234, 567);
    ExifData back = ExifCodec.parse(out).orElseThrow();
    assertThat(back.orientation()).isEqualTo(1);
    assertThat(ids(back.gps())).containsExactly(1, 2);
    assertThat(exifIfdValue(out, 0xA002)).isEqualTo(1234);
    assertThat(exifIfdValue(out, 0xA003)).isEqualTo(567);
    assertThat(exifIfdValue(out, 0xA001)).isEqualTo(1);
    assertThat(contains(out, "MAKERNOT")).isFalse();
    assertThat(contains(out, "OtroPrograma")).isFalse();
    assertThat(contains(out, String.valueOf(new char[] {0xFF, 0xD8, 9}))).isFalse();
    ByteBuffer lat = ByteBuffer.wrap(back.gps().get(1).value()).order(ByteOrder.LITTLE_ENDIAN);
    assertThat(lat.getInt(8)).isEqualTo(25);
  }

  /** Valor en línea de una etiqueta del ExifIFD de un bloque little-endian. */
  private static int exifIfdValue(byte[] tiff, int tag) {
    ByteBuffer b = ByteBuffer.wrap(tiff).order(ByteOrder.LITTLE_ENDIAN);
    int exif = find(b, 8, 0x8769);
    return find(b, exif, tag);
  }

  private static int find(ByteBuffer b, int ifd, int tag) {
    int n = b.getShort(ifd) & 0xFFFF;
    for (int i = 0; i < n; i++) {
      int at = ifd + 2 + 12 * i;
      if ((b.getShort(at) & 0xFFFF) == tag) {
        return (b.getShort(at + 2) & 0xFFFF) == 3 ? b.getShort(at + 8) & 0xFFFF : b.getInt(at + 8);
      }
    }
    throw new AssertionError("sin etiqueta " + tag);
  }

  private static ImageMetadata metadata(byte[] tiff) {
    return new ImageMetadata(
        ExifCodec.parse(tiff),
        Optional.of(new byte[] {1}),
        Optional.of(new byte[] {2}),
        Optional.of(new byte[] {3}),
        Map.of("Title", "t", "Comment", "secreto"),
        false);
  }

  @Test
  void removeLocationBuildsFromTheOpenListOnly() {
    ImageMetadata m = metadata(blob(ByteOrder.LITTLE_ENDIAN, 1, true, true));
    ImageMetadata out = MetadataPolicyApplier.apply(m, MetadataPolicy.REMOVE_LOCATION);
    ExifData e = out.exif().orElseThrow();
    assertThat(e.gps()).isEmpty();
    assertThat(ids(e.ifd0())).containsExactly(0x010F, 0x0110);
    assertThat(ids(e.exif())).containsExactly(0x8827, 0x9003);
    assertThat(out.xmp()).isEmpty();
    assertThat(out.iptc()).isEmpty();
    assertThat(out.icc()).isEmpty();
    assertThat(out.text()).isEmpty();
  }

  @Test
  void removeAllLeavesNothingAndKeepRetainsOnlyValidatedData() {
    ImageMetadata m = metadata(blob(ByteOrder.LITTLE_ENDIAN, 1, true, true));
    ImageMetadata none = MetadataPolicyApplier.apply(m, MetadataPolicy.REMOVE_ALL);
    assertThat(none.exif()).isEmpty();
    assertThat(none.xmp()).isEmpty();
    assertThat(none.text()).isEmpty();
    ImageMetadata keep = MetadataPolicyApplier.apply(m, MetadataPolicy.KEEP);
    assertThat(ids(keep.exif().orElseThrow().gps())).containsExactly(1, 2);
    assertThat(keep.xmp()).isPresent();
    assertThat(keep.icc()).isEmpty();
    assertThat(keep.text()).containsOnlyKeys("Title");
  }

  private static void assertRejected(byte[] tiff) {
    assertThat(ExifCodec.parse(tiff)).isEmpty();
  }

  private static byte[] minimal(int count, int entryTag, int type, int cnt, int value) {
    ByteBuffer b = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
    b.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
    b.putShort((short) count);
    b.putShort((short) entryTag).putShort((short) type).putInt(cnt).putInt(value);
    b.putInt(0);
    return b.array();
  }

  @Test
  void anyStructuralAnomalyDiscardsTheWholeBlock() {
    assertRejected(new byte[0]);
    assertRejected("XX*\0\0\0\0\0".getBytes(StandardCharsets.ISO_8859_1));
    assertRejected(minimal(1, 0x010F, 2, 1000, 40)); // valor fuera del bloque
    assertRejected(minimal(1, 0x010F, 0, 1, 0)); // tipo 0
    assertRejected(minimal(1, 0x010F, 13, 1, 0)); // tipo desconocido
    assertRejected(minimal(9000, 0x010F, 2, 1, 0)); // demasiadas entradas
    assertRejected(minimal(1, 0x8769, 3, 1, 8)); // puntero con tipo incorrecto
    assertRejected(minimal(1, 0x8769, 4, 1, 8)); // ciclo: ExifIFD apunta a IFD0
    assertRejected(minimal(1, 0x8769, 4, 1, 0x7FFFFFF0)); // puntero fuera
    ByteBuffer dup = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
    dup.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8).putShort((short) 2);
    for (int i = 0; i < 2; i++) {
      dup.putShort((short) 0x0112).putShort((short) 3).putInt(1).putInt(1);
    }
    assertRejected(dup.array()); // etiqueta repetida
  }

  @Test
  void aWellFormedBlockWithoutInterestingTagsIsEmpty() {
    ExifData d = ExifCodec.parse(minimal(1, 0x0100, 4, 1, 42)).orElseThrow();
    assertThat(d.isEmpty()).isTrue();
    assertThat(d.orientation()).isEqualTo(1);
  }

  @Test
  void iptcKeepsOnlyWellFormedRecords() {
    byte[] iim = {0x1C, 2, 80, 0, 2, 'a', 'b'};
    byte[] app13 = Iptc.toApp13(iim);
    byte[] body = java.util.Arrays.copyOfRange(app13, Iptc.APP13_ID.length, app13.length);
    assertThat(Iptc.fromResources(body)).hasValueSatisfying(v -> assertThat(v).isEqualTo(iim));
    assertThat(Iptc.valid(new byte[] {0x1C, 2, 80, (byte) 0x80, 2, 1})).isFalse();
    assertThat(Iptc.valid(new byte[] {0x55})).isFalse();
    assertThat(Iptc.valid(new byte[] {0x1C, 2, 80, 0, 9, 'a'})).isFalse();
    assertThat(Iptc.fromResources(new byte[] {'X', 'B', 'I', 'M', 0, 0, 0, 0, 0, 0, 0, 0}))
        .isEmpty();
  }

  @Test
  void generatedProfileIsAValidStableSrgbDescription() {
    byte[] a = SrgbProfile.bytes();
    assertThat(a).isEqualTo(SrgbProfile.bytes());
    assertThat(ICC_Profile.getInstance(a).getNumComponents()).isEqualTo(3);
    assertThat(IccInfo.description(a)).contains("sRGB");
    assertThat(IccInfo.description(new byte[10])).isEmpty();
  }
}
