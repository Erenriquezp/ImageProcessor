import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ZIP reproducible de una carpeta: entradas en orden fijo (ruta con {@code /}), fecha fija tomada de
 * {@code SOURCE_DATE_EPOCH} (la del último commit), sin atributos del sistema ni campos extra, y
 * compresión fija. Dos ejecuciones sobre el mismo árbol dan los mismos bytes.
 *
 * <pre>java tools/package/ReproZip.java CARPETA SALIDA.zip EPOCA_EN_SEGUNDOS</pre>
 *
 * La carpeta entra con su nombre como raíz del ZIP ({@code Rora Studio/...}).
 */
public final class ReproZip {

  private ReproZip() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      System.err.println("Uso: java ReproZip.java CARPETA SALIDA.zip EPOCA");
      System.exit(2);
    }
    Path dir = Path.of(args[0]).toAbsolutePath().normalize();
    Path zip = Path.of(args[1]).toAbsolutePath();
    // Fecha DOS en hora "local" fija: sin zona horaria, el resultado no depende del equipo.
    LocalDateTime when =
        LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(args[2])), ZoneOffset.UTC);
    if (when.getYear() < 1980) {
      when = LocalDateTime.of(1980, 1, 1, 0, 0);
    }
    String root = dir.getFileName().toString();
    List<Path> entries;
    try (Stream<Path> s = Files.walk(dir)) {
      entries =
          s.filter(p -> !p.equals(dir))
              .sorted((a, b) -> rel(dir, a).compareTo(rel(dir, b)))
              .toList();
    }
    Files.createDirectories(zip.getParent());
    try (OutputStream out = Files.newOutputStream(zip);
        ZipOutputStream z = new ZipOutputStream(out)) {
      z.setLevel(Deflater.BEST_COMPRESSION);
      z.setMethod(ZipOutputStream.DEFLATED);
      ZipEntry top = new ZipEntry(root + "/");
      top.setTimeLocal(when);
      z.putNextEntry(top);
      z.closeEntry();
      for (Path p : entries) {
        boolean folder = Files.isDirectory(p);
        ZipEntry e = new ZipEntry(root + "/" + rel(dir, p) + (folder ? "/" : ""));
        e.setTimeLocal(when);
        z.putNextEntry(e);
        if (!folder) {
          Files.copy(p, z);
        }
        z.closeEntry();
      }
    }
    System.out.println("ZIP: " + zip + " (" + entries.size() + " entradas)");
  }

  private static String rel(Path dir, Path p) {
    return dir.relativize(p).toString().replace('\\', '/');
  }
}
