package io.github.erenriquezp.rora.app.session;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.DecodeOptions;
import io.github.erenriquezp.rora.core.io.DecodedImage;
import io.github.erenriquezp.rora.core.io.ExportSettings;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import io.github.erenriquezp.rora.core.io.ImagingException;
import io.github.erenriquezp.rora.core.io.ProvenanceXmp;
import io.github.erenriquezp.rora.imaging.io.Codecs;
import io.github.erenriquezp.rora.imaging.io.Exporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Apertura y exportación para ejecutar fuera del hilo FX. La interfaz solo habla con esta capa. */
public final class StudioIo {

  /** Una imagen abierta y sus avisos. */
  public record Opened(SessionSource source, DecodedImage decoded) {}

  /** Situación del destino antes de exportar. */
  public enum TargetState {
    FREE,
    EXISTS,
    ORIGINAL
  }

  /** Resultado de una exportación. */
  public record Exported(Path file, int width, int height, long bytes, int quality) {}

  private static final System.Logger LOG = System.getLogger("rora.io");

  private StudioIo() {}

  /**
   * @param retainedBytes memoria que sigue ocupada mientras se decodifica
   */
  public static Opened open(
      Path file, long retainedBytes, CancellationToken token, Progress progress)
      throws ImagingException {
    DecodeOptions options = new DecodeOptions(retainedBytes, Long.getLong("rora.io.maxPixels", 0));
    DecodedImage decoded =
        Codecs.decoder(Codecs.sniff(file)).decode(file, options, token, progress);
    // Tamaño y formato ayudan a diagnosticar; el nombre del archivo no se registra.
    LOG.log(
        System.Logger.Level.INFO,
        () ->
            "Imagen abierta: "
                + decoded.image().width()
                + "x"
                + decoded.image().height()
                + " "
                + decoded.info().format());
    return new Opened(SessionSource.ofDecoded(decoded, file), decoded);
  }

  /** ¿Existe el destino, y es el archivo original? */
  public static TargetState targetState(Path target, Optional<Path> original) throws IOException {
    if (!Files.exists(target)) {
      return TargetState.FREE;
    }
    return original.isPresent() && Files.isSameFile(original.get(), target)
        ? TargetState.ORIGINAL
        : TargetState.EXISTS;
  }

  /**
   * @param provenance marca de IA; se ignora si la política de metadatos es quitarlo todo
   */
  public static Exported export(
      Img image,
      ImageMetadata metadata,
      ExportSettings settings,
      Optional<ProvenanceXmp.Provenance> provenance,
      ExportTarget target,
      CancellationToken token,
      Progress progress)
      throws ImagingException {
    Exporter.Request request =
        new Exporter.Request(
            target.folder(),
            target.fileName(),
            Exporter.Collision.valueOf(target.collision().name()),
            target.overwriteConfirmed(),
            target.originalConfirmed(),
            target.original());
    Exporter.Result r =
        new Exporter()
            .export(
                image,
                metadata,
                settings,
                provenance,
                request,
                TileExecutor.sequential(),
                token,
                progress);
    return new Exported(r.file(), r.width(), r.height(), r.bytes(), r.quality());
  }

  /** Peso aproximado, codificando {@code preview} en memoria. */
  public static long estimate(
      Img preview, ExportSettings settings, int width, int height, CancellationToken token)
      throws ImagingException {
    return Exporter.estimateBytes(preview, settings, width, height, token);
  }
}
