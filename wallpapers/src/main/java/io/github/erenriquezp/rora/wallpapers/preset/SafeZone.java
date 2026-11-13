package io.github.erenriquezp.rora.wallpapers.preset;

import java.util.Objects;
import java.util.Optional;

/**
 * Zona que el sistema suele tapar (reloj, dock...), normalizada a {@code [0, 1]} en la orientación
 * del preset. Siempre orientativa.
 */
public record SafeZone(Kind kind, double x, double y, double w, double h) {

  private static final double EPS = 1e-9;

  /** Qué tapa la zona. */
  public enum Kind {
    CLOCK("clock"),
    DOCK("dock"),
    MENU_BAR("menu-bar"),
    NOTCH("notch"),
    WIDGETS("widgets");

    private final String json;

    Kind(String json) {
      this.json = json;
    }

    public String json() {
      return json;
    }

    public static Optional<Kind> fromJson(String s) {
      for (Kind k : values()) {
        if (k.json.equals(s)) {
          return Optional.of(k);
        }
      }
      return Optional.empty();
    }
  }

  public SafeZone {
    Objects.requireNonNull(kind, "kind");
    if (!(x >= 0 && y >= 0 && w > 0 && h > 0 && x + w <= 1 + EPS && y + h <= 1 + EPS)) {
      throw new IllegalArgumentException(
          "zona fuera de [0, 1]: " + x + ", " + y + ", " + w + ", " + h);
    }
  }
}
