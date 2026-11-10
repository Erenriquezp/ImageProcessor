package io.github.erenriquezp.rora.app.session.ai;

/** Qué se puede mejorar y cuánto tardará. Sin estado: se prueba sin motor. */
public final class AiLimits {

  /** Resultado máximo, en megapíxeles. */
  public static final long MAX_OUTPUT_PIXELS = 64_000_000L;

  /** Memoria física que necesita el motor además del montón (medido: 218 MB por sesión). */
  public static final long ENGINE_PHYSICAL_BYTES = 600L << 20;

  /** Segundos por tile hasta que este equipo tenga una medida propia. */
  public static final double DEFAULT_SECONDS_PER_TILE = 1.5;

  /** Tiempo a partir del cual se muestra ETA. */
  public static final double SHORT_SECONDS = 20;

  /** Tiempo a partir del cual se advierte. */
  public static final double WARN_SECONDS = 60;

  /** Tiempo a partir del cual se pide confirmación. */
  public static final double CONFIRM_SECONDS = 120;

  /** Memoria disponible en este momento. */
  public interface Memory {
    long freeHeapBytes();

    long freePhysicalBytes();
  }

  /** Veredicto para una imagen y escala. */
  public enum Verdict {
    OK,
    OUTPUT_TOO_LARGE,
    NOT_ENOUGH_HEAP,
    NOT_ENOUGH_PHYSICAL
  }

  /** Tramo de una estimación. */
  public enum Band {
    SHORT,
    NORMAL,
    SLOW,
    CONFIRM
  }

  private AiLimits() {}

  public static long outputPixels(int width, int height, int scale) {
    return (long) width * height * scale * scale;
  }

  /** Memoria de montón que necesita el resultado. */
  public static long heapBytes(
      int width, int height, int scale, boolean fullIntensity, boolean alpha) {
    long one = outputPixels(width, height, scale) * 4;
    // A menos del 100 % conviven el resultado de la IA, el clásico y la mezcla.
    return one * (fullIntensity ? 1 : 3) + (alpha ? one : 0);
  }

  public static Verdict check(
      int width, int height, int scale, boolean fullIntensity, boolean alpha, Memory memory) {
    if (outputPixels(width, height, scale) > MAX_OUTPUT_PIXELS) {
      return Verdict.OUTPUT_TOO_LARGE;
    }
    if (heapBytes(width, height, scale, fullIntensity, alpha) > memory.freeHeapBytes()) {
      return Verdict.NOT_ENOUGH_HEAP;
    }
    if (memory.freePhysicalBytes() < ENGINE_PHYSICAL_BYTES) {
      return Verdict.NOT_ENOUGH_PHYSICAL;
    }
    return Verdict.OK;
  }

  public static Band band(double seconds) {
    if (seconds > CONFIRM_SECONDS) {
      return Band.CONFIRM;
    }
    if (seconds > WARN_SECONDS) {
      return Band.SLOW;
    }
    return seconds >= SHORT_SECONDS ? Band.NORMAL : Band.SHORT;
  }
}
