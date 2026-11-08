package io.github.erenriquezp.rora.imaging.io;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ContainerScanner;
import io.github.erenriquezp.rora.core.io.DecodeOptions;
import io.github.erenriquezp.rora.core.io.DecodedImage;
import io.github.erenriquezp.rora.core.io.DecodedImage.Note;
import io.github.erenriquezp.rora.core.io.FormatId;
import io.github.erenriquezp.rora.core.io.IccInfo;
import io.github.erenriquezp.rora.core.io.ImageDecoder;
import io.github.erenriquezp.rora.core.io.ImageInfo;
import io.github.erenriquezp.rora.core.io.ImageInfo.Kind;
import io.github.erenriquezp.rora.core.io.ImageInfo.MetadataKind;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import io.github.erenriquezp.rora.core.io.ImagingException;
import io.github.erenriquezp.rora.core.io.MemoryBudget;
import io.github.erenriquezp.rora.core.io.Orientation;
import io.github.erenriquezp.rora.core.io.SampleScale;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBufferInt;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.event.IIOReadProgressListener;
import javax.imageio.event.IIOReadWarningListener;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;

/** Decodificador de todos los formatos, con un lector nuevo por operación. */
public final class ImageIoDecoder implements ImageDecoder {

  @Override
  public Set<FormatId> formats() {
    return EnumSet.allOf(FormatId.class);
  }

  @Override
  public ImageInfo inspect(Path file, CancellationToken token) throws ImagingException {
    throwIfCancelled(token);
    FormatId format = Codecs.sniff(file);
    ImageMetadata metadata = ContainerScanner.scan(format, file);
    ImageReader reader = Codecs.reader(format);
    try (ImageInputStream in = open(file)) {
      reader.setInput(in, false, true);
      return describe(reader, format, metadata, frames(reader, format));
    } catch (IOException | RuntimeException e) {
      throw new ImagingException.CorruptFile(e.toString(), e);
    } finally {
      reader.dispose();
    }
  }

  @Override
  public DecodedImage decode(
      Path file, DecodeOptions options, CancellationToken token, Progress progress)
      throws ImagingException {
    throwIfCancelled(token);
    FormatId format = Codecs.sniff(file);
    List<Note> notes = new ArrayList<>();
    ImageMetadata metadata = validated(ContainerScanner.scan(format, file), notes);
    ImageReader reader = Codecs.reader(format);
    int w = 0;
    int h = 0;
    try (ImageInputStream in = open(file)) {
      reader.setInput(in, false, true);
      w = reader.getWidth(0);
      h = reader.getHeight(0);
      ColorModel cm = firstModel(reader);
      Kind kind = kindOf(cm);
      int orientation = metadata.orientation();
      int extra =
          extraBytes(kind, format, cm) + (Orientation.swapsAxes(orientation) ? Integer.BYTES : 0);
      MemoryBudget.require(
          w,
          h,
          extra,
          options.retainedBytes(),
          Runtime.getRuntime().maxMemory(),
          options.maxPixels());

      Listener listener = new Listener(token, progress);
      reader.addIIOReadProgressListener(listener);
      reader.addIIOReadWarningListener(listener);

      int[] argb = read(reader, format, kind, cm, metadata, w, h, token, listener, notes);

      listener.throwIfFailed();
      throwIfCancelled(token);
      int[] oriented = Orientation.apply(orientation, argb, w, h);
      boolean swap = Orientation.swapsAxes(orientation);
      Img image = Img.adopt(swap ? h : w, swap ? w : h, oriented);

      int frames = frames(reader, format);
      if (frames > 1) {
        notes.add(Note.of("io.note.frames", Integer.toString(frames)));
      }
      if (bits(cm) > 8) {
        notes.add(Note.of("io.note.16bit"));
      }
      return new DecodedImage(image, describe(reader, format, metadata, frames), metadata, notes);
    } catch (ImagingException e) {
      throw e;
    } catch (OutOfMemoryError e) {
      throw new ImagingException.TooLarge(w, h, e.toString());
    } catch (FileNotFoundException | NoSuchFileException e) {
      throw new ImagingException.IoFailure("io.error.read", e.toString(), e);
    } catch (IOException | RuntimeException e) {
      throw new ImagingException.CorruptFile(e.toString(), e);
    } finally {
      reader.dispose();
    }
  }

  private static ImageInputStream open(Path file) throws IOException {
    return new FileImageInputStream(file.toFile());
  }

  private static void throwIfCancelled(CancellationToken token) throws ImagingException.Cancelled {
    if (token.isCancelled()) {
      throw new ImagingException.Cancelled();
    }
  }

  private static ImageMetadata validated(ImageMetadata m, List<Note> notes) {
    boolean badXmp = m.xmp().isPresent() && !XmpValidator.isValid(m.xmp().get());
    if (badXmp || m.damaged()) {
      notes.add(Note.of("io.note.metadata_dropped"));
    }
    return badXmp
        ? new ImageMetadata(m.exif(), Optional.empty(), m.iptc(), m.icc(), m.text(), m.damaged())
        : m;
  }

  private static ColorModel firstModel(ImageReader r) throws IOException {
    Iterator<ImageTypeSpecifier> it = r.getImageTypes(0);
    if (!it.hasNext()) {
      throw new IOException("sin tipos de imagen");
    }
    return it.next().getColorModel();
  }

  private static Kind kindOf(ColorModel cm) {
    if (cm instanceof IndexColorModel) {
      return Kind.INDEXED;
    }
    return switch (cm.getColorSpace().getType()) {
      case ColorSpace.TYPE_GRAY -> Kind.GRAY;
      case ColorSpace.TYPE_CMYK -> Kind.CMYK;
      case ColorSpace.TYPE_RGB, ColorSpace.TYPE_YCbCr, ColorSpace.TYPE_3CLR -> Kind.RGB;
      default -> Kind.OTHER;
    };
  }

  private static int bits(ColorModel cm) {
    return cm instanceof IndexColorModel ? cm.getPixelSize() : cm.getComponentSize(0);
  }

  /** Memoria del búfer intermedio del lector, por píxel, además de los 4 bytes de {@code Img}. */
  private static int extraBytes(Kind kind, FormatId format, ColorModel cm) {
    if (usesDirectDestination(kind, format, cm)) {
      return 0;
    }
    return Math.max(1, cm.getPixelSize() / 8);
  }

  private static boolean usesDirectDestination(Kind kind, FormatId format, ColorModel cm) {
    return kind == Kind.RGB && format != FormatId.WEBP && !cm.isAlphaPremultiplied();
  }

  private int[] read(
      ImageReader reader,
      FormatId format,
      Kind kind,
      ColorModel cm,
      ImageMetadata metadata,
      int w,
      int h,
      CancellationToken token,
      Listener listener,
      List<Note> notes)
      throws IOException, ImagingException {
    Optional<byte[]> icc = metadata.icc();
    if (usesDirectDestination(kind, format, cm)) {
      Optional<int[]> direct = readDirect(reader, w, h, cm.hasAlpha());
      if (direct.isPresent()) {
        int[] px = direct.get();
        listener.throwIfFailed();
        throwIfCancelled(token);
        applyRgbProfile(px, w, h, icc, !readerAppliesProfile(format), token, notes);
        return px;
      }
    }
    BufferedImage bi = reader.read(0);
    listener.throwIfFailed();
    throwIfCancelled(token);
    return switch (kindOf(bi.getColorModel())) {
      case INDEXED -> {
        int[] px = indexed(bi, w, h);
        applyRgbProfile(px, w, h, icc, true, token, notes);
        yield px;
      }
      case GRAY -> gray(bi, w, h, icc, token, notes);
      case CMYK -> cmyk(bi, icc, token, notes);
      case RGB -> {
        int[] px = bi.getRGB(0, 0, w, h, null, 0, w);
        applyRgbProfile(px, w, h, icc, !readerAppliesProfile(format), token, notes);
        yield px;
      }
      case OTHER -> {
        notes.add(Note.of("io.note.fallback"));
        yield bi.getRGB(0, 0, w, h, null, 0, w);
      }
    };
  }

  /** Medido: estos lectores ya convierten con el perfil del archivo. */
  private static boolean readerAppliesProfile(FormatId format) {
    return format == FormatId.JPEG || format == FormatId.WEBP;
  }

  private static Optional<int[]> readDirect(ImageReader reader, int w, int h, boolean alpha)
      throws IOException {
    BufferedImage dst =
        new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    ImageReadParam param = reader.getDefaultReadParam();
    param.setDestination(dst);
    try {
      reader.read(0, param);
    } catch (IllegalArgumentException e) {
      return Optional.empty(); // El lector no admite ese destino: camino general.
    }
    int[] px = ((DataBufferInt) dst.getRaster().getDataBuffer()).getData();
    if (!alpha) {
      for (int i = 0; i < px.length; i++) {
        px[i] |= 0xFF000000;
      }
    }
    return Optional.of(px);
  }

  /**
   * Convierte con el perfil si procede.
   *
   * @param convert {@code false} si el lector ya lo aplicó
   */
  private static void applyRgbProfile(
      int[] px,
      int w,
      int h,
      Optional<byte[]> icc,
      boolean convert,
      CancellationToken token,
      List<Note> notes)
      throws ImagingException.Cancelled {
    if (icc.isEmpty()) {
      return;
    }
    Optional<ICC_Profile> profile = ColorPipeline.profile(icc.get(), 3);
    if (profile.isEmpty()) {
      notes.add(Note.of("io.note.bad_profile"));
      return;
    }
    if (ColorPipeline.equivalentToSrgb(profile.get())) {
      return;
    }
    if (convert) {
      ColorPipeline.applyRgb(px, w, h, profile.get(), token);
    }
    notes.add(Note.of("io.note.converted", name(icc.get())));
  }

  private static String name(byte[] icc) {
    return IccInfo.description(icc).orElse("?");
  }

  private static int[] indexed(BufferedImage bi, int w, int h) {
    IndexColorModel icm = (IndexColorModel) bi.getColorModel();
    int[] table = new int[icm.getMapSize()];
    icm.getRGBs(table);
    Raster r = bi.getRaster();
    int[] row = new int[w];
    int[] out = new int[w * h];
    for (int y = 0, o = 0; y < h; y++) {
      r.getPixels(0, y, w, 1, row);
      for (int x = 0; x < w; x++, o++) {
        out[o] = table[row[x]];
      }
    }
    return out;
  }

  private static int[] gray(
      BufferedImage bi,
      int w,
      int h,
      Optional<byte[]> icc,
      CancellationToken token,
      List<Note> notes)
      throws ImagingException.Cancelled {
    Raster r = bi.getRaster();
    int bands = r.getNumBands();
    int bits = bi.getColorModel().getComponentSize(0);
    int[] lut = null;
    if (icc.isPresent()) {
      Optional<ICC_Profile> p = ColorPipeline.profile(icc.get(), 1);
      if (p.isPresent()) {
        lut = ColorPipeline.grayLut(p.get());
        notes.add(Note.of("io.note.converted", name(icc.get())));
      } else {
        notes.add(Note.of("io.note.bad_profile"));
      }
    }
    int[] row = new int[w * bands];
    int[] out = new int[w * h];
    for (int y = 0, o = 0; y < h; y++) {
      if ((y & 63) == 0) {
        throwIfCancelled(token);
      }
      r.getPixels(0, y, w, 1, row);
      for (int x = 0, j = 0; x < w; x++, j += bands, o++) {
        int v = scale(row[j], bits);
        if (lut != null) {
          v = lut[v];
        }
        int a = bands > 1 ? scale(row[j + 1], bits) : 255;
        out[o] = a << 24 | v << 16 | v << 8 | v;
      }
    }
    return out;
  }

  private static int scale(int sample, int bits) {
    if (bits == 8) {
      return sample;
    }
    if (bits == 16) {
      return SampleScale.to8(sample);
    }
    int max = (1 << bits) - 1;
    return (sample * 255 + max / 2) / max;
  }

  private static int[] cmyk(
      BufferedImage bi, Optional<byte[]> icc, CancellationToken token, List<Note> notes)
      throws ImagingException.Cancelled {
    Raster r = bi.getRaster();
    Optional<ICC_Profile> p = icc.flatMap(b -> ColorPipeline.profile(b, 4));
    if (p.isPresent()) {
      notes.add(Note.of("io.note.converted", name(icc.get())));
      return ColorPipeline.cmykProfile(r, p.get(), token);
    }
    notes.add(Note.of(icc.isPresent() ? "io.note.bad_profile" : "io.note.cmyk_naive"));
    return ColorPipeline.cmykFormula(r, token);
  }

  private static int frames(ImageReader reader, FormatId format) {
    if (format != FormatId.GIF && format != FormatId.TIFF) {
      return 1;
    }
    try {
      return Math.max(1, reader.getNumImages(true));
    } catch (IOException | RuntimeException e) {
      return 1; // Un fotograma posterior dañado no impide usar el primero.
    }
  }

  private static ImageInfo describe(
      ImageReader reader, FormatId format, ImageMetadata m, int frames) throws IOException {
    ColorModel cm = firstModel(reader);
    Set<MetadataKind> present = EnumSet.noneOf(MetadataKind.class);
    m.exif()
        .filter(e -> !e.isEmpty())
        .ifPresent(
            e -> {
              present.add(MetadataKind.EXIF);
              if (!e.gps().isEmpty()) {
                present.add(MetadataKind.GPS);
              }
            });
    if (m.xmp().isPresent()) {
      present.add(MetadataKind.XMP);
    }
    if (m.iptc().isPresent()) {
      present.add(MetadataKind.IPTC);
    }
    if (m.icc().isPresent()) {
      present.add(MetadataKind.ICC);
    }
    if (!m.text().isEmpty()) {
      present.add(MetadataKind.TEXT);
    }
    return new ImageInfo(
        format,
        reader.getWidth(0),
        reader.getHeight(0),
        m.orientation(),
        bits(cm),
        kindOf(cm),
        cm.hasAlpha(),
        m.icc().flatMap(IccInfo::description),
        frames,
        present);
  }

  /** Cancelación, progreso y avisos fatales del lector. */
  private static final class Listener implements IIOReadProgressListener, IIOReadWarningListener {
    private final CancellationToken token;
    private final Progress progress;
    private volatile String fatal;

    Listener(CancellationToken token, Progress progress) {
      this.token = token;
      this.progress = progress;
    }

    void throwIfFailed() throws ImagingException.CorruptFile {
      if (fatal != null) {
        throw new ImagingException.CorruptFile(fatal, null);
      }
    }

    @Override
    public void imageProgress(ImageReader source, float percentageDone) {
      if (token.isCancelled()) {
        source.abort();
      }
      progress.update(Math.min(100, (long) percentageDone), 100);
    }

    @Override
    public void warningOccurred(ImageReader source, String warning) {
      String w = warning.toLowerCase(Locale.ROOT);
      if (w.contains("truncated") || w.contains("premature end")) {
        fatal = warning;
      }
    }

    @Override
    public void sequenceStarted(ImageReader source, int minIndex) {}

    @Override
    public void sequenceComplete(ImageReader source) {}

    @Override
    public void imageStarted(ImageReader source, int imageIndex) {}

    @Override
    public void imageComplete(ImageReader source) {}

    @Override
    public void thumbnailStarted(ImageReader source, int imageIndex, int thumbnailIndex) {}

    @Override
    public void thumbnailProgress(ImageReader source, float percentageDone) {}

    @Override
    public void thumbnailComplete(ImageReader source) {}

    @Override
    public void readAborted(ImageReader source) {}
  }
}
