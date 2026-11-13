package io.github.erenriquezp.rora.app.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.ai.api.AiEngine;
import io.github.erenriquezp.rora.ai.api.ModelInfo;
import io.github.erenriquezp.rora.ai.api.ModelSource;
import io.github.erenriquezp.rora.ai.api.TileRunner;
import io.github.erenriquezp.rora.ai.store.ArtifactStore;
import io.github.erenriquezp.rora.app.model.RecipeEdits;
import io.github.erenriquezp.rora.app.session.ai.AiJobService;
import io.github.erenriquezp.rora.app.session.ai.AiLimits;
import io.github.erenriquezp.rora.app.session.wallpaper.WallpaperExport;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy;
import io.github.erenriquezp.rora.core.io.ImageMetadata;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.wallpapers.plan.Planner;
import io.github.erenriquezp.rora.wallpapers.plan.Target;
import io.github.erenriquezp.rora.wallpapers.plan.TargetNames;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperJob;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperPlan;
import io.github.erenriquezp.rora.wallpapers.plan.WallpaperResult;
import io.github.erenriquezp.rora.wallpapers.preset.DevicePreset;
import io.github.erenriquezp.rora.wallpapers.render.FillSpec;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Wallpapers sobre el resultado de la receta: geometría y mejora (lista o con el sustituto
 * clásico), sin que la herramienta lance nunca el motor.
 */
class WallpaperRecipeIntegrationTest {

  private static final int W = 80;
  private static final int H = 40;

  private static final ModelInfo INFO = new ModelInfo("fake", 4, 16, 4, "sha", 1, "", "", "");

  private final ManualScheduler time = new ManualScheduler();
  private final ManualExecutor preview = new ManualExecutor();
  private final ManualExecutor full = new ManualExecutor();
  private final ManualExecutor aux = new ManualExecutor();
  private final ManualExecutor publisher = new ManualExecutor();
  private final ManualExecutor aiWorker = new ManualExecutor();
  private final List<EditSession.Frame<Img>> frames = new ArrayList<>();
  private int engineRuns;

  @TempDir Path root;

  private EditSession<Img> session;
  private AiJobService jobs;

  private final AiEngine engine =
      (model, threads) ->
          new TileRunner() {
            @Override
            public float[] run(float[] chw, int h, int w, CancellationToken t) {
              engineRuns++;
              float[] out = new float[3 * h * 4 * w * 4];
              Arrays.fill(out, 1f);
              return out;
            }

            @Override
            public void close() {}
          };

  private static final ModelSource MODEL =
      new ModelSource() {
        @Override
        public ModelInfo info() {
          return INFO;
        }

        @Override
        public byte[] readVerified() {
          return new byte[0];
        }
      };

  private static long ms(long m) {
    return Duration.ofMillis(m).toNanos();
  }

  @BeforeEach
  void setUp() {
    jobs =
        new AiJobService(
            engine,
            MODEL,
            () -> ArtifactStore.open(root, 1 << 24, 1 << 22),
            aiWorker,
            publisher,
            time,
            new AiJobService.Timing() {
              @Override
              public OptionalDouble load() {
                return OptionalDouble.empty();
              }

              @Override
              public void save(double s) {}
            },
            new AiLimits.Memory() {
              @Override
              public long freeHeapBytes() {
                return Long.MAX_VALUE;
              }

              @Override
              public long freePhysicalBytes() {
                return Long.MAX_VALUE;
              }
            },
            2,
            null);
    session =
        new EditSession<>(
            img -> img,
            ProxyPolicy.DEFAULT,
            64L << 20,
            true,
            r ->
                new RenderCoordinator(
                    new RenderLane(
                        "p", r, TileExecutor.sequential(), preview, time, publisher, ms(30)),
                    new RenderLane("f", r, TileExecutor.sequential(), full, time, publisher, 0),
                    new RenderLane("a", r, TileExecutor.sequential(), aux, time, publisher, 0)),
            jobs);
    session.addListener(
        new EditSession.Listener<>() {
          @Override
          public void frameReady(EditSession.Frame<Img> f) {
            frames.add(f);
          }
        });
    session.load(SessionSource.of(new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB)));
    drain();
  }

  @AfterEach
  void tearDown() {
    session.close();
    aiWorker.runAll();
  }

  private void drain() {
    for (int i = 0; i < 1000; i++) {
      int n =
          preview.runAll() + full.runAll() + aux.runAll() + aiWorker.runAll() + publisher.runAll();
      if (n == 0) {
        if (session.isIdle() && !jobs.isRunning()) {
          time.advance(ms(30));
          if (preview.runAll() + full.runAll() + aux.runAll() + publisher.runAll() == 0) {
            return;
          }
        } else {
          time.advance(ms(30));
        }
      }
    }
    throw new AssertionError("no converge");
  }

  private OpSpec spec(String id, Object... kv) {
    Operation op = session.registry().require(id);
    Params.Builder b = op.schema().defaults().toBuilder();
    for (int i = 0; i < kv.length; i += 2) {
      b.set((String) kv[i], kv[i + 1]);
    }
    return OpSpec.of(op, b.build());
  }

  private void addEnhance(double intensity) {
    session.applyRecipe(
        RecipeEdits.putEnhance(
            session.recipe(), spec(RecipeEdits.ENHANCE, "scale", "X4", "intensity", intensity)),
        null);
    drain();
  }

  private AiJobService.State state() {
    return jobs.state(session.enhanceStep().orElseThrow().key());
  }

  private Img export() {
    Img[] out = new Img[1];
    session.exportImg(
        i -> out[0] = i,
        f -> {
          throw new AssertionError(f);
        });
    drain();
    return out[0];
  }

  private static Target custom(String id, int w, int h) {
    return new Target(
        id,
        id,
        DevicePreset.Group.CUSTOM,
        w,
        h,
        Target.FitMode.AUTO,
        FillSpec.blur(),
        Optional.empty(),
        List.of(),
        false);
  }

  private WallpaperResult exportWallpapers(Img full, WallpaperPlan plan, Path out) {
    WallpaperExport.Settings s = WallpaperExport.Settings.defaults(out);
    List<WallpaperExport.Item> items =
        WallpaperExport.layout(
            plan,
            s,
            new TargetNames.Batch("foto", LocalDateTime.of(2026, 11, 13, 0, 0)),
            g -> "grupo");
    return WallpaperExport.run(
        full,
        ImageMetadata.EMPTY,
        Optional.empty(),
        plan,
        items,
        s,
        TileExecutor.sequential(),
        CancellationToken.none(),
        WallpaperJob.Listener.NONE);
  }

  @Test
  void theSourceIsTheRecipeResultIncludingGeometryAndTheClassicSubstitute() {
    session.applyRecipe(
        RecipeEdits.putGeometry(
            session.recipe(),
            spec("transform.crop", "left", 0.0, "top", 0.0, "right", 0.5, "bottom", 1.0)),
        null);
    drain();
    addEnhance(100);
    Img full = export();

    assertThat(state()).isEqualTo(AiJobService.State.NOT_RUN);
    assertThat(full.width()).isEqualTo(session.outputSize().width()).isEqualTo(W / 2 * 4);
    assertThat(full.height()).isEqualTo(session.outputSize().height()).isEqualTo(H * 4);

    WallpaperPlan plan =
        Planner.plan(
            full.width(),
            full.height(),
            List.of(),
            List.of(custom("a", 320, 160), custom("b", 90, 195)));
    WallpaperResult r = exportWallpapers(full, plan, root.resolve("out"));

    assertThat(r.written()).isEqualTo(2);
    assertThat(engineRuns).as("la herramienta nunca lanza la IA").isZero();
    assertThat(state()).isEqualTo(AiJobService.State.NOT_RUN);
    assertThat(plan.requiredEnhanceScale()).as("320 / 160 = ×2, menor de 1,5 no").isPresent();
  }

  @Test
  void aReadyEnhancementIsWhatTheWallpapersUse() throws Exception {
    addEnhance(100);
    session.startEnhance();
    drain();
    assertThat(state()).isEqualTo(AiJobService.State.READY);
    int runs = engineRuns;
    Img full = export();

    WallpaperPlan plan =
        Planner.plan(full.width(), full.height(), List.of(), List.of(custom("a", 320, 160)));
    WallpaperResult r = exportWallpapers(full, plan, root.resolve("out"));

    Path file = ((WallpaperResult.Written) r.outcomes().getFirst()).file();
    BufferedImage b = javax.imageio.ImageIO.read(file.toFile());
    assertThat(b.getWidth()).isEqualTo(320);
    assertThat(b.getRGB(160, 80) & 0xFFFFFF)
        .as("el motor simulado pinta de blanco")
        .isEqualTo(0xFFFFFF);
    assertThat(engineRuns).isEqualTo(runs);
  }
}
