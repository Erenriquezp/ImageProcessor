package io.github.erenriquezp.rora.ai.store;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.image.Img;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactStoreTest {

  @TempDir Path root;

  private static Img image(int w, int h, int seed) {
    int[] p = new int[w * h];
    for (int i = 0; i < p.length; i++) {
      p[i] = 0xFF000000 | (i * 2654435 + seed);
    }
    return Img.adopt(w, h, p);
  }

  private ArtifactStore store(long maxBytes) throws IOException {
    return ArtifactStore.open(root, maxBytes, 1 << 20);
  }

  private static long files(Path dir, String suffix) throws IOException {
    try (Stream<Path> s = Files.list(dir)) {
      return s.filter(p -> p.toString().endsWith(suffix)).count();
    }
  }

  @Test
  void aStoredImageComesBackIdentical() throws Exception {
    try (ArtifactStore s = store(1 << 24)) {
      Img in = image(37, 19, 1);
      int id = s.put(in);
      assertThat(s.contains(id)).isTrue();
      assertThat(s.full(id).orElseThrow().sameContent(in)).isTrue();
      assertThat(files(s.directory(), ".tmp")).isZero();
    }
  }

  @Test
  void identifiersAreNeverZeroNorReused() throws Exception {
    try (ArtifactStore s = store(1 << 24)) {
      int a = s.put(image(2, 2, 1));
      s.remove(a);
      int b = s.put(image(2, 2, 2));
      assertThat(a).isPositive();
      assertThat(b).isNotEqualTo(a);
    }
  }

  @Test
  void aDamagedFileIsTreatedAsMissing() throws Exception {
    try (ArtifactStore s = store(1 << 24)) {
      int id = s.put(image(16, 16, 3));
      Path file = s.directory().resolve("art-" + id + ".bin");
      byte[] data = Files.readAllBytes(file);
      data[data.length - 1] ^= 0x55;
      Files.write(file, data);
      assertThat(s.full(id)).isEmpty();
      assertThat(s.contains(id)).isFalse();
    }
  }

  @Test
  void aDeletedFileIsTreatedAsMissing() throws Exception {
    try (ArtifactStore s = store(1 << 24)) {
      int id = s.put(image(4, 4, 3));
      Files.delete(s.directory().resolve("art-" + id + ".bin"));
      assertThat(s.full(id)).isEmpty();
    }
  }

  @Test
  void theLeastRecentlyUsedIsEvictedOverTheLimit() throws Exception {
    long one = 16 + 4 * 100 * 100;
    try (ArtifactStore s = store(2 * one)) {
      int a = s.put(image(100, 100, 1));
      int b = s.put(image(100, 100, 2));
      s.full(a);
      int c = s.put(image(100, 100, 3));
      assertThat(s.contains(a)).isTrue();
      assertThat(s.contains(b)).isFalse();
      assertThat(s.contains(c)).isTrue();
      assertThat(s.usedBytes()).isEqualTo(2 * one);
    }
  }

  @Test
  void anArtifactInUseIsNeverEvicted() throws Exception {
    long one = 16 + 4 * 100 * 100;
    try (ArtifactStore s = store(one)) {
      int a = s.put(image(100, 100, 1));
      s.setInUse(Set.of(a));
      int b = s.put(image(100, 100, 2));
      assertThat(s.contains(a)).isTrue();
      // El recién calculado también se conserva: el tope cede antes que un resultado útil.
      assertThat(s.contains(b)).isTrue();
      s.setInUse(Set.of(b));
      assertThat(s.contains(a)).isFalse();
    }
  }

  @Test
  void aNewArtifactLargerThanTheLimitIsStillKept() throws Exception {
    try (ArtifactStore s = store(10)) {
      int a = s.put(image(10, 10, 1));
      assertThat(s.contains(a)).isTrue();
    }
  }

  @Test
  void proxiesAreCachedAndAreOfTheRequestedSize() throws Exception {
    try (ArtifactStore s = store(1 << 24)) {
      int id = s.put(image(64, 64, 1));
      Img p = s.proxy(id, 16, 16).orElseThrow();
      assertThat(p.width()).isEqualTo(16);
      assertThat(s.proxy(id, 16, 16).orElseThrow()).isSameAs(p);
    }
  }

  @Test
  void clearingRemovesEveryFileButKeepsTheSession() throws Exception {
    try (ArtifactStore s = store(1 << 24)) {
      int id = s.put(image(8, 8, 1));
      s.proxy(id, 2, 2);
      s.clear();
      assertThat(s.contains(id)).isFalse();
      assertThat(s.usedBytes()).isZero();
      assertThat(files(s.directory(), ".bin")).isZero();
      assertThat(s.contains(s.put(image(2, 2, 1)))).isTrue();
    }
  }

  @Test
  void closingDeletesTheSessionFolderAndIsIdempotent() throws Exception {
    ArtifactStore s = store(1 << 24);
    s.put(image(8, 8, 1));
    Path dir = s.directory();
    s.close();
    s.close();
    assertThat(dir).doesNotExist();
  }

  @Test
  void orphansWithoutALockAreRemovedAndALiveSessionIsKept() throws Exception {
    Path orphan = root.resolve("session-orphan");
    Files.createDirectories(orphan);
    Files.write(orphan.resolve("art-1.bin"), new byte[8]);
    Files.write(orphan.resolve("session.lock"), new byte[0]);
    Path noLock = root.resolve("session-nolock");
    Files.createDirectories(noLock);
    Path foreign = root.resolve("other-folder");
    Files.createDirectories(foreign);
    try (ArtifactStore live = store(1 << 24)) {
      assertThat(ArtifactStore.sweepOrphans(root)).isEqualTo(2);
      assertThat(orphan).doesNotExist();
      assertThat(noLock).doesNotExist();
      assertThat(live.directory()).exists();
      assertThat(foreign).exists();
    }
  }

  @Test
  void sweepingAMissingRootDoesNothing() {
    assertThat(ArtifactStore.sweepOrphans(root.resolve("absent"))).isZero();
  }
}
