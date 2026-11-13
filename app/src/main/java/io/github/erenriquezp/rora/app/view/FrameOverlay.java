package io.github.erenriquezp.rora.app.view;

import io.github.erenriquezp.rora.app.model.CropModel;
import io.github.erenriquezp.rora.app.model.FrameOverlayState;
import io.github.erenriquezp.rora.app.model.ViewTransform;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

/**
 * Superposición de encuadre compartida por Recortar y Wallpapers: sombra fuera del rectángulo,
 * borde, tercios, asas y zonas seguras. Solo dibuja; el visor decide qué hacer con el ratón.
 */
final class FrameOverlay extends Pane {

  static final double HANDLE_RADIUS = 5;
  static final double GRAB = 12;

  private static final CropModel.Handle[] HANDLES = {
    CropModel.Handle.NW,
    CropModel.Handle.N,
    CropModel.Handle.NE,
    CropModel.Handle.E,
    CropModel.Handle.SE,
    CropModel.Handle.S,
    CropModel.Handle.SW,
    CropModel.Handle.W
  };

  FrameOverlay() {
    setMouseTransparent(true);
  }

  void draw(FrameOverlayState s, ViewTransform t) {
    getChildren().clear();
    if (s == null || t == null) {
      return;
    }
    CropModel c = s.frame();
    double ix = t.imageToScreen(0, 0).x();
    double iy = t.imageToScreen(0, 0).y();
    double iw = t.imageWidth() * t.scale();
    double ih = t.imageHeight() * t.scale();
    double l = ix + c.left() * iw;
    double r = ix + c.right() * iw;
    double top = iy + c.top() * ih;
    double b = iy + c.bottom() * ih;

    shade(ix, iy, iw, top - iy);
    shade(ix, b, iw, iy + ih - b);
    shade(ix, top, l - ix, b - top);
    shade(r, top, ix + iw - r, b - top);

    for (FrameOverlayState.Zone z : s.zones()) {
      Rectangle zr =
          new Rectangle(
              l + z.x() * (r - l), top + z.y() * (b - top), z.w() * (r - l), z.h() * (b - top));
      zr.getStyleClass().add("frame-zone");
      getChildren().add(zr);
      Label label = new Label(z.label());
      label.getStyleClass().add("frame-zone-label");
      label.relocate(zr.getX() + 4, zr.getY() + 2);
      getChildren().add(label);
    }
    if (s.thirds()) {
      for (int i = 1; i <= 2; i++) {
        double fx = l + (r - l) * i / 3;
        double fy = top + (b - top) * i / 3;
        third(fx, top, fx, b);
        third(l, fy, r, fy);
      }
    }
    Rectangle border = new Rectangle(l, top, r - l, b - top);
    border.getStyleClass().add("crop-border");
    getChildren().add(border);
    if (s.editable()) {
      for (double[] p : handles(l, top, r, b)) {
        Circle dot = new Circle(p[0], p[1], HANDLE_RADIUS);
        dot.getStyleClass().add("crop-handle");
        getChildren().add(dot);
      }
    }
  }

  /** Asa (o cuerpo) bajo el punto, o {@code null}; nada si no es editable. */
  static CropModel.Handle hit(FrameOverlayState s, ViewTransform t, double x, double y) {
    if (s == null || t == null || !s.editable()) {
      return null;
    }
    CropModel c = s.frame();
    double iw = t.imageWidth() * t.scale();
    double ih = t.imageHeight() * t.scale();
    double l = t.tx() + c.left() * iw;
    double r = t.tx() + c.right() * iw;
    double top = t.ty() + c.top() * ih;
    double b = t.ty() + c.bottom() * ih;
    double[][] points = handles(l, top, r, b);
    for (int i = 0; i < points.length; i++) {
      if (Math.hypot(x - points[i][0], y - points[i][1]) <= GRAB) {
        return HANDLES[i];
      }
    }
    return x > l && x < r && y > top && y < b ? CropModel.Handle.BODY : null;
  }

  private void shade(double x, double y, double w, double h) {
    Rectangle s = new Rectangle(x, y, Math.max(0, w), Math.max(0, h));
    s.getStyleClass().add("crop-shade");
    getChildren().add(s);
  }

  private void third(double x1, double y1, double x2, double y2) {
    Line line = new Line(x1, y1, x2, y2);
    line.getStyleClass().add("crop-third");
    getChildren().add(line);
  }

  private static double[][] handles(double l, double t, double r, double b) {
    double cx = (l + r) / 2;
    double cy = (t + b) / 2;
    return new double[][] {{l, t}, {cx, t}, {r, t}, {r, cy}, {r, b}, {cx, b}, {l, b}, {l, cy}};
  }
}
