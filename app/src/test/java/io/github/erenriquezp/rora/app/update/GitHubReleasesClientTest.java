package io.github.erenriquezp.rora.app.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Servidor HTTP del JDK en 127.0.0.1: respuestas reales, sin salir a Internet. */
class GitHubReleasesClientTest {

  private static final String UA = "RoraStudio/0.1.0";
  private static final String PAGE = ReleaseFeed.RELEASES_PAGE + "/tag/";

  private HttpServer server;
  private final AtomicInteger requests = new AtomicInteger();
  private final List<Headers> seen = new CopyOnWriteArrayList<>();
  private final Map<String, Consumer<HttpExchange>> routes = new ConcurrentHashMap<>();
  private final CountDownLatch release = new CountDownLatch(1);

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        ex -> {
          requests.incrementAndGet();
          seen.add(ex.getRequestHeaders());
          Consumer<HttpExchange> h = routes.get(ex.getRequestURI().getPath());
          if (h == null) {
            send(ex, 404, "{\"message\":\"Not Found\"}");
          } else {
            h.accept(ex);
          }
        });
    server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
    server.start();
  }

  @AfterEach
  void stop() {
    release.countDown();
    server.stop(0);
  }

  private String url(String path) {
    return "http://127.0.0.1:" + server.getAddress().getPort() + path;
  }

  private static void send(HttpExchange ex, int status, String body) {
    send(ex, status, body.getBytes(StandardCharsets.UTF_8));
  }

  private static void send(HttpExchange ex, int status, byte[] body) {
    try {
      ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
      try (OutputStream o = ex.getResponseBody()) {
        o.write(body);
      }
    } catch (IOException e) {
      // el cliente cortó
    }
  }

  private UpdateResult check(String path, String current, Duration timeout) {
    var client = new GitHubReleasesClient(url(path), UA, Duration.ofSeconds(2), timeout);
    return new UpdateChecker(client, true).check(current);
  }

  private UpdateResult check(String path) {
    return check(path, "0.1.0", Duration.ofSeconds(5));
  }

  private static String feed(String... tags) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < tags.length; i++) {
      sb.append(i == 0 ? "" : ",")
          .append(
              ReleaseFeedTest.release(
                  tags[i], false, false, PAGE + tags[i], "2027-01-10T10:00:00Z"));
    }
    return sb.append(']').toString();
  }

  @Test
  void aNewerReleaseWithTheExactRequestHeaders() {
    routes.put("/rel", ex -> send(ex, 200, feed("v0.2.0", "v0.1.0")));
    assertThat(check("/rel"))
        .isEqualTo(
            new UpdateResult.NewVersion(
                new VersionTag(0, 2, 0), PAGE + "v0.2.0", "2027-01-10", false));
    assertThat(requests).hasValue(1);
    Headers h = seen.getFirst();
    assertThat(h.getFirst("User-Agent")).isEqualTo(UA);
    assertThat(h.getFirst("Accept")).isEqualTo("application/vnd.github+json");
    assertThat(h.getFirst("X-GitHub-Api-Version")).isEqualTo("2022-11-28");
    assertThat(h.keySet().stream().map(k -> k.toLowerCase(java.util.Locale.ROOT)))
        .doesNotContain("cookie", "authorization")
        .allMatch(
            k ->
                List.of(
                        "host",
                        "user-agent",
                        "accept",
                        "x-github-api-version",
                        "connection",
                        "content-length")
                    .contains(k));
  }

  @Test
  void sameVersionAndNewMajor() {
    routes.put("/same", ex -> send(ex, 200, feed("v0.1.0")));
    routes.put("/major", ex -> send(ex, 200, feed("v1.0.0", "v0.1.0")));
    assertThat(check("/same")).isEqualTo(new UpdateResult.UpToDate(new VersionTag(0, 1, 0)));
    assertThat(check("/major"))
        .isInstanceOfSatisfying(
            UpdateResult.NewVersion.class, n -> assertThat(n.majorChange()).isTrue());
    assertThat(check("/same", "0.1.0-SNAPSHOT", Duration.ofSeconds(5)))
        .isEqualTo(new UpdateResult.UpToDate(new VersionTag(0, 1, 0)));
  }

  @Test
  void invalidOversizedAndHttpErrors() {
    routes.put("/bad", ex -> send(ex, 200, "<html>no es JSON</html>"));
    routes.put("/huge", ex -> send(ex, 200, new byte[GitHubReleasesClient.MAX_BYTES + 10]));
    routes.put("/500", ex -> send(ex, 500, "{}"));
    assertThat(check("/bad"))
        .isEqualTo(new UpdateResult.Failed(UpdateResult.Reason.INVALID_RESPONSE));
    assertThat(check("/huge"))
        .isEqualTo(new UpdateResult.Failed(UpdateResult.Reason.INVALID_RESPONSE));
    assertThat(check("/500"))
        .isEqualTo(new UpdateResult.Failed(UpdateResult.Reason.INVALID_RESPONSE));
    assertThat(check("/no-existe"))
        .isEqualTo(new UpdateResult.Failed(UpdateResult.Reason.INVALID_RESPONSE));
  }

  @Test
  void rateLimited() {
    routes.put(
        "/limit",
        ex -> {
          ex.getResponseHeaders().add("X-RateLimit-Remaining", "0");
          send(ex, 403, "{\"message\":\"API rate limit exceeded\"}");
        });
    assertThat(check("/limit"))
        .isEqualTo(new UpdateResult.Failed(UpdateResult.Reason.RATE_LIMITED));
  }

  @Test
  void timeout() {
    routes.put(
        "/slow",
        ex -> {
          try {
            release.await(); // no responde hasta que el test termina
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          send(ex, 200, "[]");
        });
    assertThat(check("/slow", "0.1.0", Duration.ofMillis(300)))
        .isEqualTo(new UpdateResult.Failed(UpdateResult.Reason.TIMEOUT));
  }

  @Test
  void followsARedirectWithinTheSameServer() {
    routes.put(
        "/old",
        ex -> {
          ex.getResponseHeaders().add("Location", "/rel");
          send(ex, 301, "");
        });
    routes.put("/rel", ex -> send(ex, 200, feed("v0.2.0")));
    assertThat(check("/old")).isInstanceOf(UpdateResult.NewVersion.class);
    assertThat(requests).hasValue(2);
  }

  @Test
  void noNetwork() throws Exception {
    int port = server.getAddress().getPort();
    server.stop(0);
    var client =
        new GitHubReleasesClient(
            "http://127.0.0.1:" + port + "/rel", UA, Duration.ofSeconds(2), Duration.ofSeconds(2));
    assertThat(new UpdateChecker(client, true).check("0.1.0"))
        .isEqualTo(new UpdateResult.Failed(UpdateResult.Reason.NO_NETWORK));
  }

  @Test
  void disabledMakesNoRequestAtAll() {
    routes.put("/rel", ex -> send(ex, 200, feed("v0.2.0")));
    var client =
        new GitHubReleasesClient(url("/rel"), UA, Duration.ofSeconds(2), Duration.ofSeconds(2));
    UpdateChecker off = new UpdateChecker(client, UpdateChecker.enabledByProperty("off"));
    assertThat(off.enabled()).isFalse();
    assertThat(off.check("0.1.0")).isEqualTo(new UpdateResult.Failed(UpdateResult.Reason.DISABLED));
    assertThat(requests).hasValue(0);
    assertThat(UpdateChecker.enabledByProperty(null)).isTrue();
    assertThat(UpdateChecker.enabledByProperty("OFF ")).isFalse();
  }

  @Test
  void onlyHttpsOutsideTheLoopback() {
    assertThatThrownBy(
            () ->
                new GitHubReleasesClient(
                    "http://api.github.com/x", UA, Duration.ofSeconds(1), Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(GitHubReleasesClient.ENDPOINT)
        .startsWith("https://api.github.com/repos/Erenriquezp/ImageProcessor/releases");
  }
}
