package io.github.erenriquezp.rora.app.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.wallpapers.json.JsonException;
import org.junit.jupiter.api.Test;

class ReleaseFeedTest {

  static String release(String tag, boolean draft, boolean pre, String url, String date) {
    return "{\"tag_name\":\""
        + tag
        + "\",\"draft\":"
        + draft
        + ",\"prerelease\":"
        + pre
        + ",\"html_url\":\""
        + url
        + "\",\"published_at\":\""
        + date
        + "\",\"author\":{\"login\":\"x\"},\"assets\":[{\"name\":\"a.msi\"}]}";
  }

  static final String PAGE = ReleaseFeed.RELEASES_PAGE + "/tag/";

  private static final VersionTag V010 = new VersionTag(0, 1, 0);

  @Test
  void picksTheNewestPublishedTagAndIgnoresDraftsAndOddTags() throws Exception {
    String json =
        "["
            + release(
                "v0.1.0-academic", false, false, PAGE + "v0.1.0-academic", "2025-01-01T00:00:00Z")
            + ","
            + release("v0.3.0", true, false, PAGE + "v0.3.0", "2027-03-01T00:00:00Z")
            + ","
            + release("v0.2.1", false, true, PAGE + "v0.2.1", "2027-02-01T10:00:00Z")
            + ","
            + release("v0.2.0", false, false, PAGE + "v0.2.0", "2027-01-10T10:00:00Z")
            + ","
            + release("nightly", false, false, PAGE + "nightly", "2027-04-01T00:00:00Z")
            + "]";
    UpdateResult r = ReleaseFeed.evaluate(json, V010);
    assertThat(r)
        .isEqualTo(
            new UpdateResult.NewVersion(
                new VersionTag(0, 2, 1), PAGE + "v0.2.1", "2027-02-01", false));
  }

  @Test
  void sameOrOlderIsUpToDate() throws Exception {
    String json =
        "[" + release("v0.1.0", false, false, PAGE + "v0.1.0", "2026-12-01T00:00:00Z") + "]";
    assertThat(ReleaseFeed.evaluate(json, V010)).isEqualTo(new UpdateResult.UpToDate(V010));
    assertThat(ReleaseFeed.evaluate("[]", V010)).isEqualTo(new UpdateResult.UpToDate(V010));
    assertThat(ReleaseFeed.evaluate(json, new VersionTag(0, 2, 0)))
        .isEqualTo(new UpdateResult.UpToDate(new VersionTag(0, 2, 0)));
  }

  @Test
  void aNewMajorIsFlagged() throws Exception {
    String json =
        "[" + release("v1.0.0", false, false, PAGE + "v1.0.0", "2027-06-01T00:00:00Z") + "]";
    assertThat(ReleaseFeed.evaluate(json, V010))
        .isEqualTo(
            new UpdateResult.NewVersion(
                new VersionTag(1, 0, 0), PAGE + "v1.0.0", "2027-06-01", true));
  }

  @Test
  void foreignOrOddPagesFallBackToTheProjectReleases() {
    assertThat(ReleaseFeed.safePage("https://evil.example/releases/x"))
        .isEqualTo(ReleaseFeed.RELEASES_PAGE);
    assertThat(ReleaseFeed.safePage(PAGE + "../../../other")).isEqualTo(ReleaseFeed.RELEASES_PAGE);
    assertThat(ReleaseFeed.safePage(PAGE + "v1 <script>")).isEqualTo(ReleaseFeed.RELEASES_PAGE);
    assertThat(ReleaseFeed.safePage(42)).isEqualTo(ReleaseFeed.RELEASES_PAGE);
    assertThat(ReleaseFeed.safePage(PAGE + "v0.2.0")).isEqualTo(PAGE + "v0.2.0");
  }

  @Test
  void invalidDocuments() {
    assertThatThrownBy(() -> ReleaseFeed.evaluate("{\"message\":\"Not Found\"}", V010))
        .isInstanceOf(JsonException.class);
    assertThatThrownBy(() -> ReleaseFeed.evaluate("[1,2]", V010)).isInstanceOf(JsonException.class);
    assertThatThrownBy(() -> ReleaseFeed.evaluate("<html>", V010))
        .isInstanceOf(JsonException.class);
  }

  @Test
  void versionTags() {
    assertThat(VersionTag.parseTag("v0.1.0")).contains(V010);
    assertThat(VersionTag.parseTag("0.1.0")).isEmpty();
    assertThat(VersionTag.parseTag("v0.1.0-academic")).isEmpty();
    assertThat(VersionTag.parseTag("v0.1")).isEmpty();
    assertThat(VersionTag.parseVersion("0.1.1-SNAPSHOT")).contains(new VersionTag(0, 1, 1));
    assertThat(VersionTag.parseVersion("desarrollo")).isEmpty();
    assertThat(new VersionTag(0, 10, 0)).isGreaterThan(new VersionTag(0, 9, 9));
    assertThat(new VersionTag(1, 0, 0).toString()).isEqualTo("1.0.0");
  }
}
