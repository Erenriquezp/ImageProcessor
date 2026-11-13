package io.github.erenriquezp.rora.app.session.wallpaper;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.app.session.ExportTarget;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ExportSettings.MetadataPolicy;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import io.github.erenriquezp.rora.wallpapers.plan.OutputFormat;
import io.github.erenriquezp.rora.wallpapers.plan.PlannedTarget;
import io.github.erenriquezp.rora.wallpapers.plan.Planner;
import io.github.erenriquezp.rora.wallpapers.plan.Target;
import io.github.erenriquezp.rora.wallpapers.plan.TargetNames;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperJob;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperPlan;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperResult;
import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset;
import io.github.erenriquezp.rora.wallpapers.preset.PresetCatalog;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WallpaperExportTest {

  static final TargetNames.Batch BATCH =
      new TargetNames.Batch("Playa", LocalDateTime.of(2026, 11, 13, 10, 0, 0));

  static final Map<DevicePreset.Group, String> GROUPS =
      Map.of(
          DevicePreset.Group.PC_16_9, "PC 16-9",
          DevicePreset.Group.PHONE, "Móvil",
          DevicePreset.Group.TABLET, "Tableta",
          DevicePreset.Group.ULTRAWIDE, "Ultrapanorámico");

  @TempDir Path root;

  static Img photo(int w, int h) {
    int[] px = new int[w * h];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        px[y * w + x] = 0xFF000000 | (x * 255 / w) << 16 | (y * 255 / h) << 8 | 0x60;
      }
    }
    return Img.adopt(w, h, px);
  }

  static Target preset(String id) {
    return Target.of(
        PresetCatalog.builtIns().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow());
  }

  static WallpaperPlan plan(Img src, Target... targets) {
    return Planner.plan(src.width(), src.height(), List.of(), List.of(targets));
  }

  private WallpaperResult run(
      Img src,
      WallpaperPlan plan,
      WallpaperExport.Settings s,
      ImageMetadata meta,
      CancellationToken token) {
    List<WallpaperExport.Item> items = WallpaperExport.layout(plan, s, BATCH, GROUPS::get);
    return WallpaperExport.run(
        src,
        meta,
        Optional.empty(),
        plan,
        items,
        s,
        TileExecutor.sequential(),
        token,
        WallpaperJob.Listener.NONE);
  }

  private static List<Path> files(Path dir) throws IOException {
    try (Stream<Path> s = Files.walk(dir)) {
      return s.filter(Files::isRegularFile).sorted().toList();
    }
  }

  @Test
  void onePhotoGivesEveryFileWithItsExactSizeAndName() throws IOException {
    Img src = photo(600, 400);
    WallpaperPlan plan =
        plan(
            src,
            preset("pc-fhd"),
            preset("phone-1179-2556"),
            preset("tablet-1668-2388").withFit(Target.FitMode.CONTAIN),
            preset("uw-32-9"));
    WallpaperExport.Settings s = WallpaperExport.Settings.defaults(root);

    WallpaperResult r = run(src, plan, s, ImageMetadata.EMPTY, CancellationToken.none());

    assertThat(r.written()).isEqualTo(4);
    Path base = root.resolve("Playa - wallpapers");
    assertThat(files(root))
        .containsExactlyInAnyOrder(
            base.resolve("PC 16-9").resolve(fileOf(plan.targets().get(0), "1920x1080.jpg")),
            base.resolve("Móvil").resolve(fileOf(plan.targets().get(1), "1179x2556.jpg")),
            base.resolve("Tableta").resolve(fileOf(plan.targets().get(2), "1668x2388.png")),
            base.resolve("Ultrapanorámico")
                .resolve(fileOf(plan.targets().get(3), "5120x1440.jpg")));
    for (WallpaperResult.Outcome o : r.outcomes()) {
      WallpaperResult.Written w = (WallpaperResult.Written) o;
      BufferedImage b = ImageIO.read(w.file().toFile());
      assertThat(b.getWidth()).isEqualTo(o.target().width());
      assertThat(b.getHeight()).isEqualTo(o.target().height());
      assertThat(Files.size(w.file())).isEqualTo(w.bytes());
    }
  }

  private static String fileOf(PlannedTarget t, String suffix) {
    return io.github.erenriquezp.rora.core.io.FileNames.sanitize("Playa_" + t.target().name() + "_")
        + suffix;
  }

  @Test
  void collisionsAreNumberedOrOverwrittenAndDetectedBeforehand() throws IOException {
    Img src = photo(300, 200);
    WallpaperPlan plan = plan(src, preset("pc-fhd"));
    WallpaperExport.Settings s = WallpaperExport.Settings.defaults(root);
    List<WallpaperExport.Item> items = WallpaperExport.layout(plan, s, BATCH, GROUPS::get);
    assertThat(WallpaperExport.existing(items)).isEmpty();

    run(src, plan, s, ImageMetadata.EMPTY, CancellationToken.none());
    assertThat(WallpaperExport.existing(items)).containsExactly(items.getFirst().file());

    WallpaperResult numbered = run(src, plan, s, ImageMetadata.EMPTY, CancellationToken.none());
    Path second = ((WallpaperResult.Written) numbered.outcomes().getFirst()).file();
    assertThat(second).isNotEqualTo(items.getFirst().file());
    assertThat(second.getFileName().toString()).contains("(2)");

    WallpaperResult over =
        run(
            src,
            plan,
            s.withCollision(ExportTarget.Collision.OVERWRITE),
            ImageMetadata.EMPTY,
            CancellationToken.none());
    assertThat(((WallpaperResult.Written) over.outcomes().getFirst()).file())
        .isEqualTo(items.getFirst().file());
    assertThat(files(root)).hasSize(2);
  }

  @Test
  void twoTargetsWithTheSameNameInOneFolderAreNumbered() {
    Img src = photo(300, 200);
    WallpaperPlan plan =
        plan(src, preset("pc-fhd"), preset("pc-fhd").withFit(Target.FitMode.CONTAIN));
    WallpaperExport.Settings s =
        WallpaperExport.Settings.defaults(root).withFormat(OutputFormat.JPEG);
    List<WallpaperExport.Item> items = WallpaperExport.layout(plan, s, BATCH, GROUPS::get);
    assertThat(items.get(1).fileName()).isNotEqualTo(items.get(0).fileName()).contains("(2)");
  }

  @Test
  void cancellingLeavesOnlyFinishedFiles() throws IOException {
    Img src = photo(600, 400);
    WallpaperPlan plan = plan(src, preset("pc-fhd"), preset("pc-4k"), preset("pc-5k"));
    WallpaperExport.Settings s = WallpaperExport.Settings.defaults(root);
    CancellationToken token = CancellationToken.create();
    List<WallpaperExport.Item> items = WallpaperExport.layout(plan, s, BATCH, GROUPS::get);

    WallpaperResult r =
        WallpaperExport.run(
            src,
            ImageMetadata.EMPTY,
            Optional.empty(),
            plan,
            items,
            s,
            TileExecutor.sequential(),
            token,
            new WallpaperJob.Listener() {
              @Override
              public void progress(int index, double fraction) {
                if (index == 1 && fraction > 0.2) {
                  token.cancel();
                }
              }
            });

    assertThat(r.written()).isEqualTo(1);
    assertThat(r.cancelled()).isEqualTo(2);
    assertThat(files(root)).containsExactly(items.getFirst().file());
  }

  @Test
  void aFailingTargetDoesNotStopTheOthers() throws IOException {
    Img src = photo(300, 200);
    WallpaperPlan plan =
        plan(src, preset("pc-fhd"), preset("phone-1179-2556"), preset("tablet-1668-2388"));
    WallpaperExport.Settings s = WallpaperExport.Settings.defaults(root);
    // La carpeta del grupo "Móvil" ya existe como archivo: no se puede crear.
    Path base = Files.createDirectories(root.resolve("Playa - wallpapers"));
    Files.writeString(base.resolve("Móvil"), "estorbo");

    WallpaperResult r = run(src, plan, s, ImageMetadata.EMPTY, CancellationToken.none());

    assertThat(r.written()).isEqualTo(2);
    assertThat(r.outcomes().get(1)).isInstanceOf(WallpaperResult.Failed.class);
  }

  @Test
  void aReadOnlyTargetFailsAlone() throws IOException {
    Img src = photo(300, 200);
    WallpaperPlan plan = plan(src, preset("pc-fhd"), preset("tablet-1668-2388"));
    WallpaperExport.Settings s =
        WallpaperExport.Settings.defaults(root).withCollision(ExportTarget.Collision.OVERWRITE);
    List<WallpaperExport.Item> items = WallpaperExport.layout(plan, s, BATCH, GROUPS::get);
    Path locked = items.getFirst().file();
    Files.createDirectories(locked.getParent());
    Files.writeString(locked, "x");
    boolean readOnly = lock(locked.getParent(), locked);
    Assumptions.assumeTrue(
        readOnly, "el sistema no permite marcar la carpeta o el archivo como solo lectura");
    try {
      WallpaperResult r = run(src, plan, s, ImageMetadata.EMPTY, CancellationToken.none());
      assertThat(r.outcomes().getFirst()).isInstanceOf(WallpaperResult.Failed.class);
      assertThat(r.outcomes().get(1)).isInstanceOf(WallpaperResult.Written.class);
      assertThat(Files.readString(locked)).isEqualTo("x");
    } finally {
      unlock(locked.getParent(), locked);
    }
  }

  /** Carpeta sin escritura (POSIX) o archivo de solo lectura (Windows). */
  private static boolean lock(Path dir, Path file) throws IOException {
    if (Files.getFileStore(dir).supportsFileAttributeView("posix")) {
      Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("r-xr-xr-x"));
      return !Files.isWritable(dir);
    }
    DosFileAttributeView dos = Files.getFileAttributeView(file, DosFileAttributeView.class);
    if (dos == null) {
      return false;
    }
    dos.setReadOnly(true);
    return !Files.isWritable(file);
  }

  private static void unlock(Path dir, Path file) throws IOException {
    if (Files.getFileStore(dir).supportsFileAttributeView("posix")) {
      Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));
    } else {
      Files.getFileAttributeView(file, DosFileAttributeView.class).setReadOnly(false);
    }
  }

  /** Con la política por defecto (quitar todo) no queda EXIF ni rastro de la cámara. */
  @Test
  void metadataFollowsThePolicy() throws IOException {
    Img src = photo(300, 200);
    byte[] model = "Modelo-Cam\0".getBytes(StandardCharsets.US_ASCII);
    ImageMetadata meta =
        new ImageMetadata(
            Optional.of(
                new ImageMetadata.ExifData(
                    1,
                    List.of(new ImageMetadata.ExifTag(0x0110, 2, model.length, model, false)),
                    List.of(),
                    List.of())),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            false);
    WallpaperPlan plan =
        plan(src, preset("pc-fhd"), preset("tablet-1668-2388").withFit(Target.FitMode.CONTAIN));

    WallpaperResult removed =
        run(
            src,
            plan,
            WallpaperExport.Settings.defaults(root.resolve("a")),
            meta,
            CancellationToken.none());
    WallpaperResult kept =
        run(
            src,
            plan,
            WallpaperExport.Settings.defaults(root.resolve("b")).withMetadata(MetadataPolicy.KEEP),
            meta,
            CancellationToken.none());

    for (WallpaperResult.Outcome o : removed.outcomes()) {
      byte[] bytes = Files.readAllBytes(((WallpaperResult.Written) o).file());
      assertThat(count(bytes, "Modelo-Cam")).as("sin cámara").isZero();
      assertThat(count(bytes, "Exif\0\0") + count(bytes, "eXIf")).as("sin EXIF").isZero();
    }
    for (WallpaperResult.Outcome o : kept.outcomes()) {
      byte[] bytes = Files.readAllBytes(((WallpaperResult.Written) o).file());
      assertThat(count(bytes, "Modelo-Cam")).as("control: con KEEP sí está").isPositive();
    }
  }

  static int count(byte[] data, String ascii) {
    byte[] n = ascii.getBytes(StandardCharsets.ISO_8859_1);
    int c = 0;
    outer:
    for (int i = 0; i + n.length <= data.length; i++) {
      for (int j = 0; j < n.length; j++) {
        if (data[i + j] != n[j]) {
          continue outer;
        }
      }
      c++;
    }
    return c;
  }
}
