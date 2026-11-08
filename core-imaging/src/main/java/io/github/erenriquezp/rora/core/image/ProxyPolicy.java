package io.github.erenriquezp.rora.core.image;

import java.util.Optional;

/**
 * Decide el tamaño del proxy de preview a partir del visor.
 *
 * <ol>
 *   <li>Lado pedido {@code L = ceil(max(vw, vh) · dpi · zoom)}, redondeado hacia arriba a múltiplos
 *       de {@link #roundTo()} (resizes pequeños no cambian el proxy).
 *   <li>Escala {@code s = min(1, L / max(W, H), sqrt(maxPixels / (W·H)))}.
 *   <li>Sin proxy si {@code s ≥ smallImageFactor}: la imagen ya es pequeña y el preview es exacto.
 *   <li>Histéresis: se conserva el proxy actual mientras el lado pedido no lo supere ni baje de
 *       {@code lado actual / shrinkHysteresis}.
 *   <li>{@link Decision#fullNeeded()}: el lado pedido supera el del proxy (tope de píxeles), así
 *       que hace falta el render completo para verlo nítido (zoom).
 * </ol>
 *
 * @param maxPixels tope de píxeles del proxy (por defecto 4 MP)
 * @param smallImageFactor por encima de esta escala no se usa proxy (por defecto 0,75)
 * @param roundTo granularidad del lado pedido en píxeles (por defecto 256)
 * @param shrinkHysteresis cuánto puede bajar el lado pedido sin recalcular (por defecto 1,5)
 */
public record ProxyPolicy(
    long maxPixels, double smallImageFactor, int roundTo, double shrinkHysteresis) {

  public static final ProxyPolicy DEFAULT = new ProxyPolicy(4_000_000L, 0.75, 256, 1.5);

  public ProxyPolicy {
    if (maxPixels < 1 || !(smallImageFactor > 0 && smallImageFactor <= 1) || roundTo < 1) {
      throw new IllegalArgumentException("ProxyPolicy no válida");
    }
    if (!(shrinkHysteresis >= 1)) {
      throw new IllegalArgumentException("shrinkHysteresis=" + shrinkHysteresis);
    }
  }

  /** Tamaño en píxeles. */
  public record Size(int width, int height) {
    public Size {
      if (width < 1 || height < 1) {
        throw new IllegalArgumentException(width + "x" + height);
      }
    }

    public int longSide() {
      return Math.max(width, height);
    }

    public long pixels() {
      return (long) width * height;
    }
  }

  /**
   * @param proxy tamaño del proxy, o vacío si se muestra la imagen completa
   * @param fullNeeded el visor pide más resolución de la que da el proxy
   */
  public record Decision(Optional<Size> proxy, boolean fullNeeded) {}

  /**
   * Decide el proxy para una imagen {@code full} vista en un visor {@code vw × vh} (píxeles
   * lógicos) con escala {@code dpi} y {@code zoom ≥ 1}, partiendo del proxy actual (o vacío).
   */
  public Decision decide(
      Size full, int vw, int vh, double dpi, double zoom, Optional<Size> current) {
    long requested = requestedSide(vw, vh, dpi, zoom);
    int fullSide = full.longSide();
    double capScale = Math.sqrt((double) maxPixels / full.pixels());
    double s = Math.min(1.0, Math.min((double) requested / fullSide, capScale));
    if (s >= smallImageFactor) {
      return new Decision(Optional.empty(), false);
    }
    if (current.isPresent()) {
      int side = current.get().longSide();
      if (requested <= side && requested * shrinkHysteresis >= side) {
        return new Decision(current, false);
      }
    }
    Size proxy = scaled(full, s);
    return new Decision(Optional.of(proxy), requested > proxy.longSide());
  }

  long requestedSide(int vw, int vh, double dpi, double zoom) {
    if (vw < 1 || vh < 1 || !(dpi > 0) || !(zoom >= 1)) {
      throw new IllegalArgumentException(
          "visor " + vw + "x" + vh + ", dpi=" + dpi + ", zoom=" + zoom);
    }
    long raw = (long) Math.ceil(Math.max(vw, vh) * dpi * zoom);
    return ((raw + roundTo - 1) / roundTo) * roundTo;
  }

  static Size scaled(Size full, double s) {
    int w = (int) Math.max(1, Math.min(full.width(), Math.round(full.width() * s)));
    int h = (int) Math.max(1, Math.min(full.height(), Math.round(full.height() * s)));
    return new Size(w, h);
  }
}
