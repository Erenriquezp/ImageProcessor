package com.example.imageprocessor.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Comparación con PNG de referencia en {@code src/test/resources/golden/<operacion>/<caso>.png}.
 *
 * <p>Los goldens SOLO se crean o sobrescriben con {@code -Dgolden.update=true}. Sin ese flag, un
 * golden ausente hace fallar el test (nunca se crea en silencio).
 */
public final class GoldenSupport {

  public static final String UPDATE_FLAG = "golden.update";

  private GoldenSupport() {}

  private static Path root() {
    return Path.of(System.getProperty("basedir", "."), "src", "test", "resources", "golden");
  }

  public static boolean updating() {
    return Boolean.getBoolean(UPDATE_FLAG);
  }

  public static void assertMatches(
      String operation, String caseName, BufferedImage actual, int tolerance) {
    Path file = root().resolve(operation).resolve(caseName + ".png");
    try {
      if (updating()) {
        Files.createDirectories(file.getParent());
        int w = actual.getWidth();
        int h = actual.getHeight();
        // Siempre ARGB: la lectura posterior no depende del tipo de la imagen original
        BufferedImage argb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        argb.setRGB(0, 0, w, h, actual.getRGB(0, 0, w, h, null, 0, w), 0, w);
        ImageIO.write(argb, "png", file.toFile());
        return;
      }
      assertThat(file)
          .as(
              "Falta el golden %s. Genérelo de forma deliberada con: ./mvnw test -D%s=true",
              file, UPDATE_FLAG)
          .exists();
      BufferedImage expected = ImageIO.read(file.toFile());
      ImageAssert.assertEqualWithin(actual, expected, tolerance);
    } catch (IOException e) {
      throw new AssertionError("Error de E/S con el golden " + file, e);
    }
  }
}
