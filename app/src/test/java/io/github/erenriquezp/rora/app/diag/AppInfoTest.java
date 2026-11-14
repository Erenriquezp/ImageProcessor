package io.github.erenriquezp.rora.app.diag;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AppInfoTest {

  private static AppInfo parse(String text) throws IOException {
    return AppInfo.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void theFilteredResourceCarriesThePomVersion() {
    AppInfo info = AppInfo.current();
    assertThat(info.version()).matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?");
    assertThat(info.version()).doesNotContain("${");
    assertThat(info.channel()).isEqualTo("beta");
  }

  @Test
  void releaseVersionWithChannel() throws IOException {
    AppInfo info = parse("version=0.1.0\nchannel=beta\n");
    assertThat(info.displayVersion()).isEqualTo("0.1.0 (beta)");
    assertThat(info.numericVersion()).isEqualTo("0.1.0");
    assertThat(info.isDevelopment()).isFalse();
    assertThat(info.userAgent()).isEqualTo("RoraStudio/0.1.0");
  }

  @Test
  void snapshotIsDevelopmentAndKeepsItsNumbers() throws IOException {
    AppInfo info = parse("version=0.1.1-SNAPSHOT\nchannel=\n");
    assertThat(info.isDevelopment()).isTrue();
    assertThat(info.numericVersion()).isEqualTo("0.1.1");
    assertThat(info.displayVersion()).isEqualTo("0.1.1-SNAPSHOT");
    assertThat(info.userAgent()).isEqualTo("RoraStudio/0.1.1");
  }

  @Test
  void unfilteredOrMissingValuesFallBackToDevelopment() throws IOException {
    AppInfo info = parse("version=${project.version}\nchannel=${rora.channel}\n");
    assertThat(info.version()).isEqualTo(AppInfo.DEVELOPMENT);
    assertThat(info.channel()).isEmpty();
    assertThat(info.isDevelopment()).isTrue();
    assertThat(info.numericVersion()).isEmpty();
    assertThat(info.userAgent()).isEqualTo("RoraStudio/0");
    assertThat(parse("").version()).isEqualTo(AppInfo.DEVELOPMENT);
  }
}
