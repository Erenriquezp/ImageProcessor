package io.github.erenriquezp.rora.app.session.wallpaper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.CancelledException;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Downscale;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import io.github.erenriquezp.rora.wallpapers.plan.OutputFormat;
import io.github.erenriquezp.rora.wallpapers.plan.Planner;
import io.github.erenriquezp.rora.wallpapers.plan.Target;
import io.github.erenriquezp.rora.wallpapers.plan.TargetNames;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperJob;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperPlan;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** El peso estimado sobre la vista reducida se parece al del archivo real. */
class WallpaperExportEstimateTest {

  @TempDir Path dir;

  @Test
  void jpegEstimatesAreWithinAFactorOfTwoOfTheRealFiles() throws Exception {
    Img src = WallpaperMemoryTest.photo(2400, 1600);
    Img preview =
        Downscale.areaAverage(
            src, 600, 400, TileExecutor.sequential(), CancellationToken.none(), Progress.NONE);
    List<Target> targets =
        List.of(
            WallpaperExportTest.preset("pc-fhd"),
            WallpaperExportTest.preset("phone-1179-2556"),
            WallpaperExportTest.preset("tablet-1668-2388"));
    WallpaperPlan plan = Planner.plan(src.width(), src.height(), List.of(), targets);
    WallpaperExport.Settings s =
        WallpaperExport.Settings.defaults(dir).withFormat(OutputFormat.JPEG);
    List<WallpaperExport.Item> items =
        WallpaperExport.layout(
            plan,
            s,
            new TargetNames.Batch("est", LocalDateTime.of(2026, 11, 14, 0, 0)),
            g -> g.json());

    List<Long> estimates =
        WallpaperExport.estimate(preview, plan, items, s, CancellationToken.none());

    WallpaperResult r =
        WallpaperExport.run(
            src,
            ImageMetadata.EMPTY,
            Optional.empty(),
            plan,
            items,
            s,
            TileExecutor.sequential(),
            CancellationToken.none(),
            WallpaperJob.Listener.NONE);
    assertThat(r.written()).isEqualTo(3);
    assertThat(estimates).hasSize(3);
    for (int i = 0; i < items.size(); i++) {
      long real = Files.size(items.get(i).file());
      assertThat(estimates.get(i))
          .as(items.get(i).planned().target().id() + " real " + real)
          .isBetween(real / 2, real * 2);
    }
  }

  @Test
  void aCancelledEstimateStops() {
    Img src = WallpaperMemoryTest.photo(400, 300);
    WallpaperPlan plan =
        Planner.plan(400, 300, List.of(), List.of(WallpaperExportTest.preset("pc-fhd")));
    WallpaperExport.Settings s = WallpaperExport.Settings.defaults(dir);
    List<WallpaperExport.Item> items =
        WallpaperExport.layout(
            plan,
            s,
            new TargetNames.Batch("est", LocalDateTime.of(2026, 11, 14, 0, 0)),
            g -> g.json());
    CancellationToken token = CancellationToken.create();
    token.cancel();
    assertThatThrownBy(() -> WallpaperExport.estimate(src, plan, items, s, token))
        .isInstanceOf(CancelledException.class);
  }
}
