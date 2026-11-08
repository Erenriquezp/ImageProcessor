package io.github.erenriquezp.rora.imaging.io;

import io.github.erenriquezp.rora.core.io.FileNames;
import io.github.erenriquezp.rora.core.io.ImagingException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;

/** Publica un temporal hermano en su destino final sin dejar nunca un archivo a medias. */
final class AtomicWriter {

  private static final int MAX_NUMBER = 9999;

  private AtomicWriter() {}

  /** Temporal en la misma carpeta: otro volumen rompería la atomicidad. */
  static Path temporary(Path folder) {
    return folder.resolve(".rora-export-" + UUID.randomUUID() + ".tmp");
  }

  static void force(Path file) throws IOException {
    try (FileChannel ch = FileChannel.open(file, StandardOpenOption.WRITE)) {
      ch.force(true);
    }
  }

  /** Publica {@code tmp}; devuelve la ruta final. */
  static Path publish(Path tmp, Path target, Exporter.Request request) throws ImagingException {
    try {
      return switch (request.collision()) {
        case ASK -> {
          if (!linkOrMove(tmp, target)) {
            throw new ImagingException.IoFailure(
                "io.error.exists", "ya existe: " + target, null, target.getFileName().toString());
          }
          yield target;
        }
        case NUMBER -> numbered(tmp, target);
        case OVERWRITE -> {
          overwrite(tmp, target, request);
          yield target;
        }
      };
    } catch (IOException e) {
      throw new ImagingException.IoFailure("io.error.write", e.toString(), e);
    }
  }

  private static Path numbered(Path tmp, Path target) throws IOException, ImagingException {
    if (linkOrMove(tmp, target)) {
      return target;
    }
    String name = target.getFileName().toString();
    String base = FileNames.baseName(name);
    String ext = name.substring(base.length());
    for (int n = 2; n <= MAX_NUMBER; n++) {
      Path candidate = target.resolveSibling(base + " (" + n + ")" + ext);
      if (linkOrMove(tmp, candidate)) {
        return candidate;
      }
    }
    throw new ImagingException.IoFailure("io.error.exists", "sin nombre libre", null, name);
  }

  private static void overwrite(Path tmp, Path target, Exporter.Request r)
      throws IOException, ImagingException {
    if (!r.overwriteConfirmed()) {
      throw new ImagingException.IoFailure(
          "io.error.exists", "sin confirmación", null, target.getFileName().toString());
    }
    Optional<Path> original = r.original();
    if (original.isPresent()
        && Files.exists(target)
        && Files.isSameFile(original.get(), target)
        && !r.overwriteOriginalConfirmed()) {
      throw new ImagingException.IoFailure(
          "io.error.original", "sin confirmación", null, target.getFileName().toString());
    }
    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
  }

  /** Sin reemplazar nunca: {@code ATOMIC_MOVE} sí reemplazaría en Windows. */
  private static boolean linkOrMove(Path tmp, Path target) throws IOException {
    try {
      Files.createLink(target, tmp);
      Files.delete(tmp);
      return true;
    } catch (FileAlreadyExistsException e) {
      return false;
    } catch (UnsupportedOperationException | FileSystemException e) {
      try {
        Files.move(tmp, target);
        return true;
      } catch (FileAlreadyExistsException exists) {
        return false;
      }
    }
  }
}
