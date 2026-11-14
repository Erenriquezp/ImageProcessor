package io.github.erenriquezp.rora.app.update;

import io.github.erenriquezp.rora.wallpapers.json.JsonException;
import java.util.Objects;
import java.util.Optional;

/**
 * Comprobación manual de actualizaciones: solo cuando el usuario la pide. Una petición, sin estado
 * guardado. La red está en {@link GitHubReleasesClient}; aquí no hay {@code java.net}.
 */
public final class UpdateChecker {

  /** De dónde salen las releases (GitHub en producción, un servidor local en tests). */
  public interface Source {
    /**
     * @return el cuerpo de una respuesta 200
     */
    String fetch() throws SourceException;
  }

  /** Fallo de la fuente, ya clasificado. */
  public static final class SourceException extends Exception {
    private final UpdateResult.Reason reason;

    public SourceException(UpdateResult.Reason reason, String detail) {
      super(detail);
      this.reason = reason;
    }

    public UpdateResult.Reason reason() {
      return reason;
    }
  }

  private final Source source;
  private final boolean enabled;

  public UpdateChecker(Source source, boolean enabled) {
    this.source = Objects.requireNonNull(source);
    this.enabled = enabled;
  }

  /** {@code -Drora.updates=off} desactiva la comprobación (ni siquiera aparece en el menú). */
  public static boolean enabledByProperty(String value) {
    return value == null || !value.strip().equalsIgnoreCase("off");
  }

  public boolean enabled() {
    return enabled;
  }

  /** Bloquea hasta la respuesta o el tiempo de espera: siempre fuera del hilo de la interfaz. */
  public UpdateResult check(String currentVersion) {
    if (!enabled) {
      return new UpdateResult.Failed(UpdateResult.Reason.DISABLED);
    }
    Optional<VersionTag> current = VersionTag.parseVersion(currentVersion);
    VersionTag cur = current.orElse(new VersionTag(0, 0, 0));
    try {
      return ReleaseFeed.evaluate(source.fetch(), cur);
    } catch (SourceException e) {
      return new UpdateResult.Failed(e.reason());
    } catch (JsonException | RuntimeException e) {
      return new UpdateResult.Failed(UpdateResult.Reason.INVALID_RESPONSE);
    }
  }
}
