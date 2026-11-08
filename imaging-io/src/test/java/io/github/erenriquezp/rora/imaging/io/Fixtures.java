package io.github.erenriquezp.rora.imaging.io;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ExportSettings;
import io.github.erenriquezp.rora.core.io.FormatId;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/** Archivos de prueba generados por código: sin fotos ni perfiles de terceros. */
final class Fixtures {

  static final double[][] P3_D50 = {
    {0.5151, 0.2920, 0.1571}, {0.2412, 0.6922, 0.0666}, {-0.0011, 0.0419, 0.7841}
  };
  static final double[][] SRGB_D50 = {
    {0.4360747, 0.3850649, 0.1430804},
    {0.2225045, 0.7168786, 0.0606169},
    {0.0139322, 0.0971045, 0.7141733}
  };
  static final double[][] P3_TO_SRGB = mul(inv(SRGB_D50), P3_D50);

  // Marcadores que no deben aparecer en una salida sin esos metadatos.
  static final String SERIAL = "SERIE-PRIVADA-123456";
  static final String MAKER_NOTE = "MAKERNOTE-OPACA-XYZ";
  static final String COMMENT = "COMENTARIO-PRIVADO";
  static final String THUMBNAIL = "MINIATURA-DATOS-ABCDEFGH";
  static final String XMP_GPS = "40,25.206N";
  static final String IPTC_CITY = "CiudadSecreta";
  static final String PNG_COMMENT = "NOTA-PNG-PRIVADA";
  static final byte[] GPS_RATIONALS =
      ByteBuffer.allocate(24)
          .order(ByteOrder.LITTLE_ENDIAN)
          .putInt(40)
          .putInt(1)
          .putInt(25)
          .putInt(1)
          .putInt(1234)
          .putInt(100)
          .array();

  private Fixtures() {}

  static double eotf(double v) {
    return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  }

  static double oetf(double v) {
    return v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
  }

  static double[][] inv(double[][] m) {
    double a = m[0][0], b = m[0][1], c = m[0][2], d = m[1][0], e = m[1][1], f = m[1][2];
    double g = m[2][0], h = m[2][1], i = m[2][2];
    double det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
    return new double[][] {
      {(e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det},
      {(f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det},
      {(d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det}
    };
  }

  static double[][] mul(double[][] a, double[][] b) {
    double[][] r = new double[3][3];
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++) {
        for (int k = 0; k < 3; k++) {
          r[i][j] += a[i][k] * b[k][j];
        }
      }
    }
    return r;
  }

  /** P3 → sRGB por cálculo analítico, con recorte por canal. */
  static int[] analytic(int r, int g, int b) {
    double[] l = {eotf(r / 255.0), eotf(g / 255.0), eotf(b / 255.0)};
    int[] o = new int[3];
    for (int i = 0; i < 3; i++) {
      double v = P3_TO_SRGB[i][0] * l[0] + P3_TO_SRGB[i][1] * l[1] + P3_TO_SRGB[i][2] * l[2];
      o[i] = (int) Math.round(oetf(Math.min(1, Math.max(0, v))) * 255);
    }
    return o;
  }

  private static byte[] ascii(String s) {
    return s.getBytes(StandardCharsets.ISO_8859_1);
  }

  private static byte[] s15(double... v) {
    ByteBuffer b = ByteBuffer.allocate(v.length * 4);
    for (double d : v) {
      b.putInt((int) Math.round(d * 65536));
    }
    return b.array();
  }

  private static byte[] xyzTag(double x, double y, double z) {
    return ByteBuffer.allocate(20).put(ascii("XYZ ")).putInt(0).put(s15(x, y, z)).array();
  }

  private static byte[] curve(int points) {
    ByteBuffer b = ByteBuffer.allocate(12 + 2 * points);
    b.put(ascii("curv")).putInt(0).putInt(points);
    for (int i = 0; i < points; i++) {
      b.putShort((short) Math.round(eotf(i / (points - 1.0)) * 65535));
    }
    return b.array();
  }

  private static byte[] gammaCurve(double gamma) {
    return ByteBuffer.allocate(14)
        .put(ascii("curv"))
        .putInt(0)
        .putInt(1)
        .putShort((short) Math.round(gamma * 256))
        .array();
  }

  private static byte[] textTag(String s) {
    byte[] t = ascii(s);
    return ByteBuffer.allocate(8 + t.length + 1).put(ascii("text")).putInt(0).put(t).array();
  }

  private static byte[] descTag(String s) {
    byte[] t = ascii(s);
    return ByteBuffer.allocate(12 + t.length + 1 + 8 + 3 + 67)
        .put(ascii("desc"))
        .putInt(0)
        .putInt(t.length + 1)
        .put(t)
        .array();
  }

  private static byte[] profile(String cls, String space, String pcs, Map<String, byte[]> tags)
      throws IOException {
    int tableEnd = 128 + 4 + 12 * tags.size();
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    ByteBuffer table = ByteBuffer.allocate(4 + 12 * tags.size());
    table.putInt(tags.size());
    for (Map.Entry<String, byte[]> e : tags.entrySet()) {
      while (body.size() % 4 != 0) {
        body.write(0);
      }
      table.put(ascii(e.getKey())).putInt(tableEnd + body.size()).putInt(e.getValue().length);
      body.write(e.getValue());
    }
    int size = tableEnd + body.size();
    ByteBuffer h = ByteBuffer.allocate(128);
    h.putInt(size).putInt(0).putInt(0x02400000).put(ascii(cls)).put(ascii(space)).put(ascii(pcs));
    h.putShort((short) 2024).putShort((short) 1).putShort((short) 1);
    h.putShort((short) 0).putShort((short) 0).putShort((short) 0);
    h.put(ascii("acsp"));
    h.position(68);
    h.put(s15(0.9642, 1.0, 0.8249));
    return concat(h.array(), table.array(), body.toByteArray());
  }

  /** Perfil RGB matriz/TRC con la curva sRGB. */
  static byte[] rgbProfile(String name, double[][] m) throws IOException {
    Map<String, byte[]> t = new LinkedHashMap<>();
    byte[] c = curve(1024);
    t.put("desc", descTag(name));
    t.put("cprt", textTag("Generated for tests"));
    t.put("wtpt", xyzTag(0.9642, 1.0, 0.8249));
    t.put("rXYZ", xyzTag(m[0][0], m[1][0], m[2][0]));
    t.put("gXYZ", xyzTag(m[0][1], m[1][1], m[2][1]));
    t.put("bXYZ", xyzTag(m[0][2], m[1][2], m[2][2]));
    t.put("rTRC", c);
    t.put("gTRC", c);
    t.put("bTRC", c);
    return profile("mntr", "RGB ", "XYZ ", t);
  }

  /** Perfil de gris con gamma pura. */
  static byte[] grayProfile(String name, double gamma) throws IOException {
    Map<String, byte[]> t = new LinkedHashMap<>();
    t.put("desc", descTag(name));
    t.put("cprt", textTag("Generated for tests"));
    t.put("wtpt", xyzTag(0.9642, 1.0, 0.8249));
    t.put("kTRC", gammaCurve(gamma));
    return profile("mntr", "GRAY", "XYZ ", t);
  }

  /** Lab (D50) codificado en 8 bits para un mft1, a partir de un sRGB lineal. */
  private static int[] labFromSrgb(int r, int g, int b) {
    double[] l = {eotf(r / 255.0), eotf(g / 255.0), eotf(b / 255.0)};
    double x = SRGB_D50[0][0] * l[0] + SRGB_D50[0][1] * l[1] + SRGB_D50[0][2] * l[2];
    double y = SRGB_D50[1][0] * l[0] + SRGB_D50[1][1] * l[1] + SRGB_D50[1][2] * l[2];
    double z = SRGB_D50[2][0] * l[0] + SRGB_D50[2][1] * l[1] + SRGB_D50[2][2] * l[2];
    double fx = f(x / 0.9642);
    double fy = f(y);
    double fz = f(z / 0.8249);
    double ll = 116 * fy - 16;
    double aa = 500 * (fx - fy);
    double bb = 200 * (fy - fz);
    return new int[] {
      (int) Math.round(ll * 255 / 100), (int) Math.round(aa + 128), (int) Math.round(bb + 128)
    };
  }

  private static double f(double t) {
    return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116;
  }

  /** Color de cada esquina CMYK en el perfil de prueba; el cian es distinto del de la fórmula. */
  static int[] cmykCornerRgb(int c, int m, int y, int k) {
    if (k == 1) {
      return new int[] {0, 0, 0};
    }
    if (c == 1 && m == 0 && y == 0) {
      return new int[] {10, 130, 200};
    }
    return new int[] {(1 - c) * 255, (1 - m) * 255, (1 - y) * 255};
  }

  /** Perfil CMYK con una tabla A2B0 de 2 puntos por eje. */
  static byte[] cmykProfile(String name) throws IOException {
    ByteBuffer lut = ByteBuffer.allocate(48 + 256 * 4 + 16 * 3 + 256 * 3);
    lut.put(ascii("mft1")).putInt(0).put((byte) 4).put((byte) 3).put((byte) 2).put((byte) 0);
    lut.put(s15(1, 0, 0, 0, 1, 0, 0, 0, 1));
    for (int ch = 0; ch < 4; ch++) {
      for (int i = 0; i < 256; i++) {
        lut.put((byte) i);
      }
    }
    for (int c = 0; c < 2; c++) {
      for (int m = 0; m < 2; m++) {
        for (int y = 0; y < 2; y++) {
          for (int k = 0; k < 2; k++) {
            int[] rgb = cmykCornerRgb(c, m, y, k);
            for (int v : labFromSrgb(rgb[0], rgb[1], rgb[2])) {
              lut.put((byte) v);
            }
          }
        }
      }
    }
    for (int ch = 0; ch < 3; ch++) {
      for (int i = 0; i < 256; i++) {
        lut.put((byte) i);
      }
    }
    Map<String, byte[]> t = new LinkedHashMap<>();
    t.put("desc", descTag(name));
    t.put("cprt", textTag("Generated for tests"));
    t.put("wtpt", xyzTag(0.9642, 1.0, 0.8249));
    t.put("A2B0", lut.array());
    t.put("B2A0", lut.array());
    return profile("prtr", "CMYK", "Lab ", t);
  }

  static byte[] concat(byte[]... p) throws IOException {
    ByteArrayOutputStream o = new ByteArrayOutputStream();
    for (byte[] x : p) {
      o.write(x);
    }
    return o.toByteArray();
  }

  static byte[] encode(BufferedImage b, String format) throws IOException {
    ByteArrayOutputStream o = new ByteArrayOutputStream();
    if (!ImageIO.write(b, format, o)) {
      throw new IOException("sin escritor " + format);
    }
    return o.toByteArray();
  }

  /** JPEG de máxima calidad y 4:4:4, sin metadatos, por el camino de producción. */
  static byte[] bestJpeg(Path dir, Img image) throws Exception {
    Path f = dir.resolve("best-" + System.nanoTime() + ".jpg");
    new JpegEncoder()
        .encode(
            image,
            ExportSettings.defaults(FormatId.JPEG)
                .withQuality(100)
                .withJpeg(false, ExportSettings.Subsampling.S444, true)
                .withProfile(ExportSettings.ProfilePolicy.UNTAGGED),
            ImageMetadata.EMPTY,
            f,
            CancellationToken.none(),
            Progress.NONE);
    byte[] out = Files.readAllBytes(f);
    Files.delete(f);
    return out;
  }

  static byte[] seg(int marker, byte[] payload) {
    ByteBuffer b = ByteBuffer.allocate(4 + payload.length);
    b.put((byte) 0xFF).put((byte) marker).putShort((short) (payload.length + 2)).put(payload);
    return b.array();
  }

  static byte[] insertJpeg(byte[] jpg, byte[]... segs) throws IOException {
    int pos = 2;
    if ((jpg[2] & 255) == 0xFF && (jpg[3] & 255) == 0xE0) {
      pos = 4 + ((jpg[4] & 255) << 8 | (jpg[5] & 255));
    }
    return concat(Arrays.copyOf(jpg, pos), concat(segs), Arrays.copyOfRange(jpg, pos, jpg.length));
  }

  static byte[] iccSeg(byte[] icc) throws IOException {
    return seg(0xE2, concat(ascii("ICC_PROFILE\0"), new byte[] {1, 1}, icc));
  }

  static String xmpPacket() {
    return "<x:xmpmeta xmlns:x='adobe:ns:meta/'><rdf:RDF"
        + " xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'><rdf:Description"
        + " xmlns:exif='http://ns.adobe.com/exif/1.0/' exif:GPSLatitude='"
        + XMP_GPS
        + "'/></rdf:RDF></x:xmpmeta>";
  }

  static byte[] xmpSeg(String xml) throws IOException {
    return seg(
        0xE1,
        concat(ascii("http://ns.adobe.com/xap/1.0/\0"), xml.getBytes(StandardCharsets.UTF_8)));
  }

  static byte[] iptcSeg() throws IOException {
    byte[] city = ascii(IPTC_CITY);
    byte[] iim = concat(new byte[] {0x1C, 2, 90, 0, (byte) city.length}, city);
    int pad = iim.length & 1;
    ByteBuffer r = ByteBuffer.allocate(12 + iim.length + pad);
    r.put(ascii("8BIM")).putShort((short) 0x0404).putShort((short) 0).putInt(iim.length).put(iim);
    return seg(0xED, concat(ascii("Photoshop 3.0\0"), r.array()));
  }

  /** EXIF LE completo: Orientation, cámara, GPS, MakerNote, nº de serie y miniatura en IFD1. */
  static byte[] exifBlob(int orientation) {
    ByteBuffer b = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
    b.put(ascii("II")).putShort((short) 42).putInt(8);
    int ifd0 = 8;
    int nIfd0 = 5;
    int exif = ifd0 + 2 + 12 * nIfd0 + 4;
    int nExif = 4;
    int gps = exif + 2 + 12 * nExif + 4;
    int nGps = 2;
    int ifd1 = gps + 2 + 12 * nGps + 4;
    int nIfd1 = 2;
    int data = ifd1 + 2 + 12 * nIfd1 + 4;
    int[] d = {data};

    byte[] make = ascii("Marca\0");
    byte[] model = ascii("Modelo-Cam\0");
    byte[] comment = concat2(ascii("ASCII\0\0\0"), ascii(COMMENT));
    byte[] maker = ascii(MAKER_NOTE);
    byte[] serial = ascii(SERIAL + "\0");
    byte[] thumb = ascii(THUMBNAIL);

    b.position(ifd0);
    b.putShort((short) nIfd0);
    tagData(b, 0x010F, 2, make.length, make, d);
    tagData(b, 0x0110, 2, model.length, model, d);
    tagInline(b, 0x0112, 3, 1, orientation);
    tagInline(b, 0x8769, 4, 1, exif);
    tagInline(b, 0x8825, 4, 1, gps);
    b.putInt(ifd1);

    b.position(exif);
    b.putShort((short) nExif);
    tagInline(b, 0x8827, 3, 1, 400);
    tagData(b, 0x927C, 7, maker.length, maker, d);
    tagData(b, 0x9286, 7, comment.length, comment, d);
    tagData(b, 0xA431, 2, serial.length, serial, d);
    b.putInt(0);

    b.position(gps);
    b.putShort((short) nGps);
    tagInline(b, 1, 2, 2, 'N');
    tagData(b, 2, 5, 3, GPS_RATIONALS, d);
    b.putInt(0);

    b.position(ifd1);
    b.putShort((short) nIfd1);
    int tAt = d[0];
    b.position(tAt);
    b.put(thumb);
    d[0] += thumb.length;
    b.position(ifd1 + 2);
    tagInline(b, 0x0201, 4, 1, tAt);
    tagInline(b, 0x0202, 4, 1, thumb.length);
    b.putInt(0);
    return Arrays.copyOf(b.array(), d[0]);
  }

  private static byte[] concat2(byte[] a, byte[] b) {
    byte[] o = Arrays.copyOf(a, a.length + b.length);
    System.arraycopy(b, 0, o, a.length, b.length);
    return o;
  }

  private static void tagInline(ByteBuffer b, int tag, int type, int n, int v) {
    b.putShort((short) tag).putShort((short) type).putInt(n);
    if (type == 3) {
      b.putShort((short) v).putShort((short) 0);
    } else {
      b.putInt(v);
    }
  }

  private static void tagData(ByteBuffer b, int tag, int type, int n, byte[] v, int[] d) {
    b.putShort((short) tag).putShort((short) type).putInt(n).putInt(d[0]);
    int resume = b.position();
    b.position(d[0]);
    b.put(v);
    d[0] += (v.length + 1) & ~1;
    b.position(resume);
  }

  static byte[] exifSeg(int orientation) throws IOException {
    return seg(0xE1, concat(ascii("Exif\0\0"), exifBlob(orientation)));
  }

  static byte[] pngChunk(String t, byte[] d) {
    ByteBuffer b = ByteBuffer.allocate(12 + d.length);
    b.putInt(d.length).put(ascii(t)).put(d);
    CRC32 c = new CRC32();
    c.update(b.array(), 4, 4 + d.length);
    b.putInt((int) c.getValue());
    return b.array();
  }

  static byte[] insertPng(byte[] png, byte[]... chunks) throws IOException {
    return concat(Arrays.copyOf(png, 33), concat(chunks), Arrays.copyOfRange(png, 33, png.length));
  }

  static byte[] deflate(byte[] x) {
    Deflater d = new Deflater();
    d.setInput(x);
    d.finish();
    byte[] buf = new byte[x.length + 1024];
    int n = d.deflate(buf);
    d.end();
    return Arrays.copyOf(buf, n);
  }

  static byte[] iccp(byte[] icc) throws IOException {
    return pngChunk("iCCP", concat(ascii("test\0\0"), deflate(icc)));
  }

  static byte[] itxtXmp(String xml) throws IOException {
    return pngChunk(
        "iTXt", concat(ascii("XML:com.adobe.xmp\0\0\0\0\0"), xml.getBytes(StandardCharsets.UTF_8)));
  }

  /** TIFF RGB mínimo de 4×3 con Orientation y un IFD GPS. */
  static byte[] tiffWithExif(int orientation) {
    int w = 4;
    int h = 3;
    int n = 11;
    int ifd = 8;
    int gps = ifd + 2 + 12 * n + 4;
    int nGps = 2;
    int bps = gps + 2 + 12 * nGps + 4;
    int rat = bps + 6;
    int strip = rat + 24;
    ByteBuffer b = ByteBuffer.allocate(strip + w * h * 3).order(ByteOrder.LITTLE_ENDIAN);
    b.put(ascii("II")).putShort((short) 42).putInt(ifd);
    b.position(ifd);
    b.putShort((short) n);
    tagInline(b, 256, 3, 1, w);
    tagInline(b, 257, 3, 1, h);
    tagInline(b, 258, 3, 3, bps);
    tagInline(b, 259, 3, 1, 1);
    tagInline(b, 262, 3, 1, 2);
    tagInline(b, 273, 4, 1, strip);
    tagInline(b, 274, 3, 1, orientation);
    tagInline(b, 277, 3, 1, 3);
    tagInline(b, 278, 3, 1, h);
    tagInline(b, 279, 4, 1, w * h * 3);
    tagInline(b, 0x8825, 4, 1, gps);
    b.putInt(0);
    b.position(gps);
    b.putShort((short) nGps);
    tagInline(b, 1, 2, 2, 'N');
    b.putShort((short) 2).putShort((short) 5).putInt(3).putInt(rat);
    b.putInt(0);
    b.position(bps);
    b.putShort((short) 8).putShort((short) 8).putShort((short) 8);
    b.position(rat);
    b.put(GPS_RATIONALS);
    b.position(strip);
    for (int i = 0; i < w * h; i++) {
      b.put((byte) (i * 20)).put((byte) (200 - i * 10)).put((byte) 77);
    }
    return b.array();
  }

  /** ARGB ruidoso: uno de cada cuatro píxeles con alfa 0 y color distinto de 0. */
  static Img noiseArgb(int w, int h, long seed) {
    int[] d = new int[w * h];
    SplittableRandom r = new SplittableRandom(seed);
    for (int i = 0; i < d.length; i++) {
      int v = r.nextInt();
      d[i] = i % 4 == 0 ? v & 0x00FFFFFF : v;
    }
    return Img.adopt(w, h, d);
  }

  /** Degradado suave y opaco con algo de ruido, parecido a una foto. */
  static Img photo(int w, int h) {
    int[] d = new int[w * h];
    SplittableRandom rnd = new SplittableRandom(3);
    for (int y = 0, i = 0; y < h; y++) {
      for (int x = 0; x < w; x++, i++) {
        int n = rnd.nextInt(9) - 4;
        d[i] =
            0xFF000000
                | clamp(x * 255 / w + n) << 16
                | clamp(y * 255 / h + n) << 8
                | clamp(128 + (int) (90 * Math.sin((x + y) / 37.0)) + n);
      }
    }
    return Img.adopt(w, h, d);
  }

  private static int clamp(int v) {
    return v < 0 ? 0 : Math.min(255, v);
  }

  static BufferedImage toRgb(Img img) {
    BufferedImage b = new BufferedImage(img.width(), img.height(), BufferedImage.TYPE_INT_RGB);
    img.copyTo(((DataBufferInt) b.getRaster().getDataBuffer()).getData());
    return b;
  }

  /** JPEG de 4 bandas sin conversión, con el marcador Adobe indicado. */
  static byte[] fourBandJpeg(byte[] pix, int w, int h, int adobeTransform) throws Exception {
    DataBuffer db = new DataBufferByte(pix, pix.length);
    WritableRaster r =
        Raster.createInterleavedRaster(db, w, h, w * 4, 4, new int[] {0, 1, 2, 3}, new Point());
    ImageWriter wr = ImageIO.getImageWritersByFormatName("jpeg").next();
    ByteArrayOutputStream o = new ByteArrayOutputStream();
    try (ImageOutputStream ios = ImageIO.createImageOutputStream(o)) {
      wr.setOutput(ios);
      wr.write(null, new IIOImage(r, null, null), null);
    } finally {
      wr.dispose();
    }
    byte[] j = o.toByteArray();
    if (adobeTransform < 0) {
      return j;
    }
    byte[] adobe =
        seg(0xEE, concat(ascii("Adobe"), new byte[] {0, 100, 0, 0, 0, 0, (byte) adobeTransform}));
    return concat(Arrays.copyOf(j, 2), adobe, Arrays.copyOfRange(j, 2, j.length));
  }

  static int count(byte[] h, byte[] n) {
    int c = 0;
    outer:
    for (int i = 0; i <= h.length - n.length; i++) {
      for (int j = 0; j < n.length; j++) {
        if (h[i + j] != n[j]) {
          continue outer;
        }
      }
      c++;
    }
    return c;
  }

  static int count(byte[] h, String n) {
    return count(h, ascii(n));
  }
}
