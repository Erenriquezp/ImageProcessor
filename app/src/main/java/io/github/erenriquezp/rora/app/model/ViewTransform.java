package io.github.erenriquezp.rora.app.model;

/**
 * Transformación imagen ↔ pantalla del visor, inmutable y sin JavaFX. {@code scale} son píxeles
 * lógicos de pantalla por píxel de imagen; 100 % significa un píxel de imagen por píxel físico
 * ({@code scale = 1 / dpi}).
 *
 * @param fitted se mantiene ajustada a la ventana cuando cambian el visor o la imagen
 */
public record ViewTransform(
    int imageWidth,
    int imageHeight,
    double viewportWidth,
    double viewportHeight,
    double dpi,
    double scale,
    double tx,
    double ty,
    boolean fitted) {

  /** Punto en coordenadas de imagen o de pantalla. */
  public record Point(double x, double y) {}

  /** Margen entre la imagen ajustada y el borde del visor. */
  public static final double FIT_MARGIN = 16;

  private static final double MAX_ZOOM_PERCENT = 3200;

  /** Imagen ajustada al visor. */
  public static ViewTransform fitted(
      int imageWidth, int imageHeight, double viewportWidth, double viewportHeight, double dpi) {
    return new ViewTransform(
            imageWidth, imageHeight, viewportWidth, viewportHeight, dpi, 1, 0, 0, true)
        .fit();
  }

  /** Escala con la que la imagen cabe en el visor, sin ampliar más allá del 100 %. */
  public double fitScale() {
    double w = Math.max(1, viewportWidth - 2 * FIT_MARGIN);
    double h = Math.max(1, viewportHeight - 2 * FIT_MARGIN);
    return Math.min(Math.min(w / imageWidth, h / imageHeight), 1 / dpi);
  }

  public double minScale() {
    return Math.min(fitScale(), 1 / dpi);
  }

  public double maxScale() {
    return MAX_ZOOM_PERCENT / 100 / dpi;
  }

  public ViewTransform fit() {
    return withScale(fitScale(), 0, 0, true).centered();
  }

  /** 100 % real, centrado sobre el punto de la imagen que estaba en el centro del visor. */
  public ViewTransform actualSize() {
    return zoomTo(1 / dpi, viewportWidth / 2, viewportHeight / 2);
  }

  /** Multiplica el zoom dejando fijo el punto bajo {@code (cx, cy)}. */
  public ViewTransform zoomAt(double cx, double cy, double factor) {
    return zoomTo(scale * factor, cx, cy);
  }

  private ViewTransform zoomTo(double target, double cx, double cy) {
    double s = Math.max(minScale(), Math.min(maxScale(), target));
    double k = s / scale;
    return withScale(s, cx - (cx - tx) * k, cy - (cy - ty) * k, false).clamped();
  }

  public ViewTransform panBy(double dx, double dy) {
    return withScale(scale, tx + dx, ty + dy, fitted).clamped();
  }

  public ViewTransform withViewport(double width, double height, double newDpi) {
    ViewTransform v =
        new ViewTransform(imageWidth, imageHeight, width, height, newDpi, scale, tx, ty, fitted);
    return fitted ? v.fit() : v.clamped();
  }

  /** Con otro tamaño de imagen (la geometría cambió): vuelve a ajustar. */
  public ViewTransform withImage(int width, int height) {
    if (width == imageWidth && height == imageHeight) {
      return this;
    }
    return new ViewTransform(width, height, viewportWidth, viewportHeight, dpi, scale, tx, ty, true)
        .fit();
  }

  public Point imageToScreen(double x, double y) {
    return new Point(tx + x * scale, ty + y * scale);
  }

  public Point screenToImage(double sx, double sy) {
    return new Point((sx - tx) / scale, (sy - ty) / scale);
  }

  public boolean containsScreenPoint(double sx, double sy) {
    Point p = screenToImage(sx, sy);
    return p.x() >= 0 && p.y() >= 0 && p.x() < imageWidth && p.y() < imageHeight;
  }

  /** Zoom real: 100 % es un píxel de imagen por píxel físico. */
  public double zoomPercent() {
    return scale * dpi * 100;
  }

  /** Zoom respecto a la imagen ajustada, nunca menor que 1 (lo que espera la política de proxy). */
  public double zoomOverFit() {
    return Math.max(1.0, scale / fitScale());
  }

  /** Por encima del 100 % se muestran píxeles reales, sin suavizar. */
  public boolean showsRealPixels() {
    return zoomPercent() >= 100;
  }

  private ViewTransform withScale(double s, double x, double y, boolean fit) {
    return new ViewTransform(
        imageWidth, imageHeight, viewportWidth, viewportHeight, dpi, s, x, y, fit);
  }

  private ViewTransform centered() {
    return withScale(
        scale,
        (viewportWidth - imageWidth * scale) / 2,
        (viewportHeight - imageHeight * scale) / 2,
        fitted);
  }

  /** Si la imagen cabe en un eje se centra en él; si no, no deja huecos. */
  private ViewTransform clamped() {
    double w = imageWidth * scale;
    double h = imageHeight * scale;
    double x = w <= viewportWidth ? (viewportWidth - w) / 2 : clamp(tx, viewportWidth - w, 0);
    double y = h <= viewportHeight ? (viewportHeight - h) / 2 : clamp(ty, viewportHeight - h, 0);
    return withScale(scale, x, y, fitted);
  }

  private static double clamp(double v, double min, double max) {
    return Math.max(min, Math.min(max, v));
  }
}
