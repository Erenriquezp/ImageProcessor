package io.github.erenriquezp.rora.imaging.io;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ExportSettings;
import io.github.erenriquezp.rora.core.io.FormatId;
import io.github.erenriquezp.rora.core.io.ImageEncoder;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import io.github.erenriquezp.rora.core.io.ImageMetadata.ExifTag;
import io.github.erenriquezp.rora.core.io.ImagingException;
import io.github.erenriquezp.rora.core.io.ProvenanceXmp;
import io.github.erenriquezp.rora.core.io.SrgbProfile;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.IIOImage;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.plugins.tiff.BaselineTIFFTagSet;
import javax.imageio.plugins.tiff.TIFFDirectory;
import javax.imageio.plugins.tiff.TIFFField;
import javax.imageio.plugins.tiff.TIFFTag;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** TIFF sin pérdida. De los metadatos solo se escriben las etiquetas base de texto. */
public final class TiffEncoder implements ImageEncoder {

  /** Etiqueta 700: XMP, según la especificación de XMP para TIFF. */
  private static final TIFFTag XMP_TAG = new TIFFTag("XMP", 700, 1 << TIFFTag.TIFF_BYTE);

  private static final Map<Integer, Integer> BASE_TAGS =
      Map.of(
          0x010F, BaselineTIFFTagSet.TAG_MAKE,
          0x0110, BaselineTIFFTagSet.TAG_MODEL,
          0x0132, BaselineTIFFTagSet.TAG_DATE_TIME,
          0x013B, BaselineTIFFTagSet.TAG_ARTIST,
          0x8298, BaselineTIFFTagSet.TAG_COPYRIGHT);

  @Override
  public FormatId format() {
    return FormatId.TIFF;
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
    EncoderSupport.throwIfCancelled(token);
    int[] px = image.toArray();
    BufferedImage bi =
        EncoderSupport.wrap(px, image.width(), image.height(), EncoderSupport.hasAlpha(px));
    ImageWriter writer = Codecs.writer(FormatId.TIFF);
    try {
      Files.deleteIfExists(target); // FileImageOutputStream no trunca.
      long estimate =
          (long) image.width() * image.height() * (bi.getColorModel().hasAlpha() ? 4 : 3);
      try (ImageOutputStream out =
          new CancellableOutput(target.toFile(), token, progress, estimate)) {
        writer.setOutput(out);
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (s.tiff() == ExportSettings.TiffCompression.NONE) {
          param.setCompressionMode(ImageWriteParam.MODE_DISABLED);
        } else {
          param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
          param.setCompressionType(compression(s.tiff()));
        }
        writer.write(null, new IIOImage(bi, null, metadata(writer, bi, param, s, m)), param);
      }
    } catch (IOException | RuntimeException e) {
      if (token.isCancelled()) {
        throw new ImagingException.Cancelled();
      }
      throw EncoderSupport.translate(e);
    } finally {
      writer.dispose();
    }
    EncoderSupport.throwIfCancelled(token);
  }

  /**
   * El escritor TIFF del JDK no emite progreso ni atiende {@code abort()} (medido): la cancelación
   * y el progreso se hacen en el flujo de salida.
   */
  private static final class CancellableOutput extends FileImageOutputStream {
    private final CancellationToken token;
    private final Progress progress;
    private final long estimate;
    private long written;

    CancellableOutput(File file, CancellationToken token, Progress progress, long estimate)
        throws IOException {
      super(file);
      this.token = token;
      this.progress = progress;
      this.estimate = Math.max(1, estimate);
    }

    private void wrote(int n) throws IOException {
      written += n;
      progress.update(Math.min(99, written * 100 / estimate), 100);
      if (token.isCancelled()) {
        throw new IOException("cancelado");
      }
    }

    @Override
    public void write(int b) throws IOException {
      super.write(b);
      if (written % 4096 == 0) {
        wrote(1);
      } else {
        written++;
      }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      super.write(b, off, len);
      wrote(len);
    }
  }

  /** Tamaño en bytes sin escribir a disco; solo para estimar. */
  long sizeInMemory(Img image, ExportSettings s, CancellationToken token) throws ImagingException {
    EncoderSupport.throwIfCancelled(token);
    int[] px = image.toArray();
    BufferedImage bi =
        EncoderSupport.wrap(px, image.width(), image.height(), EncoderSupport.hasAlpha(px));
    ImageWriter writer = Codecs.writer(FormatId.TIFF);
    CountingOutputStream counter = new CountingOutputStream();
    try (ImageOutputStream out = new MemoryCacheImageOutputStream(counter)) {
      writer.setOutput(out);
      ImageWriteParam param = writer.getDefaultWriteParam();
      if (s.tiff() == ExportSettings.TiffCompression.NONE) {
        param.setCompressionMode(ImageWriteParam.MODE_DISABLED);
      } else {
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionType(compression(s.tiff()));
      }
      writer.write(null, new IIOImage(bi, null, null), param);
    } catch (IOException | RuntimeException e) {
      throw EncoderSupport.translate(e);
    } finally {
      writer.dispose();
    }
    return counter.count();
  }

  private static String compression(ExportSettings.TiffCompression c) {
    return switch (c) {
      case LZW -> "LZW";
      case DEFLATE -> "Deflate";
      case PACKBITS -> "PackBits";
      case NONE -> throw new IllegalArgumentException("NONE no tiene tipo");
    };
  }

  private static IIOMetadata metadata(
      ImageWriter writer,
      BufferedImage bi,
      ImageWriteParam param,
      ExportSettings s,
      ImageMetadata m)
      throws IOException {
    IIOMetadata base =
        writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(bi), param);
    TIFFDirectory dir = TIFFDirectory.createFromMetadata(base);
    BaselineTIFFTagSet tags = BaselineTIFFTagSet.getInstance();
    if (m.exif().isPresent()) {
      for (ExifTag t : m.exif().get().ifd0()) {
        Integer id = BASE_TAGS.get(t.id());
        if (id != null) {
          String text = new String(t.value(), 0, t.value().length - 1, StandardCharsets.UTF_8);
          dir.addTIFFField(
              new TIFFField(tags.getTag(id), TIFFTag.TIFF_ASCII, 1, new String[] {text}));
        }
      }
    }
    // Solo la marca de IA: el XMP de un TIFF sin mejora sigue sin escribirse.
    if (m.xmp().filter(ProvenanceXmp::isMarked).isPresent()) {
      byte[] xmp = m.xmp().get();
      dir.addTIFFField(new TIFFField(XMP_TAG, TIFFTag.TIFF_BYTE, xmp.length, xmp));
    }
    if (s.profile() == ExportSettings.ProfilePolicy.EMBED_SRGB) {
      byte[] icc = SrgbProfile.bytes();
      dir.addTIFFField(
          new TIFFField(
              tags.getTag(BaselineTIFFTagSet.TAG_ICC_PROFILE),
              TIFFTag.TIFF_UNDEFINED,
              icc.length,
              icc));
    }
    return dir.getAsMetadata();
  }
}
