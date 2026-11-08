package io.github.erenriquezp.rora.lab.session;

import io.github.erenriquezp.rora.app.session.*;
import static io.github.erenriquezp.rora.lab.session.ManualScheduler.ms;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.lab.session.PipelineSupport.Step;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.image.ProxyPolicy;
import io.github.erenriquezp.rora.core.op.ExposureStats;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.domain.ConvolutionKernel;
import io.github.erenriquezp.rora.domain.ExposureDiagnostic;
import io.github.erenriquezp.rora.domain.FilterType;
import io.github.erenriquezp.rora.service.Analysis;
import io.github.erenriquezp.rora.app.imaging.ImageBridge;
import io.github.erenriquezp.rora.service.ImageProcessor;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link EditSession} headless con carriles, tiempo y publicador manuales. */
class EditSessionTest {

  private ManualScheduler time;
  private ManualExecutor previewWorker;
  private ManualExecutor fullWorker;
  private ManualExecutor publisher;
  private EditSession<Img> session;
  private final List<EditSession.Frame<Img>> frames = new ArrayList<>();
  private final List<int[]> history = new ArrayList<>();
  private final List<RenderFailure> failures = new ArrayList<>();

  @BeforeEach
  void setUp() {
    time = new ManualScheduler();
    previewWorker = new ManualExecutor();
    fullWorker = new ManualExecutor();
    publisher = new ManualExecutor();
    session =
        new EditSession<>(
            img -> img,
            // Tope de 1 MP para que 3000×2000 tenga proxy también con zoom (tope por defecto: 4
            // MP).
            new ProxyPolicy(1_000_000L, 0.75, 256, 1.5),
            256L << 20,
            true,
            r ->
                new RenderCoordinator(
                    new RenderLane(
                        "p", r, TileExecutor.sequential(), previewWorker, time, publisher, ms(30)),
                    new RenderLane(
                        "f", r, TileExecutor.sequential(), fullWorker, time, publisher, 0),
                    null));
    session.addListener(
        new EditSession.Listener<>() {
          @Override
          public void frameReady(EditSession.Frame<Img> f) {
            frames.add(f);
          }

          @Override
          public void historyChanged(int depth, boolean canUndo, boolean canRedo) {
            history.add(new int[] {depth, canUndo ? 1 : 0, canRedo ? 1 : 0});
          }

          @Override
          public void failed(RenderFailure f) {
            failures.add(f);
          }
        });
  }

  @AfterEach
  void tearDown() {
    session.close();
  }

  /** Ejecuta todo lo pendiente (workers, publicador y temporizadores) hasta quedar en reposo. */
  private void drain() {
    for (int i = 0; i < 1000; i++) {
      int n = previewWorker.runAll() + fullWorker.runAll() + publisher.runAll();
      if (n == 0) {
        if (session.isIdle() && time.pendingTasks() == 0) {
          return;
        }
        time.advance(ms(30));
      }
    }
    throw new AssertionError("no converge");
  }

  private EditSession.Frame<Img> lastFrame() {
    return frames.get(frames.size() - 1);
  }

  private FilterParams params() {
    return SessionFixtures.randomParams(new SplittableRandom(9)).withNoHighlights();
  }

  private OpSpec spec(FilterType t, FilterParams p) {
    return new FilterSpecs(session.registry()).of(t, p).orElseThrow();
  }

  @Test
  void smallImageShowsTheExactImageAndHistoryWorks() {
    BufferedImage bi = SessionFixtures.image("3BYTE_BGR", 64, 48, 1);
    session.setViewport(800, 600, 1, 1);
    session.load(SessionSource.of(bi));
    drain();
    assertThat(lastFrame().approximate()).isFalse();
    assertThat(PipelineSupport.same(bi, lastFrame().pixels())).isTrue();

    FilterParams p = params();
    session.setPending(Optional.of(spec(FilterType.POINT_SATURATION, p)));
    drain();
    BufferedImage expected =
        PipelineSupport.reference(bi, List.of(new Step(FilterType.POINT_SATURATION, p)));
    assertThat(PipelineSupport.same(expected, lastFrame().pixels())).isTrue();

    assertThat(session.apply(spec(FilterType.POINT_SATURATION, p))).isEqualTo(1);
    assertThat(session.pending()).isEmpty();
    session.apply(spec(FilterType.NEGATIVE, p));
    drain();
    BufferedImage two =
        PipelineSupport.reference(
            bi,
            List.of(new Step(FilterType.POINT_SATURATION, p), new Step(FilterType.NEGATIVE, p)));
    assertThat(PipelineSupport.same(two, lastFrame().pixels())).isTrue();
    assertThat(session.undo()).isTrue();
    drain();
    assertThat(PipelineSupport.same(expected, lastFrame().pixels())).isTrue();
    assertThat(session.redo()).isTrue();
    assertThat(session.redo()).isFalse();
    drain();
    assertThat(PipelineSupport.same(two, lastFrame().pixels())).isTrue();
    session.reset();
    drain();
    assertThat(PipelineSupport.same(bi, lastFrame().pixels())).isTrue();
    assertThat(session.canUndo()).isFalse();
    assertThat(history.get(history.size() - 1)).containsExactly(0, 0, 0);
    assertThat(failures).isEmpty();
  }

  @Test
  void largeImageUsesAProxyAndZoomBeyondItBringsTheFullImage() {
    BufferedImage bi = SessionFixtures.image("INT_RGB", 3000, 2000, 2);
    session.setViewport(800, 600, 1, 1);
    session.load(SessionSource.of(bi));
    drain();
    EditSession.Frame<Img> f = lastFrame();
    assertThat(f.approximate()).isTrue();
    assertThat(f.pixels().width()).isEqualTo(1024);
    assertThat(f.fullWidth()).isEqualTo(3000);
    assertThat(session.proxySize()).isPresent();

    int before = frames.size();
    session.setViewport(800, 600, 1, 8); // pide más de lo que da el tope de 4 MP
    drain();
    assertThat(frames.size()).isEqualTo(before + 2);
    assertThat(frames.get(before).pixels().width()).as("proxy en el tope").isEqualTo(1225);
    assertThat(frames.get(before).approximate()).as("primero el proxy").isTrue();
    EditSession.Frame<Img> full = lastFrame();
    assertThat(full.approximate()).isFalse();
    assertThat(full.pixels().width()).isEqualTo(3000);
  }

  @Test
  void onlyTheLatestPendingIsShown() {
    BufferedImage bi = SessionFixtures.image("INT_ARGB", 40, 30, 3);
    session.load(SessionSource.of(bi));
    drain();
    frames.clear();
    FilterParams p = params();
    for (int i = 0; i < 10; i++) {
      session.setPending(Optional.of(spec(FilterType.BRIGHTNESS, withBrightness(p, i * 10))));
    }
    drain();
    assertThat(frames).hasSize(1);
    BufferedImage expected =
        PipelineSupport.reference(
            bi, List.of(new Step(FilterType.BRIGHTNESS, withBrightness(p, 90))));
    assertThat(PipelineSupport.same(expected, lastFrame().pixels())).isTrue();
  }

  @Test
  void exportKeepsTheTypeTheAppUsedToSave() {
    BufferedImage jpeg = SessionFixtures.image("3BYTE_BGR", 30, 20, 4);
    session.load(SessionSource.of(jpeg));
    drain();
    AtomicReference<BufferedImage> out = new AtomicReference<>();
    session.export(out::set, failures::add);
    drain();
    assertThat(out.get().getType()).isEqualTo(BufferedImage.TYPE_3BYTE_BGR);
    assertThat(SessionFixtures.sameRgb(jpeg, out.get())).isTrue();

    FilterParams p = params();
    FilterParams conv = withKernel(p, ConvolutionKernel.EDGES);
    session.apply(spec(FilterType.CONVOLUTION, conv));
    session.export(out::set, failures::add);
    drain();
    BufferedImage expectedConv =
        PipelineSupport.reference(jpeg, List.of(new Step(FilterType.CONVOLUTION, conv)));
    assertThat(out.get().getType()).isEqualTo(expectedConv.getType());
    assertThat(SessionFixtures.sameRgb(expectedConv, out.get())).isTrue();

    session.apply(spec(FilterType.NEGATIVE, p));
    session.export(out::set, failures::add);
    drain();
    assertThat(out.get().getType()).isEqualTo(BufferedImage.TYPE_INT_ARGB);
    assertThat(failures).isEmpty();
  }

  @Test
  void exposureDiagnosticIsExactOnTheFullImage() {
    BufferedImage bi = SessionFixtures.image("INT_RGB", 3000, 2000, 5);
    session.setViewport(800, 600, 1, 1);
    session.load(SessionSource.of(bi));
    FilterParams p = params();
    OpSpec withMarks = spec(FilterType.HISTOGRAM_EQUALIZE, withMarks(p));
    OpSpec noMarks = spec(FilterType.HISTOGRAM_EQUALIZE, p);
    session.setPending(Optional.of(withMarks));
    AtomicReference<ExposureStats> stats = new AtomicReference<>();
    session.exposure(noMarks, stats::set, failures::add);
    drain();
    BufferedImage clean =
        PipelineSupport.reference(bi, List.of(new Step(FilterType.HISTOGRAM_EQUALIZE, p)));
    ExposureDiagnostic expected = ImageProcessor.diagnoseExposure(clean);
    assertThat(Analysis.describe(stats.get())).isEqualTo(expected);
    assertThat(stats.get().total()).isEqualTo(3000L * 2000);
  }

  @Test
  void exactPixelFromCacheOrFromAFullRender() {
    BufferedImage bi = SessionFixtures.image("INT_ARGB", 3000, 2000, 6);
    session.setViewport(800, 600, 1, 1);
    session.load(SessionSource.of(bi));
    drain();
    List<Integer> got = new ArrayList<>();
    session.exactPixel(5, 7, got::add, failures::add);
    assertThat(got).as("sin pasos: de la fuente").containsExactly(bi.getRGB(5, 7));
    FilterParams p = params();
    session.apply(spec(FilterType.NEGATIVE, p));
    drain();
    session.exactPixel(5, 7, got::add, failures::add);
    assertThat(got).as("aun no hay FULL").hasSize(1);
    drain();
    int expected =
        PipelineSupport.reference(bi, List.of(new Step(FilterType.NEGATIVE, p))).getRGB(5, 7);
    assertThat(got).containsExactly(bi.getRGB(5, 7), expected);
    session.exactPixel(6, 7, got::add, failures::add);
    assertThat(got).as("ya en cache").hasSize(3);
  }

  @Test
  void aChangeOfStateAbandonsThePendingExactPixel() {
    session.setViewport(800, 600, 1, 1);
    session.load(SessionSource.of(SessionFixtures.image("INT_ARGB", 3000, 2000, 6)));
    FilterParams p = params();
    session.apply(spec(FilterType.NEGATIVE, p));
    drain();
    List<Integer> got = new ArrayList<>();
    session.exactPixel(1, 1, got::add, failures::add);
    session.setPending(Optional.of(spec(FilterType.BRIGHTNESS, p)));
    drain();
    assertThat(got).isEmpty();
  }

  @Test
  void currentImageIsComputedOncePerStateAndKeepsTheChainType() {
    BufferedImage bi = SessionFixtures.image("3BYTE_BGR", 300, 200, 11);
    session.load(SessionSource.of(bi));
    drain();
    FilterParams p = params();
    session.apply(spec(FilterType.CONVOLUTION, withKernel(p, ConvolutionKernel.BLUR)));
    drain();
    List<BufferedImage> got = new ArrayList<>();
    session.withCurrentImage(got::add, failures::add);
    session.withCurrentImage(got::add, failures::add); // espera al mismo render
    drain();
    assertThat(got).hasSize(2);
    assertThat(got.get(0)).isSameAs(got.get(1));
    assertThat(got.get(0).getType()).isEqualTo(BufferedImage.TYPE_3BYTE_BGR);
    session.withCurrentImage(got::add, failures::add);
    assertThat(got).as("ya calculada: inmediato").hasSize(3);
  }

  @Test
  void waitersForTheCurrentImageAreServedForTheNewStateAfterAChange() {
    BufferedImage bi = SessionFixtures.image("INT_ARGB", 300, 200, 12);
    session.load(SessionSource.of(bi));
    drain();
    FilterParams p = params();
    List<BufferedImage> got = new ArrayList<>();
    session.withCurrentImage(got::add, failures::add);
    session.setPending(Optional.of(spec(FilterType.NEGATIVE, p)));
    drain();
    BufferedImage expected =
        PipelineSupport.reference(bi, List.of(new Step(FilterType.NEGATIVE, p)));
    assertThat(got).hasSize(1);
    assertThat(SessionFixtures.sameRgb(expected, got.get(0))).isTrue();
  }

  @Test
  void purposesDoNotSupersedeEachOther() {
    BufferedImage bi = SessionFixtures.image("INT_RGB", 300, 200, 13);
    session.load(SessionSource.of(bi));
    FilterParams p = params();
    OpSpec noMarks = spec(FilterType.HISTOGRAM_EQUALIZE, p);
    session.setPending(Optional.of(spec(FilterType.HISTOGRAM_EQUALIZE, withMarks(p))));
    drain();
    AtomicReference<ExposureDiagnostic> diag = new AtomicReference<>();
    List<BufferedImage> img = new ArrayList<>();
    session.exposure(noMarks, stats -> diag.set(Analysis.describe(stats)), failures::add);
    session.withCurrentImage(img::add, failures::add);
    drain();
    assertThat(diag.get()).as("el diagnostico no se pierde").isNotNull();
    assertThat(img).hasSize(1);
    assertThat(failures).isEmpty();
  }

  @Test
  void originalFrameUsesTheProxyForDisplayAndTheSourceForReadout() {
    BufferedImage bi = SessionFixtures.image("INT_RGB", 3000, 2000, 14);
    session.setViewport(800, 600, 1, 1);
    session.load(SessionSource.of(bi));
    drain();
    AtomicReference<EditSession.Frame<Img>> f = new AtomicReference<>();
    session.original(false, img -> img, f::set, failures::add);
    drain();
    assertThat(f.get().image().width()).isEqualTo(1024);
    assertThat(f.get().pixels().width()).as("cuentagotas exacto").isEqualTo(3000);
    assertThat(f.get().approximate()).isFalse();
    session.original(true, img -> img, f::set, failures::add);
    drain();
    assertThat(f.get().image().width()).isEqualTo(3000);
  }

  @Test
  void loadingAnotherImageInvalidatesTheOldCacheAndHistory() {
    session.load(SessionSource.of(SessionFixtures.image("INT_ARGB", 50, 40, 7)));
    session.apply(spec(FilterType.NEGATIVE, params()));
    drain();
    assertThat(session.renderer().cache().weightBytes()).isPositive();
    BufferedImage other = SessionFixtures.image("INT_ARGB", 20, 10, 8);
    session.load(SessionSource.of(other));
    assertThat(session.renderer().cache().weightBytes()).isZero();
    assertThat(session.canUndo()).isFalse();
    drain();
    assertThat(PipelineSupport.same(other, lastFrame().pixels())).isTrue();
  }

  @Test
  void sourceKeepsTheOriginalOnlyForLossyFormats() {
    assertThat(SessionSource.of(SessionFixtures.image("3BYTE_BGR", 4, 4, 1)).nativeImage())
        .isEmpty();
    assertThat(SessionSource.of(SessionFixtures.image("BYTE_GRAY", 4, 4, 1)).nativeImage())
        .isPresent();
    Img img = ImageBridge.toImg(SessionFixtures.image("INT_ARGB", 4, 4, 1));
    assertThat(img.width()).isEqualTo(4);
  }

  private static FilterParams withBrightness(FilterParams p, int b) {
    return rebuild(p, b, p.kernel(), p.equalizeMarkBurned(), p.equalizeMarkDark());
  }

  private static FilterParams withKernel(FilterParams p, ConvolutionKernel k) {
    return rebuild(p, p.brightness(), k, false, false);
  }

  private static FilterParams withMarks(FilterParams p) {
    return rebuild(p, p.brightness(), p.kernel(), true, true);
  }

  private static FilterParams rebuild(
      FilterParams p, int brightness, ConvolutionKernel k, boolean burned, boolean dark) {
    return new FilterParams(
        brightness,
        p.saturation(),
        p.valueFactor(),
        p.retroLevels(),
        p.retro2Levels(),
        p.r2r(),
        p.r2g(),
        p.r2b(),
        p.grayQuantLevels(),
        p.threshold(),
        p.alpha(),
        p.tintR(),
        p.tintG(),
        p.tintB(),
        p.stretchMode(),
        k,
        p.depthSource(),
        p.depthThreshold(),
        p.pixelBlockSize(),
        p.textureScale(),
        p.textureFilter(),
        p.alphaTestThreshold(),
        p.alphaTestLuminance(),
        p.multisampleSize(),
        p.stencilPattern(),
        p.fragmentBlendMode(),
        p.fragmentBlendAlpha(),
        p.logicOp(),
        p.logicMask(),
        p.planeR(),
        p.planeG(),
        p.planeB(),
        p.fogR(),
        p.fogG(),
        p.fogB(),
        p.equalizeMode(),
        p.gainR(),
        p.gainG(),
        p.gainB(),
        p.lerpFactor(),
        p.extrapolateFactor(),
        p.scale(),
        p.bias(),
        p.pointThreshold(),
        p.softThreshold(),
        p.pointSaturation(),
        p.hueDegrees(),
        p.colorSpace(),
        p.equalizeIntensity(),
        burned,
        dark);
  }
}
