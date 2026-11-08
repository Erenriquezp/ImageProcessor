package io.github.erenriquezp.rora.core.io;

/** Formatos que la aplicación reconoce. */
public enum FormatId {
  JPEG("jpg", true),
  PNG("png", true),
  TIFF("tif", true),
  BMP("bmp", false),
  GIF("gif", false),
  WEBP("webp", false);

  private final String extension;
  private final boolean writable;

  FormatId(String extension, boolean writable) {
    this.extension = extension;
    this.writable = writable;
  }

  /** Extensión sin punto, en minúsculas. */
  public String extension() {
    return extension;
  }

  /** ¿Se puede exportar a este formato? */
  public boolean writable() {
    return writable;
  }
}
