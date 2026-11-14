package io.github.erenriquezp.rora.app.shell;

import io.github.erenriquezp.rora.ai.api.AiEngines;
import io.github.erenriquezp.rora.ai.api.ModelSource;
import io.github.erenriquezp.rora.ai.api.TileRunner;
import io.github.erenriquezp.rora.ai.tile.Tiler;
import io.github.erenriquezp.rora.app.session.StudioIo;
import io.github.erenriquezp.rora.app.session.wallpaper.WallpaperExport;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.Progress;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import io.github.erenriquezp.rora.wallpapers.plan.Planner;
import io.github.erenriquezp.rora.wallpapers.plan.Target;
import io.github.erenriquezp.rora.wallpapers.plan.TargetNames;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperJob;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperPlan;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperResult;
import io.github.erenriquezp.rora.wallpapers.preset.PresetCatalog;
import io.github.erenriquezp.rora.wallpapers.subject.SubjectDetector;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.imageio.ImageIO;

/**
 * Herramienta de desarrollo (no se distribuye): humo del paquete instalado. La lanza {@code
 * tools/package/smoke-package.ps1} con el runtime empaquetado y {@code --patch-module}; abre una
 * foto de 24 MP y un WebP, ejecuta YuNet y x4v3 (×4 de una imagen pequeña) y exporta wallpapers.
 * Escribe el resultado en un archivo porque el lanzador no tiene consola.
 *
 * <pre>PackageSmoke INFORME CARPETA_DE_TRABAJO WEBP</pre>
 */
public final class PackageSmoke {

  private static Path report;

  private PackageSmoke() {}

  private static void say(String fmt, Object... args) throws IOException {
    Files.writeString(
        report,
        String.format(Locale.ROOT, fmt, args) + System.lineSeparator(),
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND);
  }

  private static long ms(long t0) {
    return (System.nanoTime() - t0) / 1_000_000;
  }

  public static void main(String[] args) throws Exception {
    report = Path.of(args[0]);
    Path work = Files.createDirectories(Path.of(args[1]));
    Path webp = Path.of(args[2]);
    try {
      // Como Launcher: el directorio temporal propio existe antes de tocar ORT.
      io.github.erenriquezp.rora.app.diag.AppTemp.prepare(
          io.github.erenriquezp.rora.app.diag.AppTemp.resolve(
              System.getProperty("java.io.tmpdir"),
              io.github.erenriquezp.rora.app.diag.AppDirs.fromSystem()));
      // Como en la app: el toolkit de JavaFX (y sus DLL de Visual C++) se carga antes que ORT.
      javafx.application.Platform.startup(() -> {});
      run(work, webp);
      say("SMOKE OK");
      javafx.application.Platform.exit();
    } catch (Throwable e) {
      say("SMOKE FALLO %s", e);
      for (Throwable t = e; t != null; t = t.getCause()) {
        say("  causa: %s", t);
        for (StackTraceElement f : t.getStackTrace()) {
          say("    at %s", f);
        }
      }
      System.exit(1);
    }
  }

  private static void run(Path work, Path webp) throws Exception {
    say("java.home=%s", System.getProperty("java.home"));
    say("java.io.tmpdir=%s", System.getProperty("java.io.tmpdir"));
    say("maxHeap=%d MB", Runtime.getRuntime().maxMemory() >> 20);

    Path jpg = work.resolve("foto 24MP ñ 🎉.jpg");
    if (!Files.exists(jpg)) {
      BufferedImage bi = new BufferedImage(6000, 4000, BufferedImage.TYPE_INT_RGB);
      int[] row = new int[6000];
      for (int y = 0; y < 4000; y++) {
        for (int x = 0; x < 6000; x++) {
          row[x] = ((x * 255 / 6000) << 16) | ((y * 255 / 4000) << 8) | ((x ^ y) & 0xFF);
        }
        bi.setRGB(0, y, 6000, 1, row, 0, 6000);
      }
      ImageIO.write(bi, "jpg", jpg.toFile());
    }
    long t0 = System.nanoTime();
    Img src = StudioIo.open(jpg, 0, CancellationToken.none(), Progress.NONE).decoded().image();
    say("abrir 24 MP: %d ms (%dx%d)", ms(t0), src.width(), src.height());

    t0 = System.nanoTime();
    Img w = StudioIo.open(webp, 0, CancellationToken.none(), Progress.NONE).decoded().image();
    say("abrir WebP: %d ms (%dx%d)", ms(t0), w.width(), w.height());

    t0 = System.nanoTime();
    try (SubjectDetector d = AiEngines.faceDetector()) {
      int n = d.detect(src, CancellationToken.none()).size();
      say("YuNet 24 MP (incluye cargar ORT): %d ms, %d caras", ms(t0), n);
    }
    say("onnxruntime.native.path=%s", System.getProperty("onnxruntime.native.path"));

    int[] px = new int[160 * 120];
    for (int i = 0; i < px.length; i++) {
      px[i] = 0xFF000000 | (int) ((i * 2654435761L) & 0xFFFFFF);
    }
    Img small = Img.adopt(160, 120, px);
    ModelSource m = AiEngines.bundledModel();
    t0 = System.nanoTime();
    try (TileRunner runner = AiEngines.onnx().open(m, 4)) {
      Tiler tiler = new Tiler(m.info().tile(), m.info().overlap(), m.info().scale());
      Img out = tiler.enhance(small, 4, runner, CancellationToken.none(), Progress.NONE);
      say("x4v3 160x120 -> %dx%d: %d ms", out.width(), out.height(), ms(t0));
    }

    List<Target> targets =
        List.of(preset("pc-4k"), preset("phone-1179-2556"), preset("tablet-1668-2388"));
    WallpaperPlan plan = Planner.plan(src.width(), src.height(), List.of(), targets);
    Path dest = work.resolve("Fondos de prueba ñ 🎉");
    WallpaperExport.Settings s = WallpaperExport.Settings.defaults(dest);
    List<WallpaperExport.Item> items =
        WallpaperExport.layout(
            plan,
            s,
            new TargetNames.Batch("foto ñ", LocalDateTime.of(2026, 11, 14, 0, 0)),
            g -> g.json());
    t0 = System.nanoTime();
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
              new WallpaperJob.Listener() {});
    }
    say("wallpapers 24 MP x 3: %d ms, escritos %d", ms(t0), r.written());
    if (r.written() != targets.size()) {
      throw new IllegalStateException("faltan wallpapers");
    }
    say("PID=%d", ProcessHandle.current().pid());
    say("HOLD");
    Thread.sleep(Long.getLong("smoke.hold.ms", 4000));
  }

  private static Target preset(String id) {
    return Target.of(
        PresetCatalog.builtIns().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow());
  }
}
