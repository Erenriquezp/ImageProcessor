package io.github.erenriquezp.rora.core.io;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Perfil sRGB matriz/TRC (ICC v2) generado por código: no se redistribuye ningún perfil ajeno. */
public final class SrgbProfile {

  private static final int CURVE_POINTS = 1024;
  private static final byte[] BYTES = build();

  private SrgbProfile() {}

  /** Bytes del perfil. */
  public static byte[] bytes() {
    return BYTES.clone();
  }

  private static byte[] build() {
    byte[] desc = desc("sRGB");
    byte[] cprt = text("No copyright");
    byte[] wtpt = xyz(0.9642, 1.0, 0.8249);
    byte[] rxyz = xyz(0.4360747, 0.2225045, 0.0139322);
    byte[] gxyz = xyz(0.3850649, 0.7168786, 0.0971045);
    byte[] bxyz = xyz(0.1430804, 0.0606169, 0.7141733);
    byte[] trc = curve();
    String[] names = {"desc", "cprt", "wtpt", "rXYZ", "gXYZ", "bXYZ"};
    byte[][] data = {desc, cprt, wtpt, rxyz, gxyz, bxyz};
    int tags = names.length + 3;
    int table = 128 + 4 + 12 * tags;
    int[] offsets = new int[names.length];
    int pos = table;
    for (int i = 0; i < data.length; i++) {
      offsets[i] = pos;
      pos = align(pos + data[i].length);
    }
    int trcOff = pos;
    int total = align(trcOff + trc.length);

    ByteBuffer b = ByteBuffer.allocate(total);
    b.putInt(total).putInt(0).putInt(0x02400000);
    b.put(ascii("mntr")).put(ascii("RGB ")).put(ascii("XYZ "));
    b.putShort((short) 2024).putShort((short) 1).putShort((short) 1);
    b.putShort((short) 0).putShort((short) 0).putShort((short) 0);
    b.put(ascii("acsp"));
    b.position(68);
    b.put(s15(0.9642)).put(s15(1.0)).put(s15(0.8249));
    b.position(128);
    b.putInt(tags);
    for (int i = 0; i < names.length; i++) {
      b.put(ascii(names[i])).putInt(offsets[i]).putInt(data[i].length);
    }
    for (String trcName : new String[] {"rTRC", "gTRC", "bTRC"}) {
      b.put(ascii(trcName)).putInt(trcOff).putInt(trc.length);
    }
    for (int i = 0; i < data.length; i++) {
      b.position(offsets[i]);
      b.put(data[i]);
    }
    b.position(trcOff);
    b.put(trc);
    return b.array();
  }

  private static int align(int v) {
    return (v + 3) & ~3;
  }

  private static byte[] ascii(String s) {
    return s.getBytes(StandardCharsets.US_ASCII);
  }

  private static byte[] s15(double v) {
    return ByteBuffer.allocate(4).putInt((int) Math.round(v * 65536)).array();
  }

  private static byte[] xyz(double x, double y, double z) {
    return ByteBuffer.allocate(20)
        .put(ascii("XYZ "))
        .putInt(0)
        .put(s15(x))
        .put(s15(y))
        .put(s15(z))
        .array();
  }

  private static byte[] text(String s) {
    byte[] t = ascii(s);
    return ByteBuffer.allocate(8 + t.length + 1).put(ascii("text")).putInt(0).put(t).array();
  }

  private static byte[] desc(String s) {
    byte[] t = ascii(s);
    return ByteBuffer.allocate(12 + t.length + 1 + 8 + 3 + 67)
        .put(ascii("desc"))
        .putInt(0)
        .putInt(t.length + 1)
        .put(t)
        .array();
  }

  private static byte[] curve() {
    ByteBuffer b = ByteBuffer.allocate(12 + 2 * CURVE_POINTS);
    b.put(ascii("curv")).putInt(0).putInt(CURVE_POINTS);
    for (int i = 0; i < CURVE_POINTS; i++) {
      double v = i / (CURVE_POINTS - 1.0);
      double linear = v <= 0.04045 ? v / 12.92 : StrictMath.pow((v + 0.055) / 1.055, 2.4);
      b.putShort((short) Math.round(linear * 65535));
    }
    return b.array();
  }
}
