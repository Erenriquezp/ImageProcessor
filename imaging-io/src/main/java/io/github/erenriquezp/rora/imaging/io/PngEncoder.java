package io.github.erenriquezp.rora.imaging.io;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ExifCodec;
import io.github.erenriquezp.rora.core.io.ExportSettings;
import io.github.erenriquezp.rora.core.io.FormatId;
import io.github.erenriquezp.rora.core.io.ImageEncoder;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import io.github.erenriquezp.rora.core.io.ImagingException;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** PNG con alfa no premultiplicado exacto; los metadatos se insertan tras IHDR. */
public final class PngEncoder implements ImageEncoder {

  private static final byte[] XMP_KEY =
      "XML:com.adobe.xmp\0\0\0\0\0".getBytes(StandardCharsets.ISO_8859_1);
  private static final int RELATIVE_COLORIMETRIC = 1;

  @Override
  public FormatId format() {
    return FormatId.PNG;
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
    ImageWriter writer = Codecs.writer(FormatId.PNG);
    try (OutputStream file = new BufferedOutputStream(Files.newOutputStream(target));
        InjectingOutputStream injecting =
            new InjectingOutputStream(
                file,
                chunks(m, s, image.width(), image.height()),
                InjectingOutputStream::afterPngIhdr);
        ImageOutputStream out = new MemoryCacheImageOutputStream(injecting)) {
      EncoderSupport.attach(writer, token, progress);
      writer.setOutput(out);
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality((9 - s.deflateLevel()) / 9f);
      writer.write(null, new IIOImage(bi, null, null), param);
    } catch (IOException | RuntimeException e) {
      throw EncoderSupport.translate(e);
    } finally {
      writer.dispose();
    }
    EncoderSupport.throwIfCancelled(token);
  }

  private static byte[] chunks(ImageMetadata m, ExportSettings s, int w, int h) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    if (s.profile() == ExportSettings.ProfilePolicy.EMBED_SRGB) {
      out.write(EncoderSupport.pngChunk("sRGB", new byte[] {RELATIVE_COLORIMETRIC}));
    }
    if (m.exif().isPresent()) {
      out.write(EncoderSupport.pngChunk("eXIf", ExifCodec.write(m.exif().get(), w, h)));
    }
    if (m.xmp().isPresent()) {
      out.write(EncoderSupport.pngChunk("iTXt", EncoderSupport.concat(XMP_KEY, m.xmp().get())));
    }
    for (Map.Entry<String, String> e : new TreeMap<>(m.text()).entrySet()) {
      byte[] kv =
          EncoderSupport.concat(
              e.getKey().getBytes(StandardCharsets.ISO_8859_1),
              new byte[] {0, 0, 0, 0, 0},
              e.getValue().getBytes(StandardCharsets.UTF_8));
      out.write(EncoderSupport.pngChunk("iTXt", kv));
    }
    return out.toByteArray();
  }
}
