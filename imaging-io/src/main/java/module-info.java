/** Códecs de Rora Studio sobre {@code ImageIO}. El núcleo solo ve la SPI de {@code core.io}. */
module io.github.erenriquezp.rora.imaging.io {
  requires io.github.erenriquezp.rora.core;
  requires java.desktop;
  requires java.xml;

  exports io.github.erenriquezp.rora.imaging.io;

  uses io.github.erenriquezp.rora.core.io.ImageDecoder;
  uses io.github.erenriquezp.rora.core.io.ImageEncoder;

  provides io.github.erenriquezp.rora.core.io.ImageDecoder with
      io.github.erenriquezp.rora.imaging.io.ImageIoDecoder;
  provides io.github.erenriquezp.rora.core.io.ImageEncoder with
      io.github.erenriquezp.rora.imaging.io.JpegEncoder,
      io.github.erenriquezp.rora.imaging.io.PngEncoder,
      io.github.erenriquezp.rora.imaging.io.TiffEncoder;
}
