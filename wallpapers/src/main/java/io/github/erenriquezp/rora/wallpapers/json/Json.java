package io.github.erenriquezp.rora.wallpapers.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lector y escritor JSON mínimos para los presets. Subconjunto estricto de RFC 8259: objetos,
 * listas, cadenas, números, booleanos y {@code null}. Sin claves duplicadas y con profundidad
 * acotada.
 *
 * <p>Tipos: objeto → {@code Map<String, Object>} (orden de aparición), lista → {@code
 * List<Object>}, número → {@link BigDecimal}, cadena → {@link String}, booleano → {@link Boolean},
 * {@code null} → {@link #NULL}.
 */
public final class Json {

  /** Valor {@code null} de JSON (los mapas no admiten {@code null} sin ambigüedad). */
  public static final Object NULL =
      new Object() {
        @Override
        public String toString() {
          return "null";
        }
      };

  /** Profundidad máxima de anidamiento. */
  public static final int MAX_DEPTH = 16;

  private final String text;
  private int pos;
  private int depth;

  private Json(String text) {
    this.text = text;
  }

  /**
   * Analiza un documento completo.
   *
   * @throws JsonException con línea y columna si no es válido
   */
  public static Object parse(String text) throws JsonException {
    Objects.requireNonNull(text, "text");
    Json p = new Json(text);
    if (p.pos < text.length() && text.charAt(0) == '﻿') {
      p.pos++;
    }
    p.skipSpace();
    Object value = p.value();
    p.skipSpace();
    if (p.pos != text.length()) {
      throw p.error("contenido tras el final del documento");
    }
    return value;
  }

  private Object value() throws JsonException {
    if (pos >= text.length()) {
      throw error("fin inesperado");
    }
    char c = text.charAt(pos);
    return switch (c) {
      case '{' -> object();
      case '[' -> array();
      case '"' -> string();
      case 't' -> literal("true", Boolean.TRUE);
      case 'f' -> literal("false", Boolean.FALSE);
      case 'n' -> literal("null", NULL);
      default -> {
        if (c == '-' || (c >= '0' && c <= '9')) {
          yield number();
        }
        throw error("carácter inesperado '" + c + "'");
      }
    };
  }

  private Map<String, Object> object() throws JsonException {
    enter();
    pos++;
    Map<String, Object> out = new LinkedHashMap<>();
    skipSpace();
    if (peek() == '}') {
      pos++;
      depth--;
      return Collections.unmodifiableMap(out);
    }
    while (true) {
      skipSpace();
      if (peek() != '"') {
        throw error("se esperaba una clave");
      }
      int keyPos = pos;
      String key = string();
      skipSpace();
      expect(':');
      skipSpace();
      Object v = value();
      if (out.putIfAbsent(key, v) != null) {
        pos = keyPos;
        throw error("clave duplicada \"" + key + "\"");
      }
      skipSpace();
      char c = peek();
      pos++;
      if (c == '}') {
        depth--;
        return Collections.unmodifiableMap(out);
      }
      if (c != ',') {
        pos--;
        throw error("se esperaba ',' o '}'");
      }
    }
  }

  private List<Object> array() throws JsonException {
    enter();
    pos++;
    List<Object> out = new ArrayList<>();
    skipSpace();
    if (peek() == ']') {
      pos++;
      depth--;
      return Collections.unmodifiableList(out);
    }
    while (true) {
      skipSpace();
      out.add(value());
      skipSpace();
      char c = peek();
      pos++;
      if (c == ']') {
        depth--;
        return Collections.unmodifiableList(out);
      }
      if (c != ',') {
        pos--;
        throw error("se esperaba ',' o ']'");
      }
    }
  }

  private String string() throws JsonException {
    pos++;
    StringBuilder sb = new StringBuilder();
    while (true) {
      if (pos >= text.length()) {
        throw error("cadena sin cerrar");
      }
      char c = text.charAt(pos++);
      if (c == '"') {
        return sb.toString();
      }
      if (c < 0x20) {
        pos--;
        throw error("carácter de control en una cadena");
      }
      if (c != '\\') {
        sb.append(c);
        continue;
      }
      if (pos >= text.length()) {
        throw error("escape incompleto");
      }
      char e = text.charAt(pos++);
      switch (e) {
        case '"' -> sb.append('"');
        case '\\' -> sb.append('\\');
        case '/' -> sb.append('/');
        case 'b' -> sb.append('\b');
        case 'f' -> sb.append('\f');
        case 'n' -> sb.append('\n');
        case 'r' -> sb.append('\r');
        case 't' -> sb.append('\t');
        case 'u' -> sb.append(unicode());
        default -> {
          pos--;
          throw error("escape no válido '\\" + e + "'");
        }
      }
    }
  }

  private char unicode() throws JsonException {
    if (pos + 4 > text.length()) {
      throw error("escape \\u incompleto");
    }
    int v = 0;
    for (int i = 0; i < 4; i++) {
      int d = Character.digit(text.charAt(pos + i), 16);
      if (d < 0) {
        pos += i;
        throw error("dígito hexadecimal no válido");
      }
      v = v * 16 + d;
    }
    pos += 4;
    return (char) v;
  }

  private BigDecimal number() throws JsonException {
    int start = pos;
    if (peek() == '-') {
      pos++;
    }
    if (peek() == '0') {
      pos++;
    } else if (isDigit(peek())) {
      digits();
    } else {
      throw error("número no válido");
    }
    if (peek() == '.') {
      pos++;
      if (!isDigit(peek())) {
        throw error("número no válido");
      }
      digits();
    }
    if (peek() == 'e' || peek() == 'E') {
      pos++;
      if (peek() == '+' || peek() == '-') {
        pos++;
      }
      if (!isDigit(peek())) {
        throw error("número no válido");
      }
      digits();
    }
    String s = text.substring(start, pos);
    if (s.length() > 64) {
      pos = start;
      throw error("número demasiado largo");
    }
    return new BigDecimal(s);
  }

  private void digits() {
    while (isDigit(peek())) {
      pos++;
    }
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private Object literal(String word, Object v) throws JsonException {
    if (!text.startsWith(word, pos)) {
      throw error("literal no válido");
    }
    pos += word.length();
    return v;
  }

  private void enter() throws JsonException {
    if (++depth > MAX_DEPTH) {
      throw error("anidamiento mayor que " + MAX_DEPTH);
    }
  }

  private void expect(char c) throws JsonException {
    if (peek() != c) {
      throw error("se esperaba '" + c + "'");
    }
    pos++;
  }

  private char peek() {
    return pos < text.length() ? text.charAt(pos) : '\0';
  }

  private void skipSpace() {
    while (pos < text.length()) {
      char c = text.charAt(pos);
      if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
        return;
      }
      pos++;
    }
  }

  private JsonException error(String detail) {
    int line = 1;
    int col = 1;
    for (int i = 0; i < Math.min(pos, text.length()); i++) {
      if (text.charAt(i) == '\n') {
        line++;
        col = 1;
      } else {
        col++;
      }
    }
    return new JsonException(detail, line, col);
  }

  /** Escribe un valor con sangría de dos espacios, en el orden de los mapas. Termina en salto. */
  public static String write(Object value) {
    StringBuilder sb = new StringBuilder();
    write(sb, value, 0);
    return sb.append('\n').toString();
  }

  private static void write(StringBuilder sb, Object v, int indent) {
    if (v == NULL || v == null) {
      sb.append("null");
    } else if (v instanceof String s) {
      quote(sb, s);
    } else if (v instanceof Boolean b) {
      sb.append(b);
    } else if (v instanceof BigDecimal d) {
      sb.append(d.toString());
    } else if (v instanceof Integer || v instanceof Long) {
      sb.append(v);
    } else if (v instanceof Double d) {
      if (!Double.isFinite(d)) {
        throw new IllegalArgumentException("número no finito: " + d);
      }
      sb.append(BigDecimal.valueOf(d).stripTrailingZeros().toPlainString());
    } else if (v instanceof Map<?, ?> m) {
      if (m.isEmpty()) {
        sb.append("{}");
        return;
      }
      sb.append("{\n");
      int i = 0;
      for (Map.Entry<?, ?> e : m.entrySet()) {
        pad(sb, indent + 1);
        quote(sb, (String) e.getKey());
        sb.append(": ");
        write(sb, e.getValue(), indent + 1);
        sb.append(++i < m.size() ? ",\n" : "\n");
      }
      pad(sb, indent);
      sb.append('}');
    } else if (v instanceof List<?> l) {
      if (l.isEmpty()) {
        sb.append("[]");
        return;
      }
      sb.append("[\n");
      for (int i = 0; i < l.size(); i++) {
        pad(sb, indent + 1);
        write(sb, l.get(i), indent + 1);
        sb.append(i + 1 < l.size() ? ",\n" : "\n");
      }
      pad(sb, indent);
      sb.append(']');
    } else {
      throw new IllegalArgumentException("tipo no admitido: " + v.getClass().getName());
    }
  }

  private static void pad(StringBuilder sb, int indent) {
    sb.append("  ".repeat(indent));
  }

  private static void quote(StringBuilder sb, String s) {
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    sb.append('"');
  }
}
