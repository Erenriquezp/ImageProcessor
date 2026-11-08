package io.github.erenriquezp.rora.core.io;

/** Búsqueda determinista de la mayor calidad JPEG que cumple un peso máximo. */
public final class QualitySearch {

  /** Calidad más baja que se intenta. */
  public static final int MIN_QUALITY = 20;

  /** Iteraciones de bisección tras comprobar los extremos. */
  public static final int MAX_ITERATIONS = 7;

  /** Codifica a una calidad y devuelve el tamaño en bytes. */
  @FunctionalInterface
  public interface Trial {
    long sizeAt(int quality) throws ImagingException;
  }

  /** Calidad elegida y su tamaño. */
  public record Result(int quality, long bytes) {}

  private QualitySearch() {}

  /**
   * @param start calidad pedida por el usuario: la máxima que se considera
   * @throws ImagingException.EncodingFailure si ni con {@link #MIN_QUALITY} se alcanza el peso
   */
  public static Result search(int start, long maxBytes, Trial trial) throws ImagingException {
    long atStart = trial.sizeAt(start);
    if (atStart <= maxBytes) {
      return new Result(start, atStart);
    }
    int low = Math.min(start, MIN_QUALITY);
    long atLow = trial.sizeAt(low);
    if (atLow > maxBytes) {
      throw new ImagingException.EncodingFailure(
          "io.error.targetsize",
          "calidad " + low + " = " + atLow + " bytes > " + maxBytes,
          null,
          Long.toString(atLow));
    }
    Result best = new Result(low, atLow);
    int high = start;
    for (int i = 0; i < MAX_ITERATIONS && high - best.quality() > 1; i++) {
      int mid = (best.quality() + high) >>> 1;
      long size = trial.sizeAt(mid);
      if (size <= maxBytes) {
        best = new Result(mid, size);
      } else {
        high = mid;
      }
    }
    return best;
  }
}
