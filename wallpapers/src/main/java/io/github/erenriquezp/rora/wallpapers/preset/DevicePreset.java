package io.github.erenriquezp.rora.wallpapers.preset;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Clase de dispositivo con su resolución exacta.
 *
 * @param id identificador estable; los propios empiezan por {@code user:}
 * @param parallaxMargin margen de desplazamiento por lado, fracción en {@code [0, 0,5)}; 0 =
 *     ninguno
 * @param reviewed fecha de la última comprobación de la fuente, si la hay
 */
public record DevicePreset(
    String id,
    String name,
    Group group,
    int width,
    int height,
    Orientation orientation,
    Status status,
    String source,
    Optional<LocalDate> reviewed,
    List<SafeZone> safeZones,
    double parallaxMargin,
    Origin origin) {

  /** Lado mínimo y máximo de un destino, en píxeles. */
  public static final int MIN_SIDE = 16;

  public static final int MAX_SIDE = 16384;

  /** Prefijo de los presets propios. */
  public static final String USER_PREFIX = "user:";

  /** Grupo de la lista, con su identificador en el JSON. */
  public enum Group {
    SCREEN("screen"),
    PC_16_9("pc-16-9"),
    PC_16_10("pc-16-10"),
    ULTRAWIDE("ultrawide"),
    LAPTOP_3_2("laptop-3-2"),
    PHONE("phone"),
    TABLET("tablet"),
    CUSTOM("custom");

    private final String json;

    Group(String json) {
      this.json = json;
    }

    public String json() {
      return json;
    }

    public static Optional<Group> fromJson(String s) {
      for (Group g : values()) {
        if (g.json.equals(s)) {
          return Optional.of(g);
        }
      }
      return Optional.empty();
    }
  }

  /** Orientación nativa del preset. */
  public enum Orientation {
    LANDSCAPE("landscape"),
    PORTRAIT("portrait"),
    SQUARE("square");

    private final String json;

    Orientation(String json) {
      this.json = json;
    }

    public String json() {
      return json;
    }

    public static Optional<Orientation> fromJson(String s) {
      for (Orientation o : values()) {
        if (o.json.equals(s)) {
          return Optional.of(o);
        }
      }
      return Optional.empty();
    }

    /** Orientación que corresponde a unas dimensiones. */
    public static Orientation of(int width, int height) {
      return width > height ? LANDSCAPE : width < height ? PORTRAIT : SQUARE;
    }
  }

  /** Fiabilidad de la resolución. */
  public enum Status {
    VERIFIED("verified"),
    STANDARD("standard"),
    APPROXIMATE("approximate"),
    USER("user");

    private final String json;

    Status(String json) {
      this.json = json;
    }

    public String json() {
      return json;
    }

    public static Optional<Status> fromJson(String s) {
      for (Status v : values()) {
        if (v.json.equals(s)) {
          return Optional.of(v);
        }
      }
      return Optional.empty();
    }
  }

  /** De dónde viene el preset. */
  public enum Origin {
    BUILT_IN,
    USER,
    SCREEN
  }

  public DevicePreset {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(group, "group");
    Objects.requireNonNull(orientation, "orientation");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(reviewed, "reviewed");
    Objects.requireNonNull(origin, "origin");
    safeZones = List.copyOf(safeZones);
    check(!id.isBlank() && id.length() <= 64, "id vacío o de más de 64 caracteres");
    check(!name.isBlank() && name.length() <= 80, "nombre vacío o de más de 80 caracteres");
    check(
        width >= MIN_SIDE && width <= MAX_SIDE && height >= MIN_SIDE && height <= MAX_SIDE,
        "dimensiones fuera de " + MIN_SIDE + ".." + MAX_SIDE);
    check(
        orientation == Orientation.of(width, height),
        "la orientación no coincide con las dimensiones");
    check(
        parallaxMargin >= 0 && parallaxMargin < 0.5, "margen de desplazamiento fuera de [0, 0,5)");
    check(
        (origin == Origin.USER) == id.startsWith(USER_PREFIX),
        "solo los presets propios usan el prefijo " + USER_PREFIX);
  }

  private static void check(boolean ok, String detail) {
    if (!ok) {
      throw new IllegalArgumentException(detail);
    }
  }

  /** Ancho / alto. */
  public double aspect() {
    return (double) width / height;
  }
}
