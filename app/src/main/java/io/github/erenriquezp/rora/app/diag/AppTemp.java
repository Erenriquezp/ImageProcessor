package io.github.erenriquezp.rora.app.diag;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Directorio temporal propio ({@code -Djava.io.tmpdir=%LOCALAPPDATA%\RoraStudio\tmp} en la versión
 * instalada): así ni ONNX Runtime ni nadie deja nada en {@code %TEMP%}. Se crea antes de tocar
 * ninguna biblioteca (ORT no carga si no existe) y se vacía al arrancar si no hay otra instancia.
 */
public final class AppTemp {

  static final String LOCK = ".lock";

  // Mantiene el bloqueo mientras viva el proceso.
  private static FileChannel heldChannel;
  private static FileLock heldLock;

  private AppTemp() {}

  /**
   * Directorio que usar: el de {@code java.io.tmpdir} si es absoluto; si no (p. ej. jpackage no
   * pudo expandir {@code $LOCALAPPDATA}), el de la app, y fija la propiedad.
   */
  public static Path resolve(String tmpdirProperty, AppDirs dirs) {
    if (tmpdirProperty != null && !tmpdirProperty.isBlank()) {
      Path p = Path.of(tmpdirProperty);
      if (p.isAbsolute()) {
        return p;
      }
    }
    return dirs.tmp();
  }

  /** Crea el directorio, se queda con su bloqueo y lo vacía si es la única instancia. */
  public static synchronized Path prepare(Path dir) throws IOException {
    Files.createDirectories(dir);
    if (heldLock == null && acquire(dir)) {
      clean(dir);
    }
    return dir;
  }

  private static boolean acquire(Path dir) throws IOException {
    FileChannel ch =
        FileChannel.open(dir.resolve(LOCK), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    try {
      FileLock lock = ch.tryLock();
      if (lock == null) {
        ch.close();
        return false;
      }
      heldChannel = ch;
      heldLock = lock;
      return true;
    } catch (OverlappingFileLockException e) {
      ch.close();
      return false;
    }
  }

  /**
   * Borra todo lo que hay dentro salvo el bloqueo, sin seguir enlaces.
   *
   * @return entradas borradas
   */
  static int clean(Path dir) {
    int n = 0;
    try (Stream<Path> s = Files.walk(dir)) {
      List<Path> all =
          s.filter(p -> !p.equals(dir) && !p.getFileName().toString().equals(LOCK))
              .sorted(Comparator.comparingInt(Path::getNameCount).reversed())
              .toList();
      for (Path p : all) {
        if (Files.isSymbolicLink(p) || Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
          try {
            Files.delete(p);
            n++;
          } catch (IOException e) {
            // en uso: se queda hasta el próximo arranque
          }
        }
      }
    } catch (IOException e) {
      return n;
    }
    return n;
  }

  /** Para tests: suelta el bloqueo. */
  static synchronized void release() throws IOException {
    if (heldLock != null) {
      heldLock.release();
      heldChannel.close();
      heldLock = null;
      heldChannel = null;
    }
  }
}
