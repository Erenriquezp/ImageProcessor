package io.github.erenriquezp.rora.wallpapers.json;

/** Documento JSON no válido, con su posición (línea y columna desde 1). */
public final class JsonException extends Exception {

  private static final long serialVersionUID = 1L;

  private final int line;
  private final int column;

  public JsonException(String detail, int line, int column) {
    super(detail + " (línea " + line + ", columna " + column + ")");
    this.line = line;
    this.column = column;
  }

  public int line() {
    return line;
  }

  public int column() {
    return column;
  }
}
