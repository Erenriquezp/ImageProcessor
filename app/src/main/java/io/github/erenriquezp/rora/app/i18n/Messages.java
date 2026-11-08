package io.github.erenriquezp.rora.app.i18n;

import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

/** Textos de la interfaz, de {@code labels_<idioma>.properties}. El idioma se inyecta. */
public final class Messages {

  private static final String BASE = "io.github.erenriquezp.rora.app.i18n.labels";
  private static final Locale FALLBACK = Locale.of("es");
  private static final Set<String> AVAILABLE = Set.of("es");

  private final ResourceBundle bundle;
  private final List<ResourceBundle> extras;
  private final Locale locale;

  private Messages(ResourceBundle bundle, List<ResourceBundle> extras, Locale locale) {
    this.bundle = bundle;
    this.extras = List.copyOf(extras);
    this.locale = locale;
  }

  /** Mensajes para {@code locale}, o en español si ese idioma no está disponible. */
  public static Messages of(Locale locale) {
    return of(locale, List.of());
  }

  /** Con textos adicionales, p. ej. los del Laboratorio. */
  public static Messages of(Locale locale, List<ResourceBundle> extras) {
    Objects.requireNonNull(locale, "locale");
    Locale chosen = AVAILABLE.contains(locale.getLanguage()) ? locale : FALLBACK;
    return new Messages(ResourceBundle.getBundle(BASE, chosen), extras, chosen);
  }

  public Locale locale() {
    return locale;
  }

  public boolean has(String key) {
    return bundle.containsKey(key);
  }

  private ResourceBundle owner(String key) {
    for (ResourceBundle extra : extras) {
      if (extra.containsKey(key)) {
        return extra;
      }
    }
    return bundle;
  }

  /** Texto de {@code key}; lanza si falta (lo cubre {@code I18nCompletenessTest}). */
  public String get(String key) {
    return owner(key).getString(key);
  }

  /** Texto de {@code key} con los argumentos de {@link MessageFormat}. */
  public String format(String key, Object... args) {
    return new MessageFormat(get(key), locale).format(args);
  }

  public Set<String> keys() {
    Set<String> keys = new TreeSet<>();
    for (Enumeration<String> e = bundle.getKeys(); e.hasMoreElements(); ) {
      keys.add(e.nextElement());
    }
    return keys;
  }
}
