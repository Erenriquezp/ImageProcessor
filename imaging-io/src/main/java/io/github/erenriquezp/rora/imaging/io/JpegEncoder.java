package io.github.erenriquezp.rora.imaging.io;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.AlphaFlatten;
import io.github.erenriquezp.rora.core.io.ExifCodec;
import io.github.erenriquezp.rora.core.io.ExportSettings;
import io.github.erenriquezp.rora.core.io.FormatId;
import io.github.erenriquezp.rora.core.io.ImageEncoder;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import io.github.erenriquezp.rora.core.io.ImagingException;
import io.github.erenriquezp.rora.core.io.Iptc;
import io.github.erenriquezp.rora.core.io.SrgbProfile;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.w3c.dom.Node;

/** JPEG con aplanado de transparencia; los metadatos se insertan tras SOI/APP0. */
public final class JpegEncoder implements ImageEncoder {

  private static final String TREE = "javax_imageio_jpeg_image_1.0";
  private static final int MAX_SEGMENT = 65533;
  private static final byte[] EXIF_ID = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] XMP_ID =
      "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] ICC_ID = "ICC_PROFILE\0".getBytes(StandardCharsets.ISO_8859_1);

  @Override
  public FormatId format() {
    return FormatId.JPEG;
  }

  @Override
  public void encode(
      Img image,
      ExportSettings s,
      ImageMetadata m,
      Path target,
      CancellationToken token,
      Progress progress)
      throws ImagingException {
    try (OutputStream file = new BufferedOutputStream(Files.newOutputStream(target))) {
      encodeTo(image, s, m, file, token, progress);
    } catch (IOException e) {
      throw EncoderSupport.translate(e);
    }
  }

  /** Codifica en {@code sink}, sin cerrarlo. */
  void encodeTo(
      Img image,
      ExportSettings s,
      ImageMetadata m,
      OutputStream sink,
      CancellationToken token,
      Progress progress)
      throws ImagingException {
    EncoderSupport.throwIfCancelled(token);
    int[] px = image.toArray();
    AlphaFlatten.flatten(px, s.background());
    BufferedImage bi = EncoderSupport.wrap(px, image.width(), image.height(), false);
    ImageWriter writer = Codecs.writer(FormatId.JPEG);
    try (InjectingOutputStream injecting =
            new InjectingOutputStream(
                EncoderSupport.keepOpen(sink),
                segments(m, s, image.width(), image.height()),
                InjectingOutputStream::afterJpegApp0);
        ImageOutputStream out = new MemoryCacheImageOutputStream(injecting)) {
      EncoderSupport.attach(writer, token, progress);
      writer.setOutput(out);
      JPEGImageWriteParam param = new JPEGImageWriteParam(Locale.ROOT);
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(s.quality() / 100f);
      param.setOptimizeHuffmanTables(s.optimizeHuffman());
      param.setProgressiveMode(
          s.progressive() ? ImageWriteParam.MODE_DEFAULT : ImageWriteParam.MODE_DISABLED);
      writer.write(null, new IIOImage(bi, null, metadata(writer, bi, param, s)), param);
    } catch (IOException | RuntimeException e) {
      throw EncoderSupport.translate(e);
    } finally {
      writer.dispose();
    }
    EncoderSupport.throwIfCancelled(token);
  }

  private static IIOMetadata metadata(
      ImageWriter writer, BufferedImage bi, ImageWriteParam param, ExportSettings s)
      throws IOException {
    IIOMetadata md =
        writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(bi), param);
    if (s.subsampling() == ExportSettings.Subsampling.S444) {
      IIOMetadataNode tree = (IIOMetadataNode) md.getAsTree(TREE);
      Node sof = tree.getElementsByTagName("sof").item(0);
      for (Node n = sof.getFirstChild(); n != null; n = n.getNextSibling()) {
        IIOMetadataNode component = (IIOMetadataNode) n;
        component.setAttribute("HsamplingFactor", "1");
        component.setAttribute("VsamplingFactor", "1");
      }
      md.setFromTree(TREE, tree);
    }
    return md;
  }

  private static byte[] segments(ImageMetadata m, ExportSettings s, int w, int h)
      throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    if (m.exif().isPresent()) {
      byte[] tiff = ExifCodec.write(m.exif().get(), w, h);
      if (EXIF_ID.length + tiff.length <= MAX_SEGMENT) {
        out.write(EncoderSupport.jpegSegment(0xE1, EXIF_ID, tiff));
      }
    }
    if (m.xmp().isPresent() && XMP_ID.length + m.xmp().get().length <= MAX_SEGMENT) {
      out.write(EncoderSupport.jpegSegment(0xE1, XMP_ID, m.xmp().get()));
    }
    if (s.profile() == ExportSettings.ProfilePolicy.EMBED_SRGB) {
      out.write(EncoderSupport.jpegSegment(0xE2, ICC_ID, new byte[] {1, 1}, SrgbProfile.bytes()));
    }
    if (m.iptc().isPresent() && Iptc.toApp13(m.iptc().get()).length <= MAX_SEGMENT) {
      out.write(EncoderSupport.jpegSegment(0xED, Iptc.toApp13(m.iptc().get())));
    }
    return out.toByteArray();
  }
}
