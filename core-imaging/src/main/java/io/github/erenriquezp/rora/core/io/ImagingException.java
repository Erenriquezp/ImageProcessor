package io.github.erenriquezp.rora.core.io;

import java.util.List;

/**
 * Error de entrada o salida de imagen. {@link #messageKey()} es la clave del mensaje para el
 * usuario; {@link #technical()} es el detalle técnico, que nunca se muestra como mensaje principal.
 */
public abstract sealed class ImagingException extends Exception {

  private static final long serialVersionUID = 1L;

  private final String messageKey;
  private final transient List<String> args;

  private ImagingException(String key, String technical, Throwable cause, String... args) {
    super(technical, cause);
    this.messageKey = key;
    this.args = List.of(args);
  }

  public String messageKey() {
    return messageKey;
  }

  public List<String> args() {
    return args;
  }

  public String technical() {
    return getMessage();
  }

  /** El archivo no es de un formato admitido. */
  public static final class UnsupportedFormat extends ImagingException {
    private static final long serialVersionUID = 1L;

    public UnsupportedFormat(String key, String technical) {
      super(key, technical, null);
    }
  }

  /** El archivo está truncado o dañado. */
  public static final class CorruptFile extends ImagingException {
    private static final long serialVersionUID = 1L;

    public CorruptFile(String technical, Throwable cause) {
      super("io.error.corrupt", technical, cause);
    }
  }

  /** La imagen no cabe en la memoria disponible. */
  public static final class TooLarge extends ImagingException {
    private static final long serialVersionUID = 1L;

    public TooLarge(int width, int height, String technical) {
      super(
          "io.error.toolarge", technical, null, Integer.toString(width), Integer.toString(height));
    }
  }

  /** El usuario canceló. */
  public static final class Cancelled extends ImagingException {
    private static final long serialVersionUID = 1L;

    public Cancelled() {
      super("io.error.cancelled", "cancelled", null);
    }
  }

  /** Fallo del sistema de archivos. */
  public static final class IoFailure extends ImagingException {
    private static final long serialVersionUID = 1L;

    public IoFailure(String key, String technical, Throwable cause, String... args) {
      super(key, technical, cause, args);
    }
  }

  /** No se pudo codificar la salida. */
  public static final class EncodingFailure extends ImagingException {
    private static final long serialVersionUID = 1L;

    public EncodingFailure(String key, String technical, Throwable cause, String... args) {
      super(key, technical, cause, args);
    }
  }
}
