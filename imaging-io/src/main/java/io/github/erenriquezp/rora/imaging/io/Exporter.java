package io.github.erenriquezp.rora.imaging.io;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.CancelledException;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ExportSettings;
import io.github.erenriquezp.rora.core.io.ImageEncoder;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import io.github.erenriquezp.rora.core.io.ImagingException;
import io.github.erenriquezp.rora.core.io.MetadataPolicyApplier;
import io.github.erenriquezp.rora.core.io.QualitySearch;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.Params;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Exportación completa: reducción, peso objetivo, metadatos y publicación atómica. Un fallo o una
 * cancelación no dejan temporales ni tocan un archivo previo.
 */
public final class Exporter {

  /** Qué hacer si el destino existe. */
  public enum Collision {
    ASK,
    NUMBER,
    OVERWRITE
  }

  /**
   * Destino de la exportación.
   *
   * @param folder carpeta de destino
   * @param fileName nombre final, ya saneado
   * @param overwriteConfirmed el usuario confirmó sobrescribir
   * @param overwriteOriginalConfirmed el usuario confirmó sobrescribir el archivo original
   * @param original archivo del que procede la imagen, si lo hay
   */
  public record Request(
      Path folder,
      String fileName,
      Collision collision,
      boolean overwriteConfirmed,
      boolean overwriteOriginalConfirmed,
      Optional<Path> original) {
    public Request {
      Objects.requireNonNull(folder, "folder");
      Objects.requireNonNull(fileName, "fileName");
      Objects.requireNonNull(collision, "collision");
      Objects.requireNonNull(original, "original");
    }

    /** Sin sobrescribir: pregunta si ya existe. */
    public static Request ask(Path folder, String fileName) {
      return new Request(folder, fileName, Collision.ASK, false, false, Optional.empty());
    }
  }

  /** Resultado: archivo escrito, dimensiones finales, peso y calidad usada. */
  public record Result(Path file, int width, int height, long bytes, int quality) {}

  /** Punto de inyección de fallos en las pruebas. */
  interface Stage {
    Stage NONE = name -> {};

    void reached(String name) throws IOException;
  }

  private static final String RESIZE = "transform.resize";

  private final Stage stage;

  public Exporter() {
    this(Stage.NONE);
  }

  Exporter(Stage stage) {
    this.stage = stage;
  }

  public Result export(
      Img image,
      ImageMetadata source,
      ExportSettings settings,
      Request request,
      TileExecutor executor,
      CancellationToken token,
      Progress progress)
      throws ImagingException {
    EncoderSupport.throwIfCancelled(token);
    Img output = limited(image, settings.maxLongSide(), executor, token);
    ImageMetadata metadata = MetadataPolicyApplier.apply(source, settings.metadata());
    ImageEncoder encoder = Codecs.encoder(settings.format());
    Path target = request.folder().resolve(request.fileName());
    Path tmp = AtomicWriter.temporary(request.folder());
    try {
      int[] last = {-1};
      int quality = settings.quality();
      if (settings.maxBytes() > 0) {
        quality =
            QualitySearch.search(
                    settings.quality(),
                    settings.maxBytes(),
                    q -> {
                      encoder.encode(
                          output, settings.withQuality(q), metadata, tmp, token, Progress.NONE);
                      last[0] = q;
                      return sizeOf(tmp);
                    })
                .quality();
      }
      if (last[0] != quality) {
        encoder.encode(output, settings.withQuality(quality), metadata, tmp, token, progress);
      }
      stage.reached("encoded");
      EncoderSupport.throwIfCancelled(token);
      AtomicWriter.force(tmp);
      stage.reached("forced");
      Path published = AtomicWriter.publish(tmp, target, request);
      return new Result(published, output.width(), output.height(), Files.size(published), quality);
    } catch (IOException e) {
      throw new ImagingException.IoFailure("io.error.write", e.toString(), e);
    } finally {
      deleteQuietly(tmp);
    }
  }

  private static long sizeOf(Path file) throws ImagingException {
    try {
      return Files.size(file);
    } catch (IOException e) {
      throw new ImagingException.IoFailure("io.error.write", e.toString(), e);
    }
  }

  private static void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      System.getLogger(Exporter.class.getName())
          .log(System.Logger.Level.WARNING, "No se pudo borrar el temporal " + file, e);
    }
  }

  /** Reduce si el lado mayor supera el límite; nunca amplía. */
  private static Img limited(Img image, int maxLongSide, TileExecutor executor, CancellationToken t)
      throws ImagingException.Cancelled {
    int side = Math.max(image.width(), image.height());
    if (maxLongSide <= 0 || side <= maxLongSide) {
      return image;
    }
    Operation resize = OpRegistry.load().require(RESIZE);
    Params params = Params.builder(resize.schema()).set("longSide", maxLongSide).build();
    try {
      return resize.apply(image, params, new OpContext(executor, t, Progress.NONE)).image();
    } catch (CancelledException e) {
      throw new ImagingException.Cancelled();
    }
  }
}
