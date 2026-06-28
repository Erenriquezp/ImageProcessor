package com.example.imageprocessor.app.ui;

import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import java.util.function.BooleanSupplier;

/**
 * Permite redimensionar una ventana sin bordes ({@code StageStyle.UNDECORATED})
 * arrastrando desde sus bordes y esquinas. Se instala como filtro de eventos de
 * ratón sobre la {@link Scene}; el cursor cambia al acercarse a un borde.
 */
public final class WindowResizer {

    /** Grosor (px) de la zona sensible alrededor del borde. */
    private static final double EDGE = 6;

    private WindowResizer() {}

    public static void install(Stage stage, Scene scene,
                               double minW, double minH,
                               BooleanSupplier maximized) {
        final double[] startW = new double[1];
        final double[] startH = new double[1];
        final double[] startX = new double[1];
        final double[] startY = new double[1];
        final double[] pressScreenX = new double[1];
        final double[] pressScreenY = new double[1];
        final Cursor[] active = { Cursor.DEFAULT };

        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (maximized.getAsBoolean()) { scene.setCursor(Cursor.DEFAULT); return; }
            scene.setCursor(cursorFor(e.getSceneX(), e.getSceneY(),
                    scene.getWidth(), scene.getHeight()));
        });

        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            active[0] = maximized.getAsBoolean() ? Cursor.DEFAULT
                    : cursorFor(e.getSceneX(), e.getSceneY(), scene.getWidth(), scene.getHeight());
            startW[0] = stage.getWidth();
            startH[0] = stage.getHeight();
            startX[0] = stage.getX();
            startY[0] = stage.getY();
            pressScreenX[0] = e.getScreenX();
            pressScreenY[0] = e.getScreenY();
        });

        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            Cursor c = active[0];
            if (c == Cursor.DEFAULT) return;

            double dx = e.getScreenX() - pressScreenX[0];
            double dy = e.getScreenY() - pressScreenY[0];

            boolean west  = c == Cursor.W_RESIZE || c == Cursor.NW_RESIZE || c == Cursor.SW_RESIZE;
            boolean east  = c == Cursor.E_RESIZE || c == Cursor.NE_RESIZE || c == Cursor.SE_RESIZE;
            boolean north = c == Cursor.N_RESIZE || c == Cursor.NW_RESIZE || c == Cursor.NE_RESIZE;
            boolean south = c == Cursor.S_RESIZE || c == Cursor.SW_RESIZE || c == Cursor.SE_RESIZE;

            if (east)  stage.setWidth(Math.max(minW, startW[0] + dx));
            if (south) stage.setHeight(Math.max(minH, startH[0] + dy));
            if (west) {
                double w = Math.max(minW, startW[0] - dx);
                stage.setX(startX[0] + (startW[0] - w));
                stage.setWidth(w);
            }
            if (north) {
                double h = Math.max(minH, startH[0] - dy);
                stage.setY(startY[0] + (startH[0] - h));
                stage.setHeight(h);
            }
            e.consume();
        });
    }

    private static Cursor cursorFor(double x, double y, double w, double h) {
        boolean left   = x < EDGE;
        boolean right  = x > w - EDGE;
        boolean top    = y < EDGE;
        boolean bottom = y > h - EDGE;
        if (top && left)     return Cursor.NW_RESIZE;
        if (top && right)    return Cursor.NE_RESIZE;
        if (bottom && left)  return Cursor.SW_RESIZE;
        if (bottom && right) return Cursor.SE_RESIZE;
        if (left)   return Cursor.W_RESIZE;
        if (right)  return Cursor.E_RESIZE;
        if (top)    return Cursor.N_RESIZE;
        if (bottom) return Cursor.S_RESIZE;
        return Cursor.DEFAULT;
    }
}
