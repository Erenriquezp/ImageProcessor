package io.github.erenriquezp.rora.app.viewmodel;

/** Dónde se recuerdan los ajustes de exportación. */
public interface ExportPrefs {

  ExportPrefs NONE =
      new ExportPrefs() {
        @Override
        public String get(String key, String fallback) {
          return fallback;
        }

        @Override
        public void put(String key, String value) {}
      };

  String get(String key, String fallback);

  void put(String key, String value);
}
