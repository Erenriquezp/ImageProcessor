package io.github.erenriquezp.rora.core.io;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Nombre legible de un perfil ICC, saneado para mostrarlo. */
public final class IccInfo {

  private static final int MAX_NAME = 64;

  private IccInfo() {}

  /** Descripción del perfil (ASCII imprimible, 64 caracteres como mucho), si tiene. */
  public static Optional<String> description(byte[] icc) {
    try {
      ByteBuffer b = ByteBuffer.wrap(icc);
      int n = b.getInt(128);
      if (n < 0 || n > 256) {
        return Optional.empty();
      }
      for (int i = 0; i < n; i++) {
        int at = 132 + 12 * i;
        if (b.getInt(at) == 0x64657363) { // 'desc'
          return parse(b, b.getInt(at + 4));
        }
      }
    } catch (RuntimeException e) {
      return Optional.empty();
    }
    return Optional.empty();
  }

  private static Optional<String> parse(ByteBuffer b, int off) {
    int type = b.getInt(off);
    String s;
    if (type == 0x64657363) { // 'desc' (v2): longitud y ASCII
      int len = b.getInt(off + 8);
      s = new String(b.array(), off + 12, Math.max(0, len), StandardCharsets.ISO_8859_1);
    } else if (type == 0x6D6C7563) { // 'mluc' (v4): primer registro UTF-16BE
      int len = b.getInt(off + 20);
      int start = off + b.getInt(off + 24);
      s = new String(b.array(), start, len, StandardCharsets.UTF_16BE);
    } else {
      return Optional.empty();
    }
    StringBuilder out = new StringBuilder();
    s.chars().filter(c -> c >= 0x20 && c < 0x7F).limit(MAX_NAME).forEach(out::appendCodePoint);
    String name = out.toString().trim();
    return name.isEmpty() ? Optional.empty() : Optional.of(name);
  }
}
