package io.github.erenriquezp.rora.ai.store;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Downscale;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.ArtifactLookup;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.CRC32;

/**
 * Resultados de IA en disco, solo durante la sesión. Cada proceso escribe en su propia carpeta
 * {@code session-<uuid>}, bloqueada con un {@code FileLock}: al arrancar se borran las carpetas
 * cuyo bloqueo se pueda adquirir, que son las de procesos que ya no existen.
 *
 * <p>Solo se conservan en memoria los proxies. El resultado completo se relee del disco, y la caché
 * del renderer se ocupa de no repetir esa lectura. Segura para hilos.
 */
public final class ArtifactStore implements ArtifactLookup, AutoCloseable {

  private static final int MAGIC = 0x524F5241;
  private static final int HEADER = Integer.BYTES * 4;
  private static final String PREFIX = "session-";
  private static final String LOCK = "session.lock";
  private static final int CHUNK = 1 << 16;

  private record Entry(Path file, int width, int height, long bytes) {}

  private record ProxyKey(int id, int width, int height) {}

  private final Path dir;
  private final long maxBytes;
  private final long maxProxyBytes;
  private final FileChannel lockChannel;
  private final FileLock lock;
  private final LinkedHashMap<Integer, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
  private final LinkedHashMap<ProxyKey, Img> proxies = new LinkedHashMap<>(16, 0.75f, true);
  private final Set<Integer> inUse = new HashSet<>();
  private long usedBytes;
  private long proxyBytes;
  private int nextId = 1;
  private boolean closed;

  private ArtifactStore(Path dir, long maxBytes, long maxProxyBytes) throws IOException {
    this.dir = dir;
    this.maxBytes = maxBytes;
    this.maxProxyBytes = maxProxyBytes;
    Files.createDirectories(dir);
    this.lockChannel =
        FileChannel.open(
            dir.resolve(LOCK),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.READ);
    this.lock = lockChannel.lock();
  }

  /** Directorio de caché de la app para este sistema, o {@code -Drora.ai.cache}. */
  public static Path defaultRoot() {
    String override = System.getProperty("rora.ai.cache");
    if (override != null && !override.isBlank()) {
      return Path.of(override);
    }
    String home = System.getProperty("user.home");
    String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    if (os.contains("win")) {
      String local = System.getenv("LOCALAPPDATA");
      Path base = local != null ? Path.of(local) : Path.of(home, "AppData", "Local");
      return base.resolve("RoraStudio").resolve("cache").resolve("ai");
    }
    if (os.contains("mac")) {
      return Path.of(home, "Library", "Caches", "RoraStudio", "ai");
    }
    return Path.of(home, ".cache", "rora-studio", "ai");
  }

  /** Abre una sesión nueva bajo {@code root}. */
  public static ArtifactStore open(Path root, long maxBytes, long maxProxyBytes)
      throws IOException {
    return new ArtifactStore(root.resolve(PREFIX + UUID.randomUUID()), maxBytes, maxProxyBytes);
  }

  /**
   * Borra las sesiones de procesos terminados. Solo actúa dentro de {@code root}.
   *
   * @return número de carpetas borradas
   */
  public static int sweepOrphans(Path root) {
    if (!Files.isDirectory(root)) {
      return 0;
    }
    int removed = 0;
    List<Path> sessions;
    try (Stream<Path> s = Files.list(root)) {
      sessions =
          s.filter(p -> p.getFileName().toString().startsWith(PREFIX))
              .filter(Files::isDirectory)
              .toList();
    } catch (IOException e) {
      return 0;
    }
    for (Path session : sessions) {
      if (isOrphan(session) && deleteTree(session)) {
        removed++;
      }
    }
    return removed;
  }

  private static boolean isOrphan(Path session) {
    Path file = session.resolve(LOCK);
    if (!Files.exists(file)) {
      return true;
    }
    try (FileChannel c = FileChannel.open(file, StandardOpenOption.WRITE)) {
      FileLock l = c.tryLock();
      if (l == null) {
        return false;
      }
      l.release();
      return true;
    } catch (OverlappingFileLockException e) {
      return false;
    } catch (IOException e) {
      return false;
    }
  }

  private static boolean deleteTree(Path root) {
    try (Stream<Path> walk = Files.walk(root)) {
      boolean ok = true;
      for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
        try {
          Files.deleteIfExists(p);
        } catch (IOException e) {
          ok = false;
        }
      }
      return ok;
    } catch (IOException e) {
      return false;
    }
  }

  /** Carpeta de esta sesión. */
  public Path directory() {
    return dir;
  }

  /** Guarda {@code image} y devuelve su identificador. Puede expulsar los menos usados. */
  public int put(Img image) throws IOException {
    int id;
    synchronized (this) {
      ensureOpen();
      id = nextId++;
    }
    Path file = dir.resolve("art-" + id + ".bin");
    Path tmp = dir.resolve("art-" + id + ".tmp");
    long bytes = HEADER + 4L * image.pixelCount();
    try {
      write(tmp, image);
      try {
        Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(tmp);
    }
    synchronized (this) {
      if (closed) {
        Files.deleteIfExists(file);
        throw new IOException("Almacén cerrado");
      }
      entries.put(id, new Entry(file, image.width(), image.height(), bytes));
      usedBytes += bytes;
      inUse.add(id);
      evict();
      inUse.remove(id);
    }
    return id;
  }

  private static void write(Path tmp, Img image) throws IOException {
    CRC32 crc = new CRC32();
    int w = image.width();
    int h = image.height();
    byte[] buffer = new byte[CHUNK * 4];
    int[] row = new int[w];
    try (OutputStream raw = Files.newOutputStream(tmp);
        BufferedOutputStream bos = new BufferedOutputStream(raw, CHUNK);
        DataOutputStream out = new DataOutputStream(bos)) {
      out.writeInt(MAGIC);
      out.writeInt(w);
      out.writeInt(h);
      for (int y = 0; y < h; y++) {
        image.copyRow(y, row, 0);
        crc.update(toBytes(row, w, buffer), 0, w * 4);
      }
      out.writeInt((int) crc.getValue());
      for (int y = 0; y < h; y++) {
        image.copyRow(y, row, 0);
        out.write(toBytes(row, w, buffer), 0, w * 4);
      }
    }
  }

  private static byte[] toBytes(int[] row, int w, byte[] buffer) {
    byte[] b = buffer.length >= w * 4 ? buffer : new byte[w * 4];
    for (int x = 0, i = 0; x < w; x++, i += 4) {
      int p = row[x];
      b[i] = (byte) (p >>> 24);
      b[i + 1] = (byte) (p >>> 16);
      b[i + 2] = (byte) (p >>> 8);
      b[i + 3] = (byte) p;
    }
    return b;
  }

  private void evict() {
    var it = entries.entrySet().iterator();
    while (usedBytes > maxBytes && it.hasNext()) {
      Map.Entry<Integer, Entry> e = it.next();
      if (inUse.contains(e.getKey())) {
        continue;
      }
      it.remove();
      forget(e.getKey(), e.getValue());
    }
  }

  private void forget(int id, Entry entry) {
    usedBytes -= entry.bytes();
    dropProxies(id);
    try {
      Files.deleteIfExists(entry.file());
    } catch (IOException e) {
      // Se borrará al cerrar la sesión.
    }
  }

  private void dropProxies(int id) {
    var it = proxies.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<ProxyKey, Img> e = it.next();
      if (e.getKey().id() == id) {
        proxyBytes -= 4L * e.getValue().pixelCount();
        it.remove();
      }
    }
  }

  /** Artefactos que no se pueden expulsar (los que usa la receta actual). */
  public synchronized void setInUse(Set<Integer> ids) {
    inUse.clear();
    inUse.addAll(ids);
    evict();
  }

  public synchronized boolean contains(int id) {
    return entries.containsKey(id);
  }

  public synchronized long usedBytes() {
    return usedBytes;
  }

  /** Quita un artefacto. */
  public synchronized void remove(int id) {
    Entry e = entries.remove(id);
    if (e != null) {
      forget(id, e);
    }
  }

  /** Quita todos los artefactos: al cerrar la imagen. */
  public synchronized void clear() {
    for (var e : List.copyOf(entries.entrySet())) {
      entries.remove(e.getKey());
      forget(e.getKey(), e.getValue());
    }
    proxies.clear();
    proxyBytes = 0;
    inUse.clear();
  }

  @Override
  public Optional<Img> full(int id) {
    Entry e;
    synchronized (this) {
      e = entries.get(id);
    }
    if (e == null) {
      return Optional.empty();
    }
    Optional<Img> read = read(e);
    if (read.isEmpty()) {
      remove(id);
    }
    return read;
  }

  @Override
  public Optional<Img> proxy(int id, int width, int height) {
    ProxyKey key = new ProxyKey(id, width, height);
    synchronized (this) {
      Img hit = proxies.get(key);
      if (hit != null) {
        return Optional.of(hit);
      }
    }
    Optional<Img> full = full(id);
    if (full.isEmpty()) {
      return Optional.empty();
    }
    Img proxy =
        Downscale.areaAverage(
            full.get(),
            width,
            height,
            TileExecutor.sequential(),
            CancellationToken.none(),
            Progress.NONE);
    synchronized (this) {
      if (entries.containsKey(id) && proxies.put(key, proxy) == null) {
        proxyBytes += 4L * proxy.pixelCount();
        var it = proxies.entrySet().iterator();
        while (proxyBytes > maxProxyBytes && it.hasNext()) {
          Map.Entry<ProxyKey, Img> e = it.next();
          if (e.getKey().equals(key)) {
            continue;
          }
          proxyBytes -= 4L * e.getValue().pixelCount();
          it.remove();
        }
      }
    }
    return Optional.of(proxy);
  }

  private static Optional<Img> read(Entry e) {
    try (InputStream raw = Files.newInputStream(e.file());
        BufferedInputStream bis = new BufferedInputStream(raw, CHUNK);
        DataInputStream in = new DataInputStream(bis)) {
      if (in.readInt() != MAGIC || in.readInt() != e.width() || in.readInt() != e.height()) {
        return Optional.empty();
      }
      int stored = in.readInt();
      int w = e.width();
      int h = e.height();
      int[] pixels = new int[Math.multiplyExact(w, h)];
      byte[] row = new byte[w * 4];
      CRC32 crc = new CRC32();
      for (int y = 0; y < h; y++) {
        in.readFully(row);
        crc.update(row);
        for (int x = 0, i = 0; x < w; x++, i += 4) {
          pixels[y * w + x] =
              (row[i] & 0xFF) << 24
                  | (row[i + 1] & 0xFF) << 16
                  | (row[i + 2] & 0xFF) << 8
                  | row[i + 3] & 0xFF;
        }
      }
      if ((int) crc.getValue() != stored) {
        return Optional.empty();
      }
      return Optional.of(Img.adopt(w, h, pixels));
    } catch (NoSuchFileException ex) {
      return Optional.empty();
    } catch (IOException ex) {
      return Optional.empty();
    }
  }

  private void ensureOpen() throws IOException {
    if (closed) {
      throw new IOException("Almacén cerrado");
    }
  }

  /** Borra la carpeta de la sesión y libera su bloqueo. Es idempotente. */
  @Override
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    entries.clear();
    proxies.clear();
    usedBytes = 0;
    proxyBytes = 0;
    try {
      lock.release();
      lockChannel.close();
    } catch (IOException e) {
      // La carpeta se limpiará como huérfana en el próximo arranque.
    }
    deleteTree(dir);
  }
}
