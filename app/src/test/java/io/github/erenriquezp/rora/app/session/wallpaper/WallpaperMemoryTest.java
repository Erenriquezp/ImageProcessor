package io.github.erenriquezp.rora.app.session.wallpaper;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import io.github.erenriquezp.rora.wallpapers.plan.PlannedTarget;
import io.github.erenriquezp.rora.wallpapers.plan.Planner;
import io.github.erenriquezp.rora.wallpapers.plan.Target;
import io.github.erenriquezp.rora.wallpapers.plan.TargetNames;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperJob;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperPlan;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperResult;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 24 MP × 8 destinos con {@code -Xmx1536m}: termina y el pico de heap queda por debajo de 1 GB.
 * Solo corre en la ejecución {@code memory} (JVM aparte); saltable con {@code -Dskip.memory=true}.
 * Tiempos por destino en {@code target/wallpaper-memory-report.txt}.
 */
@Tag("memory")
class WallpaperMemoryTest {

  private static final long MB = 1L << 20;

  @TempDir Path dir;

  private static long peakHeap() {
    long s = 0;
    for (MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans()) {
      if (p.getType() == MemoryType.HEAP) {
        s += p.getPeakUsage().getUsed();
      }
    }
    return s;
  }

  /** Foto sintética con detalle (degradados y ruido determinista) para que los códecs trabajen. */
  static Img photo(int w, int h) {
    int[] px = new int[w * h];
    long seed = 12345;
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        seed = seed * 6364136223846793005L + 1442695040888963407L;
        int n = (int) (seed >>> 59);
        int r = Math.min(255, x * 255 / w + n);
        int g = Math.min(255, y * 255 / h + n);
        int b = Math.min(255, ((x ^ y) & 0xFF) / 2 + n);
        px[y * w + x] = 0xFF000000 | r << 16 | g << 8 | b;
      }
    }
    return Img.adopt(w, h, px);
  }

  @Test
  void twentyFourMegapixelsToEightTargets() throws Exception {
    Img src = photo(6000, 4000);
    List<Target> targets =
        List.of(
            WallpaperExportTest.preset("pc-4k"),
            WallpaperExportTest.preset("pc-5k"),
            WallpaperExportTest.preset("pc-qhd"),
            WallpaperExportTest.preset("phone-1179-2556"),
            WallpaperExportTest.preset("phone-1440-3200").withFit(Target.FitMode.CONTAIN),
            WallpaperExportTest.preset("tablet-2048-2732"),
            WallpaperExportTest.preset("uw-32-9"),
            WallpaperExportTest.preset("laptop-16-notch"));
    WallpaperPlan plan = Planner.plan(src.width(), src.height(), List.of(), targets);
    WallpaperExport.Settings s = WallpaperExport.Settings.defaults(dir);
    List<WallpaperExport.Item> items =
        WallpaperExport.layout(
            plan,
            s,
            new TargetNames.Batch("mem", LocalDateTime.of(2026, 11, 13, 0, 0)),
            g -> g.json());
    System.gc();
    ManagementFactory.getMemoryPoolMXBeans().forEach(MemoryPoolMXBean::resetPeakUsage);
    long[] start = new long[targets.size()];
    long[] took = new long[targets.size()];
    long t0 = System.nanoTime();
    WallpaperResult r;
    try (TileExecutor ex = TileExecutor.create(TileExecutor.defaultParallelism())) {
      r =
          WallpaperExport.run(
              src,
              ImageMetadata.EMPTY,
              Optional.empty(),
              plan,
              items,
              s,
              ex,
              CancellationToken.none(),
              new WallpaperJob.Listener() {
                @Override
                public void started(int i, int total, PlannedTarget t) {
                  start[i] = System.nanoTime();
                }

                @Override
                public void finished(int i, WallpaperResult.Outcome o) {
                  took[i] = (System.nanoTime() - start[i]) / 1_000_000;
                }
              });
    }
    long total = (System.nanoTime() - t0) / 1_000_000;
    long peak = peakHeap();

    List<String> report = new ArrayList<>();
    report.add(
        String.format(
            Locale.ROOT,
            "Fuente 6000x4000 (24 MP), %d hilos, -Xmx%d MB",
            TileExecutor.defaultParallelism(),
            Runtime.getRuntime().maxMemory() / MB));
    for (int i = 0; i < targets.size(); i++) {
      PlannedTarget p = plan.targets().get(i);
      WallpaperResult.Outcome o = r.outcomes().get(i);
      report.add(
          String.format(
              Locale.ROOT,
              "%-22s %5dx%-5d %-7s %-4s %6d ms  %8.1f MB",
              p.target().id(),
              p.target().width(),
              p.target().height(),
              p.resolved(),
              items.get(i).format(),
              took[i],
              o instanceof WallpaperResult.Written w ? w.bytes() / (double) MB : -1));
    }
    report.add(String.format(Locale.ROOT, "Total %d ms, pico de heap %d MB", total, peak / MB));
    Files.write(Path.of("target", "wallpaper-memory-report.txt"), report);
    report.forEach(System.out::println);

    assertThat(r.written()).isEqualTo(targets.size());
    assertThat(peak).as("pico de heap").isLessThan(1024 * MB);
  }
}
