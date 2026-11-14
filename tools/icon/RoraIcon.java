import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Icono original de Rora Studio, dibujado con Java2D y sin recursos de terceros: un cuadrado
 * redondeado con el degradado del azul de acento, una "R" de trazo (asta, panza y pierna) y, desde
 * 32 px, una esquina de recorte arriba a la derecha. Determinista: mismas entradas, mismos bytes.
 *
 * <pre>
 * java tools/icon/RoraIcon.java OUT_DIR            escribe RoraStudio.ico y app-icon.png
 * java tools/icon/RoraIcon.java --verify FILE.ico  comprueba la estructura del ICO
 * </pre>
 */
public final class RoraIcon {

  static final int[] SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};
  static final Color LIGHT = new Color(0x6AAEF2);
  static final Color DARK = new Color(0x2B6CB0);

  private RoraIcon() {}

  public static void main(String[] args) throws IOException {
    if (args.length == 2 && args[0].equals("--verify")) {
      verify(Files.readAllBytes(Path.of(args[1])));
      System.out.println("ICO correcto: " + SIZES.length + " tamaños");
      return;
    }
    if (args.length != 1) {
      System.err.println("Uso: java RoraIcon.java OUT_DIR | --verify FILE.ico");
      System.exit(2);
    }
    Path out = Files.createDirectories(Path.of(args[0]));
    List<byte[]> pngs = new ArrayList<>();
    for (int s : SIZES) {
      pngs.add(png(draw(s)));
    }
    byte[] ico = ico(pngs);
    verify(ico);
    Files.write(out.resolve("RoraStudio.ico"), ico);
    Files.write(out.resolve("app-icon.png"), pngs.get(pngs.size() - 1));
    System.out.println("Escritos RoraStudio.ico (" + ico.length + " bytes) y app-icon.png");
  }

  static BufferedImage draw(int s) {
    BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    double pad = Math.max(0.5, s * 0.04);
    double side = s - 2 * pad;
    double radius = s * 0.22;
    g.setPaint(
        new GradientPaint(
            (float) pad, (float) pad, LIGHT, (float) (s - pad), (float) (s - pad), DARK));
    g.fill(new RoundRectangle2D.Double(pad, pad, side, side, 2 * radius, 2 * radius));

    // "R": asta, panza (medio círculo) y pierna en diagonal.
    float stroke = (float) Math.max(1.6, s * (s <= 24 ? 0.13 : 0.11));
    g.setColor(Color.WHITE);
    g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    double x0 = s * 0.33;
    double top = s * 0.25;
    double bottom = s * 0.76;
    double bowl = s * 0.14;
    double mid = top + 2 * bowl;
    double arm = s * 0.13;
    Path2D r = new Path2D.Double();
    r.moveTo(x0, bottom);
    r.lineTo(x0, top);
    r.lineTo(x0 + arm, top);
    r.append(new Arc2D.Double(x0 + arm - bowl, top, 2 * bowl, 2 * bowl, 90, -180, Arc2D.OPEN), true);
    r.lineTo(x0, mid);
    g.draw(r);
    g.draw(new Line2D.Double(x0 + arm, mid, x0 + s * 0.34, bottom));

    if (s >= 32) {
      // Esquina de recorte: encuadrar es la mitad de la app.
      double m = s * 0.17;
      double len = s * 0.13;
      g.setStroke(
          new BasicStroke(
              (float) Math.max(1.0, s * 0.045), BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
      Path2D c = new Path2D.Double();
      c.moveTo(s - m - len, m);
      c.lineTo(s - m, m);
      c.lineTo(s - m, m + len);
      g.setColor(new Color(255, 255, 255, 210));
      g.draw(c);
    }
    g.dispose();
    return img;
  }

  static byte[] png(BufferedImage img) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    if (!ImageIO.write(img, "png", out)) {
      throw new IOException("sin escritor PNG");
    }
    return out.toByteArray();
  }

  /** ICO con las imágenes en PNG (admitido desde Windows Vista), en el orden de {@link #SIZES}. */
  static byte[] ico(List<byte[]> pngs) {
    int header = 6 + 16 * pngs.size();
    int total = header + pngs.stream().mapToInt(p -> p.length).sum();
    ByteBuffer b = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
    b.putShort((short) 0).putShort((short) 1).putShort((short) pngs.size());
    int offset = header;
    for (int i = 0; i < pngs.size(); i++) {
      int s = SIZES[i];
      b.put((byte) (s >= 256 ? 0 : s)).put((byte) (s >= 256 ? 0 : s));
      b.put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32);
      b.putInt(pngs.get(i).length).putInt(offset);
      offset += pngs.get(i).length;
    }
    pngs.forEach(b::put);
    return b.array();
  }

  static void verify(byte[] ico) throws IOException {
    ByteBuffer b = ByteBuffer.wrap(ico).order(ByteOrder.LITTLE_ENDIAN);
    if (b.getShort() != 0 || b.getShort() != 1) {
      throw new IOException("cabecera ICO no válida");
    }
    int n = b.getShort();
    if (n != SIZES.length) {
      throw new IOException("se esperaban " + SIZES.length + " imágenes y hay " + n);
    }
    for (int i = 0; i < n; i++) {
      int w = Byte.toUnsignedInt(b.get());
      int h = Byte.toUnsignedInt(b.get());
      b.get();
      b.get();
      b.getShort();
      int bits = b.getShort();
      int len = b.getInt();
      int off = b.getInt();
      int expected = SIZES[i] >= 256 ? 0 : SIZES[i];
      if (w != expected || h != expected || bits != 32 || off + len > ico.length) {
        throw new IOException("entrada " + i + " no válida");
      }
      BufferedImage img = ImageIO.read(new ByteArrayInputStream(ico, off, len));
      if (img == null || img.getWidth() != SIZES[i] || img.getHeight() != SIZES[i]) {
        throw new IOException("PNG " + i + " no válido");
      }
    }
  }
}
