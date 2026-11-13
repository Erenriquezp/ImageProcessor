package io.github.erenriquezp.rora.wallpapers.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.FormatId;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WallpaperJobTest {

  static Img source() {
    int[] px = new int[300 * 200];
    for (int i = 0; i < px.length; i++) {
      px[i] = 0xFF000000 | (int) ((i * 2654435761L) & 0xFFFFFF);
    }
    return Img.adopt(300, 200, px);
  }

  static WallpaperPlan plan(Img src) {
    return Planner.plan(
        src.width(),
        src.height(),
        List.of(),
        List.of(
            PlannerTest.preset("pc-fhd"),
            PlannerTest.preset("phone-1179-2556"),
            PlannerTest.preset("uw-32-9"),
            PlannerTest.preset("tablet-1668-2388").withFit(Target.FitMode.CONTAIN)));
  }

  /** Destino en memoria que registra lo que recibe. */
  static final class Memory implements TargetSink {
    final List<String> seen = new ArrayList<>();
    final List<Img> images = new ArrayList<>();
    String failOn = "";
    Runnable onWrite = () -> {};

    @Override
    public Written write(PlannedTarget t, Img image, FormatId f, CancellationToken token)
        throws IOException {
      onWrite.run();
      token.throwIfCancelled();
      if (t.target().id().equals(failOn)) {
        throw new IOException("disco lleno");
      }
      seen.add(t.target().id() + ":" + f + ":" + image.width() + "x" + image.height());
      images.add(image);
      return new Written(Path.of(t.target().id() + "." + f.extension()), image.pixelCount());
    }
  }

  @Test
  void writesEveryTargetAtItsExactSize() {
    Img src = source();
    Memory sink = new Memory();
    WallpaperResult r =
        WallpaperJob.run(
            src,
            plan(src),
            OutputFormat.AUTO,
            0xFFFFFFFF,
            sink,
            TileExecutor.sequential(),
            CancellationToken.none(),
            WallpaperJob.Listener.NONE);

    assertThat(r.written()).isEqualTo(4);
    assertThat(sink.seen)
        .containsExactly(
            "pc-fhd:JPEG:1920x1080",
            "phone-1179-2556:JPEG:1179x2556",
            "uw-32-9:JPEG:5120x1440",
            "tablet-1668-2388:PNG:1668x2388");
  }

  @Test
  void aFailingTargetDoesNotStopTheOthers() {
    Img src = source();
    Memory sink = new Memory();
    sink.failOn = "phone-1179-2556";
    WallpaperResult r =
        WallpaperJob.run(
            src,
            plan(src),
            OutputFormat.JPEG,
            0xFFFFFFFF,
            sink,
            TileExecutor.sequential(),
            CancellationToken.none(),
            WallpaperJob.Listener.NONE);

    assertThat(r.written()).isEqualTo(3);
    assertThat(r.outcomes().get(1))
        .isInstanceOfSatisfying(
            WallpaperResult.Failed.class, f -> assertThat(f.reason()).isEqualTo("disco lleno"));
  }

  @Test
  void cancellingStopsAndMarksTheRest() {
    Img src = source();
    Memory sink = new Memory();
    CancellationToken token = CancellationToken.create();
    List<String> events = new ArrayList<>();
    sink.onWrite =
        () -> {
          if (sink.seen.size() == 1) {
            token.cancel();
          }
        };
    WallpaperResult r =
        WallpaperJob.run(
            src,
            plan(src),
            OutputFormat.JPEG,
            0xFFFFFFFF,
            sink,
            TileExecutor.sequential(),
            token,
            new WallpaperJob.Listener() {
              @Override
              public void started(int i, int total, PlannedTarget t) {
                events.add("start " + i + "/" + total);
              }

              @Override
              public void finished(int i, WallpaperResult.Outcome o) {
                events.add("end " + i + " " + o.getClass().getSimpleName());
              }
            });

    assertThat(r.written()).isEqualTo(1);
    assertThat(r.cancelled()).isEqualTo(3);
    assertThat(events)
        .containsExactly(
            "start 0/4",
            "end 0 Written",
            "start 1/4",
            "end 1 Cancelled",
            "end 2 Cancelled",
            "end 3 Cancelled");
  }

  @Test
  void theSameInputGivesTheSamePixels() {
    Img src = source();
    Memory a = new Memory();
    Memory b = new Memory();
    WallpaperJob.run(
        src,
        plan(src),
        OutputFormat.AUTO,
        0xFFFFFFFF,
        a,
        TileExecutor.sequential(),
        CancellationToken.none(),
        WallpaperJob.Listener.NONE);
    try (TileExecutor four = TileExecutor.create(4)) {
      WallpaperJob.run(
          src,
          plan(src),
          OutputFormat.AUTO,
          0xFFFFFFFF,
          b,
          four,
          CancellationToken.none(),
          WallpaperJob.Listener.NONE);
    }
    for (int i = 0; i < a.images.size(); i++) {
      assertThat(b.images.get(i).sameContent(a.images.get(i))).isTrue();
    }
  }

  @Test
  void refusesASourceThatIsNotThePlans() {
    Img src = source();
    assertThatThrownBy(
            () ->
                WallpaperJob.run(
                    Img.create(10, 10),
                    plan(src),
                    OutputFormat.AUTO,
                    0,
                    new Memory(),
                    TileExecutor.sequential(),
                    CancellationToken.none(),
                    WallpaperJob.Listener.NONE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void namesUseTheNewTokensAndStayInsideTheRoot() {
    Img src = source();
    PlannedTarget t = plan(src).targets().get(1);
    TargetNames.Batch batch =
        new TargetNames.Batch("Vacaciones ñ", LocalDateTime.of(2026, 11, 13, 9, 5, 7));

    assertThat(TargetNames.fileName(TargetNames.DEFAULT_FILE, batch, 1, t, "Móvil", FormatId.PNG))
        .startsWith("Vacaciones ñ_")
        .endsWith("_1179x2556.png")
        .doesNotContain("\"", "/", "\\");
    assertThat(TargetNames.fileName("{grupo}-{fecha}-{n}", batch, 3, t, "Móvil", FormatId.JPEG))
        .isEqualTo("Móvil-2026-11-13-3.jpg");

    Path root = Path.of("salida");
    assertThat(TargetNames.folder(root, TargetNames.DEFAULT_FOLDER, batch, "Móvil"))
        .isEqualTo(root.resolve("Vacaciones ñ - wallpapers").resolve("Móvil"));
    Path evil =
        TargetNames.folder(
            root, "../{grupo}/..", new TargetNames.Batch("..", batch.now()), "..\\x");
    assertThat(evil.normalize().startsWith(root)).isTrue();
  }

  @Test
  void duplicateNamesInABatchAreNumbered() {
    assertThat(TargetNames.unique(List.of("a.jpg", "A.jpg", "b.png", "a.jpg")))
        .containsExactly("a.jpg", "A (2).jpg", "b.png", "a (3).jpg");
  }
}
