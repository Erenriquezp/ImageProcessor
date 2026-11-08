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
}
