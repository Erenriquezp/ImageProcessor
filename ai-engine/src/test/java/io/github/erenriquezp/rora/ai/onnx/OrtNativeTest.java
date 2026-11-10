package io.github.erenriquezp.rora.ai.onnx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure.Reason;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrtNativeTest {

  private static final String PLATFORM = "linux-x64";

  @TempDir Path dir;

  private static String sha(String text) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
  }

  private void write(String name, String content) throws Exception {
    Files.writeString(dir.resolve(name), content);
  }

  private static void assertUnavailable(Path d, Map<String, String> table) {
    assertThatThrownBy(() -> OrtNative.verify(d, PLATFORM, table))
        .isInstanceOfSatisfying(
            AiFailure.class, e -> assertThat(e.reason()).isEqualTo(Reason.ENGINE_UNAVAILABLE));
  }

  @ParameterizedTest
  @CsvSource({
    "Windows 11,amd64,win-x64",
    "Linux,amd64,linux-x64",
    "Linux,aarch64,linux-aarch64",
    "Mac OS X,aarch64,osx-aarch64"
  })
  void recognisesThePlatformsWithNatives(String os, String arch, String expected) throws Exception {
    assertThat(OrtNative.platform(os, arch)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"Mac OS X,x86_64", "Windows 11,x86", "FreeBSD,amd64"})
  void failsClosedOnAnUnsupportedPlatform(String os, String arch) {
    assertThatThrownBy(() -> OrtNative.platform(os, arch)).isInstanceOf(AiFailure.class);
  }

  @Test
  void aMissingDirectoryFailsClosed() {
    assertUnavailable(dir.resolve("nope"), Map.of());
  }

  @Test
  void anIncompleteDirectoryFailsClosed() throws Exception {
    write("libonnxruntime.so", "a");
    assertUnavailable(dir, Map.of("linux-x64/libonnxruntime.so", sha("a")));
  }

  @Test
  void aLibraryWithTheWrongHashFailsClosed() throws Exception {
    write("libonnxruntime.so", "a");
    write("libonnxruntime4j_jni.so", "b");
    assertUnavailable(
        dir,
        Map.of(
            "linux-x64/libonnxruntime.so", sha("a"),
            "linux-x64/libonnxruntime4j_jni.so", sha("otra")));
  }

  @Test
  void aLibraryAbsentFromTheTableFailsClosed() throws Exception {
    write("libonnxruntime.so", "a");
    write("libonnxruntime4j_jni.so", "b");
    assertUnavailable(dir, Map.of("linux-x64/libonnxruntime.so", sha("a")));
  }

  @Test
  void aValidDirectoryPasses() throws Exception {
    write("libonnxruntime.so", "a");
    write("libonnxruntime4j_jni.so", "b");
    OrtNative.verify(
        dir,
        PLATFORM,
        Map.of(
            "linux-x64/libonnxruntime.so", sha("a"),
            "linux-x64/libonnxruntime4j_jni.so", sha("b")));
  }

  @Test
  void withoutThePropertyTheImageDirectoryIsUsed() {
    assertThat(OrtNative.resolve(null, "home")).isEqualTo(Path.of("home", "ort-native"));
    assertThat(OrtNative.resolve(" ", "home")).isEqualTo(Path.of("home", "ort-native"));
    assertThat(OrtNative.resolve("x", "home")).isEqualTo(Path.of("x"));
  }

  @Test
  void theRealNativesOfThisBuildMatchTheTable() throws Exception {
    OrtNative.prepare();
  }
}
