package io.github.erenriquezp.rora.app.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * La única clase de Rora Studio que usa la red (ArchUnit lo comprueba). Pide la lista de releases a
 * la API pública de GitHub con un User-Agent genérico con la versión, sin autenticación, cookies ni
 * identificadores. No descarga ni ejecuta nada: devuelve el texto y cierra.
 */
public final class GitHubReleasesClient implements UpdateChecker.Source {

  /** Lista y no {@code /latest}: esta última excluye las versiones marcadas como prerelease. */
  public static final String ENDPOINT =
      "https://api.github.com/repos/Erenriquezp/rora-studio/releases?per_page=10";

  public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  public static final Duration TIMEOUT = Duration.ofSeconds(10);
  public static final int MAX_BYTES = 256 * 1024;

  private final URI endpoint;
  private final String userAgent;
  private final Duration connectTimeout;
  private final Duration timeout;

  /** Producción. */
  public GitHubReleasesClient(String userAgent) {
    this(ENDPOINT, userAgent, CONNECT_TIMEOUT, TIMEOUT);
  }

  /** Solo HTTPS; HTTP únicamente contra la propia máquina (servidor de pruebas). */
  GitHubReleasesClient(
      String endpoint, String userAgent, Duration connectTimeout, Duration timeout) {
    URI u = URI.create(endpoint);
    String scheme = Objects.requireNonNullElse(u.getScheme(), "").toLowerCase(Locale.ROOT);
    boolean loopback = "127.0.0.1".equals(u.getHost()) || "localhost".equals(u.getHost());
    if (!scheme.equals("https") && !(scheme.equals("http") && loopback)) {
      throw new IllegalArgumentException("Solo HTTPS");
    }
    this.endpoint = u;
    this.userAgent = Objects.requireNonNull(userAgent);
    this.connectTimeout = connectTimeout;
    this.timeout = timeout;
  }

  @Override
  public String fetch() throws UpdateChecker.SourceException {
    HttpRequest request =
        HttpRequest.newBuilder(endpoint)
            .GET()
            .timeout(timeout)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", userAgent)
            .build();
    try (HttpClient client =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()) {
      HttpResponse<InputStream> r = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (InputStream in = r.body()) {
        int status = r.statusCode();
        if ((status == 403 || status == 429)
            && r.headers()
                .firstValue("X-RateLimit-Remaining")
                .map("0"::equals)
                .orElse(status == 429)) {
          throw new UpdateChecker.SourceException(
              UpdateResult.Reason.RATE_LIMITED, "HTTP " + status);
        }
        if (status != 200) {
          throw new UpdateChecker.SourceException(
              UpdateResult.Reason.INVALID_RESPONSE, "HTTP " + status);
        }
        byte[] body = in.readNBytes(MAX_BYTES + 1);
        if (body.length > MAX_BYTES) {
          throw new UpdateChecker.SourceException(
              UpdateResult.Reason.INVALID_RESPONSE, "respuesta demasiado grande");
        }
        return new String(body, StandardCharsets.UTF_8);
      }
    } catch (HttpConnectTimeoutException | ConnectException | UnknownHostException e) {
      throw new UpdateChecker.SourceException(UpdateResult.Reason.NO_NETWORK, e.toString());
    } catch (HttpTimeoutException e) {
      throw new UpdateChecker.SourceException(UpdateResult.Reason.TIMEOUT, e.toString());
    } catch (IOException e) {
      throw new UpdateChecker.SourceException(UpdateResult.Reason.NO_NETWORK, e.toString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new UpdateChecker.SourceException(UpdateResult.Reason.TIMEOUT, "interrumpida");
    }
  }
}
