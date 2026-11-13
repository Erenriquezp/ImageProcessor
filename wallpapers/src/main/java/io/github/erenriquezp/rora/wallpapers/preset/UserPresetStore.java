package io.github.erenriquezp.rora.wallpapers.preset;

import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset.Origin;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Presets propios en un directorio de datos: todos los {@code *.json} de ese directorio (sin
 * subcarpetas ni enlaces), y la app escribe {@code user-presets.json}. Ninguna ruta se deriva del
 * contenido de los archivos.
 */
public final class UserPresetStore {

  /** Archivo que escribe la aplicación. */
  public static final String FILE = "user-presets.json";

  public static final long MAX_FILE_BYTES = 256 * 1024;
  public static final int MAX_PRESETS = 500;

  private final Path dir;

  public UserPresetStore(Path dir) {
    this.dir = Objects.requireNonNull(dir, "dir");
  }

  public Path directory() {
    return dir;
  }

  /** Resultado de la lectura: presets válidos y avisos. */
  public record Loaded(List<DevicePreset> presets, List<PresetProblem> problems) {
    public Loaded {
      presets = List.copyOf(presets);
      problems = List.copyOf(problems);
    }
  }

  /**
   * Directorio de datos del usuario para los presets: {@code %APPDATA%\RoraStudio\wallpapers} en
   * Windows, {@code ~/Library/Application Support/RoraStudio/wallpapers} en macOS y {@code
   * $XDG_DATA_HOME/rora-studio/wallpapers} (o {@code ~/.local/share/...}) en el resto.
   */
  public static Path defaultDirectory(Map<String, String> env, String osName, String userHome) {
    String os = osName.toLowerCase(Locale.ROOT);
    if (os.startsWith("windows")) {
      String appData = env.get("APPDATA");
      Path base =
          appData != null && !appData.isBlank()
              ? Path.of(appData)
              : Path.of(userHome, "AppData", "Roaming");
      return base.resolve("RoraStudio").resolve("wallpapers");
    }
    if (os.startsWith("mac")) {
      return Path.of(userHome, "Library", "Application Support", "RoraStudio", "wallpapers");
    }
    String xdg = env.get("XDG_DATA_HOME");
    Path base =
        xdg != null && !xdg.isBlank() && Path.of(xdg).isAbsolute()
            ? Path.of(xdg)
            : Path.of(userHome, ".local", "share");
    return base.resolve("rora-studio").resolve("wallpapers");
  }

  /** Lee los presets propios. Un directorio inexistente es una lista vacía. */
  public Loaded load() {
    List<DevicePreset> out = new ArrayList<>();
    List<PresetProblem> problems = new ArrayList<>();
    if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
      return new Loaded(out, problems);
    }
    List<Path> files = new ArrayList<>();
    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.json")) {
      for (Path p : ds) {
        files.add(p);
      }
    } catch (IOException e) {
      problems.add(new PresetProblem(dir.getFileName().toString(), "", "no se pudo leer: " + e));
      return new Loaded(out, problems);
    }
    files.sort(null);
    Set<String> ids = new HashSet<>();
    for (Path f : files) {
      String name = f.getFileName().toString();
      try {
        BasicFileAttributes a =
            Files.readAttributes(f, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!a.isRegularFile()) {
          problems.add(new PresetProblem(name, "", "no es un archivo normal; se ignora"));
          continue;
        }
        if (a.size() > MAX_FILE_BYTES) {
          problems.add(new PresetProblem(name, "", "mayor de 256 KB; se ignora"));
          continue;
        }
        String text = decode(Files.readAllBytes(f));
        PresetCodec.Parsed parsed = PresetCodec.read(text, name, Origin.USER);
        problems.addAll(parsed.problems());
        for (DevicePreset p : parsed.presets()) {
          if (out.size() >= MAX_PRESETS) {
            problems.add(new PresetProblem(name, p.id(), "más de " + MAX_PRESETS + " presets"));
          } else if (!ids.add(p.id())) {
            problems.add(new PresetProblem(name, p.id(), "id duplicado en otro archivo"));
          } else {
            out.add(p);
          }
        }
      } catch (PresetCodec.FileException e) {
        problems.add(new PresetProblem(name, "", e.getMessage()));
      } catch (CharacterCodingException e) {
        problems.add(new PresetProblem(name, "", "no está en UTF-8"));
      } catch (IOException e) {
        problems.add(new PresetProblem(name, "", "no se pudo leer: " + e.getMessage()));
      }
    }
    return new Loaded(out, problems);
  }

  private static String decode(byte[] bytes) throws CharacterCodingException {
    return StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(java.nio.ByteBuffer.wrap(bytes))
        .toString();
  }

  /**
   * Escribe los presets propios en {@link #FILE} de forma atómica (temporal en el mismo directorio
   * y movimiento).
   */
  public void save(List<DevicePreset> presets, LocalDate today) throws IOException {
    for (DevicePreset p : presets) {
      if (p.origin() != Origin.USER) {
        throw new IllegalArgumentException("solo se guardan presets propios: " + p.id());
      }
    }
    Files.createDirectories(dir);
    Path target = dir.resolve(FILE);
    Path tmp = Files.createTempFile(dir, ".presets-", ".tmp");
    try {
      Files.writeString(tmp, PresetCodec.write(presets, today), StandardCharsets.UTF_8);
      try {
        Files.move(
            tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      try {
        Files.deleteIfExists(tmp);
      } catch (NoSuchFileException ignored) {
        // ya movido
      }
    }
  }

  /**
   * Id nuevo para un preset propio a partir de su nombre: {@code user:} + nombre en minúsculas sin
   * tildes ni símbolos, con sufijo numérico si ya existe.
   */
  public static String newId(String name, Set<String> taken) {
    String slug = PresetCatalog.fold(name).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    if (slug.isEmpty()) {
      slug = "preset";
    }
    if (slug.length() > 40) {
      slug = slug.substring(0, 40);
    }
    String id = DevicePreset.USER_PREFIX + slug;
    for (int n = 2; taken.contains(id); n++) {
      id = DevicePreset.USER_PREFIX + slug + "-" + n;
    }
    return id;
  }
}
