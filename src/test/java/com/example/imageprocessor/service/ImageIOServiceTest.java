package com.example.imageprocessor.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.imageprocessor.testsupport.ImageAssert;
import com.example.imageprocessor.testsupport.TestImages;
import com.example.imageprocessor.testsupport.TestImages.Kind;
import com.example.imageprocessor.testsupport.TestImages.Pattern;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageIOServiceTest {

  @TempDir Path tmp;

  @Test
  @Tag("legacy-pin")
  void pngRoundTripKeepsPixelsAndPartialAlpha() throws IOException {
    BufferedImage img = TestImages.create(Pattern.NOISE, Kind.ARGB, 33, 17);
    File f = tmp.resolve("a.png").toFile();
    ImageIOService.write(img, f);
    ImageAssert.assertEqualExactly(ImageIOService.read(f), img);
  }

  @Test
  @Tag("legacy-pin")
  void jpgFromOpaqueRgbImageIsWritten() throws IOException {
    BufferedImage img = TestImages.create(Pattern.GRADIENT, Kind.RGB, 16, 16);
    File f = tmp.resolve("rgb.jpg").toFile();
    ImageIOService.write(img, f);
    BufferedImage back = ImageIOService.read(f);
    ImageAssert.assertSameSize(back, img);
  }

  @Test
  @Tag("legacy-pin")
  void unknownOrMissingExtensionFallsBackToPng() throws IOException {
    BufferedImage img = TestImages.create(Pattern.CHECKER, Kind.RGB, 4, 4);
    for (String name : new String[] {"noext", "dot.", "x.webp"}) {
      File f = tmp.resolve(name).toFile();
      ImageIOService.write(img, f);
      ImageAssert.assertEqualExactly(ImageIOService.read(f), img);
    }
  }

  @Test
  @Tag("legacy-pin")
  void readingANonImageThrowsIOException() throws IOException {
    File f = tmp.resolve("not-an-image.png").toFile();
    Files.writeString(f.toPath(), "esto no es una imagen");
    assertThatThrownBy(() -> ImageIOService.read(f)).isInstanceOf(IOException.class);
  }

  /**
   * Caracterización del defecto n.º 1 (CONFIRMADO): el writer JPEG del JDK no acepta TYPE_INT_ARGB,
   * {@code ImageIO.write} devuelve {@code false} y el servicio lanza IOException. Todo resultado de
   * filtro es ARGB, así que exportar a JPG falla casi siempre.
   */
  @Test
  @Tag("known-issue")
  void knownIssue_jpgExportOfArgbImageFails() {
    BufferedImage argb = TestImages.create(Pattern.GRADIENT, Kind.ARGB, 16, 16);
    File f = tmp.resolve("argb.jpg").toFile();
    assertThatThrownBy(() -> ImageIOService.write(argb, f))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("No hay writer para el formato: jpg");
  }

  /** Comportamiento CORRECTO esperado: la exportación produce un JPG válido. */
  @Test
  @Tag("known-issue")
  @Disabled(
      "known-issue: exportar ARGB a .jpg falla (ImageIO.write devuelve false). Se corrige en fase 4 (PROPUESTA_PRODUCTO.md §5.6/§6.3: aplanado de alpha en el encoder JPG)")
  void jpgExportOfArgbImageProducesValidJpg() throws IOException {
    BufferedImage argb = TestImages.create(Pattern.GRADIENT, Kind.ARGB, 16, 16);
    File f = tmp.resolve("argb.jpg").toFile();
    ImageIOService.write(argb, f);
    BufferedImage back = ImageIOService.read(f);
    ImageAssert.assertSameSize(back, argb);
  }
}
