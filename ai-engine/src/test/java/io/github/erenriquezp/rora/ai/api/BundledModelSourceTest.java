package io.github.erenriquezp.rora.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure.Reason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BundledModelSourceTest {

  @Test
  void theBundledModelMatchesItsHashAndSize() throws Exception {
    ModelSource source = AiEngines.bundledModel();
    byte[] data = source.readVerified();
    assertThat((long) data.length).isEqualTo(source.info().bytes());
    assertThat(source.info().scale()).isEqualTo(4);
  }

  @Test
  void alteredBytesAreReportedAsCorrupt() throws Exception {
    byte[] data = AiEngines.bundledModel().readVerified();
    data[data.length / 2] ^= 1;
    assertThatThrownBy(() -> BundledModelSource.verified(data, BundledModelSource.INFO.sha256()))
        .isInstanceOfSatisfying(
            AiFailure.class, e -> assertThat(e.reason()).isEqualTo(Reason.MODEL_CORRUPT));
  }

  @ParameterizedTest
  @CsvSource({"1,1", "2,2", "3,2", "4,2", "8,4", "16,8", "6,3"})
  void defaultThreadsKeepOneFreeForTheInterface(int logical, int expected) {
    assertThat(AiEngines.defaultThreads(logical)).isEqualTo(expected);
  }
}
