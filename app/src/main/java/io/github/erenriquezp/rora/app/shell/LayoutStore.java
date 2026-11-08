package io.github.erenriquezp.rora.app.shell;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

/** Preferencias de disposición (tamaños, última carpeta…). */
public interface LayoutStore {

  double getDouble(String key, double fallback);

  void putDouble(String key, double value);

  String getString(String key, String fallback);

  void putString(String key, String value);

  /** Persistente, con {@code java.util.prefs}. */
  static LayoutStore preferences() {
    Preferences node = Preferences.userRoot().node("io/github/erenriquezp/rora/studio");
    return new LayoutStore() {
      @Override
      public double getDouble(String key, double fallback) {
        return node.getDouble(key, fallback);
      }

      @Override
      public void putDouble(String key, double value) {
        node.putDouble(key, value);
      }

      @Override
      public String getString(String key, String fallback) {
        return node.get(key, fallback);
      }

      @Override
      public void putString(String key, String value) {
        node.put(key, value);
      }
    };
  }

  /** En memoria, para tests y capturas. */
  static LayoutStore inMemory() {
    Map<String, Object> values = new HashMap<>();
    return new LayoutStore() {
      @Override
      public double getDouble(String key, double fallback) {
        return values.get(key) instanceof Double d ? d : fallback;
      }

      @Override
      public void putDouble(String key, double value) {
        values.put(key, value);
      }

      @Override
      public String getString(String key, String fallback) {
        return values.get(key) instanceof String s ? s : fallback;
      }

      @Override
      public void putString(String key, String value) {
        values.put(key, value);
      }
    };
  }
}
