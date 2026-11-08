package io.github.erenriquezp.rora.imaging.io;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.DecodeOptions;
import io.github.erenriquezp.rora.core.io.ExportSettings;
import io.github.erenriquezp.rora.core.io.FormatId;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Memoria y tiempos reales de 12 y 24 MP. Solo corre en la ejecución {@code memory} (JVM aparte con
 * {@code -Xmx1536m}); saltable con {@code -Dskip.memory=true}. Informe en {@code
 * target/io-memory-report.txt}.
 */
@Tag("memory")
class IoMemoryTest {

  private static final long MB = 1L << 20;

  private final List<String> report = new ArrayList<>();
  private long worst;

  private static long peakHeap() {
    long s = 0;
    for (MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans()) {
      if (p.getType() == MemoryType.HEAP) {
        s += p.getPeakUsage().getUsed();
      }
    }
    return s;
  }

  private static long live() {
    System.gc();
    System.gc();
    Runtime r = Runtime.getRuntime();
    return r.totalMemory() - r.freeMemory();
  }

  private interface Work {
    void run() throws Exception;
  }

  private void measure(String label, Work work) throws Exception {
    live();
    ManagementFactory.getMemoryPoolMXBeans().forEach(MemoryPoolMXBean::resetPeakUsage);
    long t0 = System.nanoTime();
    work.run();
    long ms = (System.nanoTime() - t0) / 1_000_000;
    long peak = peakHeap();
    worst = Math.max(worst, peak);
    report.add(String.format(Locale.ROOT, "%-34s %7d ms   pico %5d MB", label, ms, peak / MB));
  }

  private void cycle(int w, int h, Path dir) throws Exception {
    Img src = Fixtures.photo(w, h);
    String mp = w * h / 1_000_000 + " MP";
    ImageIoDecoder decoder = new ImageIoDecoder();
    Exporter exporter = new Exporter();
    for (FormatId f : List.of(FormatId.JPEG, FormatId.PNG, FormatId.TIFF)) {
      String name = mp.replace(' ', '_') + "." + f.extension();
      ExportSettings s = ExportSettings.defaults(f);
      Path out = dir.resolve(name);
      measure(
          "exportar " + f + " " + mp,
          () ->
              exporter.export(
                  src,
                  ImageMetadata.EMPTY,
                  s,
                  Exporter.Request.ask(dir, name),
                  TileExecutor.sequential(),
                  CancellationToken.none(),
                  Progress.NONE));
      report.add(String.format(Locale.ROOT, "  tamaño: %d KB", Files.size(out) / 1024));
      measure(
          "abrir " + f + " " + mp,
          () -> {
            var d =
                decoder.decode(out, DecodeOptions.DEFAULT, CancellationToken.none(), Progress.NONE);
            assertThat(d.image().width()).isEqualTo(w);
          });
    }
  }

  @Test
  @Timeout(600)
  void twelveAndTwentyFourMegapixelsFitAndAreTimed(@TempDir Path dir) throws Exception {
    report.add("maxMemory " + Runtime.getRuntime().maxMemory() / MB + " MB");
    report.add("JVM " + System.getProperty("java.vm.version"));
    try {
      cycle(4000, 3000, dir);
      cycle(6000, 4000, dir);
    } finally {
      report.add("peor pico de pools: " + worst / MB + " MB");
      Path target = Path.of(System.getProperty("user.dir"), "target", "io-memory-report.txt");
      Files.createDirectories(target.getParent());
      Files.write(target, report);
      report.forEach(System.out::println);
    }
    assertThat(worst).isLessThan(1536 * MB);
  }
}
