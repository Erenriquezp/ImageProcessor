package io.github.erenriquezp.rora.app.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.OpSpec;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.op.Renderer;
import io.github.erenriquezp.rora.domain.ConvolutionKernel;
import io.github.erenriquezp.rora.domain.FilterType;
import io.github.erenriquezp.rora.service.ImageBridge;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Memoria con una imagen de 24 MP (fase 2). Solo corre en la ejecución {@code memory} de surefire
 * (JVM aparte con {@code -Xmx1536m}); saltable con {@code -Dskip.memory=true}. Escribe un informe
 * en {@code target/memory-report.txt}.
 *
 * <p>Escenario: 6000×4000 {@code 3BYTE_BGR} → sesión → receta de 10 pasos (núcleo y legacy) con
 * preview tras cada cambio → FULL → exportación PNG → historial de 35 pasos con 35 deshacer y 35
 * rehacer.
 */
@Tag("memory")
class SessionMemoryTest {

  private static final long MB = 1L << 20;

  private final List<String> report = new ArrayList<>();
  private long maxLive;

  /** Heap vivo tras un GC completo (proceso aislado: forzar el GC es aceptable aquí). */
  private long live(String label) {
    System.gc();
    System.gc();
    Runtime rt = Runtime.getRuntime();
    long used = rt.totalMemory() - rt.freeMemory();
    maxLive = Math.max(maxLive, used);
    report.add(String.format(java.util.Locale.ROOT, "%-40s %6d MB", label, used / MB));
    return used;
  }

  private static BufferedImage synthetic24mp() {
    BufferedImage bi = new BufferedImage(6000, 4000, BufferedImage.TYPE_3BYTE_BGR);
    byte[] data = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
    SplittableRandom rnd = new SplittableRandom(0x5EED_0090L);
    for (int i = 0; i < data.length; i += 8) {
      long v = rnd.nextLong();
      for (int k = 0; k < 8 && i + k < data.length; k++) {
        data[i + k] = (byte) (v >>> (8 * k));
      }
    }
    return bi;
  }

  @Test
  @Timeout(600)
  void session24mpTenStepsFullExportAndHistory(@TempDir Path tmp) throws Exception {
    long xmx = Runtime.getRuntime().maxMemory();
    report.add("maxMemory (Xmx)                          " + xmx / MB + " MB");
    report.add("JVM: " + System.getProperty("java.vm.version"));
    LinkedBlockingQueue<Runnable> fx = new LinkedBlockingQueue<>();
    List<RenderFailure> failures = new ArrayList<>();
    try (EditSession<Img> session = EditSession.create(fx::add, img -> img, true)) {
      session.addListener(
          new EditSession.Listener<>() {
            @Override
            public void failed(RenderFailure f) {
              failures.add(f);
            }

            @Override
            public void frameReady(EditSession.Frame<Img> frame) {
              CompletableFuture<EditSession.Frame<Img>> f = slot.getAndSet(null);
              if (f != null) {
                f.complete(frame);
              }
            }
          });
      // Hilo "FX" simulado: el test ejecuta lo publicado hasta que se cumple la condición.
      Pump pump = new Pump(fx);
      session.setViewport(1600, 900, 1.0, 1.0);

      live("antes de cargar");
      SessionSource src = SessionSource.of(synthetic24mp()); // el BufferedImage se suelta aquí
      session.load(src);
      CompletableFuture<EditSession.Frame<Img>> first = nextFrame(session);
      pump.until(first::isDone);
      assertThat(first.get().approximate()).isTrue();
      report.add("proxy                                    " + session.proxySize().orElseThrow());
      live("cargada (fuente + proxy)");

      FilterParams p = SessionFixtures.randomParams(new SplittableRandom(3)).withNoHighlights();
      List<OpSpec> steps = tenSteps(session, p);
      for (OpSpec s : steps) {
        CompletableFuture<EditSession.Frame<Img>> f1 = nextFrame(session);
        session.setPending(Optional.of(s));
        pump.until(f1::isDone);
        CompletableFuture<EditSession.Frame<Img>> f2 = nextFrame(session);
        session.apply(s);
        pump.until(f2::isDone);
      }
      assertThat(session.undoDepth()).isEqualTo(10);
      live("receta de 10 pasos (preview)");

      CompletableFuture<Renderer.Result> full = new CompletableFuture<>();
      session.full(null, List.of(), r -> r, full::complete, failures::add);
      pump.until(full::isDone);
      assertThat(full.get().image().width()).isEqualTo(6000);
      live("FULL de 10 pasos");
      full = null;

      CompletableFuture<BufferedImage> exported = new CompletableFuture<>();
      session.export(exported::complete, failures::add);
      pump.until(exported::isDone);
      File out = tmp.resolve("export.png").toFile();
      ImageIo.save(exported.get(), out);
      assertThat(Files.size(out.toPath())).isPositive();
      exported = null;
      live("exportado a PNG");

      // Historial: 25 pasos más (35 en total), deshacer y rehacer todo con preview.
      session.renderer().cache().clear();
      report.add("publicaciones pendientes en la cola FX    " + fx.size());
      fx.clear();
      long beforeHistory = live("antes del historial (caché vacía)");
      Operation bright = session.registry().require("color.brightness");
      for (int i = 0; i < 25; i++) {
        OpSpec s = OpSpec.of(bright, Params.builder(bright.schema()).set("value", i - 12).build());
        CompletableFuture<EditSession.Frame<Img>> f = nextFrame(session);
        session.apply(s);
        pump.until(f::isDone);
      }
      assertThat(session.undoDepth()).isEqualTo(35);
      for (int i = 0; i < 35; i++) {
        CompletableFuture<EditSession.Frame<Img>> f = nextFrame(session);
        session.undo();
        pump.until(f::isDone);
      }
      assertThat(session.canUndo()).isFalse();
      for (int i = 0; i < 35; i++) {
        CompletableFuture<EditSession.Frame<Img>> f = nextFrame(session);
        session.redo();
        pump.until(f::isDone);
      }
      assertThat(session.undoDepth()).isEqualTo(35);
      live("35 deshacer + 35 rehacer");
      session.renderer().cache().clear();
      fx.clear();
      long afterHistory = live("historial de 35 pasos (caché vacía)");
      long historyBytes = afterHistory - beforeHistory;
      report.add("crecimiento por el historial             " + historyBytes / 1024 + " KB");
      report.add(
          "caché: presupuesto "
              + session.renderer().cache().budgetBytes() / MB
              + " MB; estadísticas "
              + session.renderer().cache().stats());
      assertThat(failures).isEmpty();
      assertThat(historyBytes).as("el historial no guarda imágenes").isLessThan(4 * MB);
    } finally {
      report.add(
          "heap comprometido al final               "
              + Runtime.getRuntime().totalMemory() / MB
              + " MB");
      report.add("máximo vivo tras GC                      " + maxLive / MB + " MB");
      Path target = Path.of(System.getProperty("user.dir"), "target", "memory-report.txt");
      Files.createDirectories(target.getParent());
      Files.write(target, report);
      report.forEach(System.out::println);
    }
    assertThat(maxLive).as("meta del roadmap: < 1,5 GB").isLessThan(1536 * MB);
  }

  /**
   * Decisión 3 de la fase 2: heap vivo del panel de blending con 24 MP, publicando a la interfaz la
   * imagen completa o una copia de 4 MP. Las {@code WritableImage} se emulan con {@code int[]} del
   * mismo tamaño (el {@code FramePool} retiene hasta 3 por vista).
   */
  @Test
  @Timeout(600)
  void blendingPanelDisplayCopy() throws Exception {
    BufferedImage fg = ImageBridge.toBufferedImage(ImageBridge.toImg(synthetic24mp()));
    BufferedImage bg = synthetic24mp();
    long full = blendLive(fg, bg, 0);
    long copy = blendLive(fg, bg, PaneWork.DISPLAY_MAX_PIXELS);
    long xmx = Runtime.getRuntime().maxMemory();
    List<String> lines =
        List.of(
            "blending 24 MP, imagen completa en la UI: " + full / MB + " MB vivos",
            "blending 24 MP, copia de 4 MP en la UI:   " + copy / MB + " MB vivos",
            "25 % del heap (umbral de la decisión 3):  " + xmx / 4 / MB + " MB");
    Path target = Path.of(System.getProperty("user.dir"), "target", "memory-blend-report.txt");
    Files.createDirectories(target.getParent());
    Files.write(target, lines);
    lines.forEach(System.out::println);
    assertThat(copy).as("la copia reduce la memoria").isLessThan(full);
  }

  private long blendLive(BufferedImage fg, BufferedImage bg, long maxPixels) {
    long max = 0;
    int[][] fx = new int[3][];
    BufferedImage exact = null;
    for (int tick = 0; tick < 4; tick++) {
      exact = PaneWork.blend(fg, bg, 0.2f + tick * 0.1f);
      Img shown = PaneWork.displayCopy(exact, maxPixels, CancellationToken.none());
      fx[tick % 3] = shown.toArray();
      System.gc();
      System.gc();
      Runtime rt = Runtime.getRuntime();
      max = Math.max(max, rt.totalMemory() - rt.freeMemory());
    }
    assertThat(exact.getWidth()).isEqualTo(6000);
    assertThat(fx[0]).isNotNull();
    return max;
  }

  private static List<OpSpec> tenSteps(EditSession<Img> s, FilterParams p) {
    FilterSpecs fs = s.specs();
    List<OpSpec> l = new ArrayList<>();
    l.add(fs.of(FilterType.BRIGHTNESS, p).orElseThrow());
    l.add(fs.of(FilterType.POINT_SATURATION, p).orElseThrow());
    l.add(fs.of(FilterType.BITMAP_PIXELATE, p).orElseThrow());
    l.add(fs.of(FilterType.HISTOGRAM_EQUALIZE, p).orElseThrow());
    l.add(fs.of(FilterType.HUE_ROTATE, p).orElseThrow());
    l.add(fs.of(FilterType.FROSTED, p).orElseThrow());
    l.add(fs.of(FilterType.SCALE_BIAS, p).orElseThrow());
    l.add(fs.of(FilterType.CONVOLUTION, withKernel(p)).orElseThrow());
    l.add(fs.of(FilterType.COLOR_ADJUST, p).orElseThrow());
    l.add(fs.of(FilterType.RETRO1, p).orElseThrow());
    return l;
  }

  private static FilterParams withKernel(FilterParams p) {
    return new FilterParams(
        p.brightness(),
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
        ConvolutionKernel.SHARPEN,
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
        false,
        false);
  }

  /** Ranura del próximo frame: un único oyente, sin retener frames ya entregados. */
  private final java.util.concurrent.atomic.AtomicReference<
          CompletableFuture<EditSession.Frame<Img>>>
      slot = new java.util.concurrent.atomic.AtomicReference<>();

  private CompletableFuture<EditSession.Frame<Img>> nextFrame(EditSession<Img> s) {
    CompletableFuture<EditSession.Frame<Img>> f = new CompletableFuture<>();
    slot.set(f);
    return f;
  }

  /** Ejecuta en el hilo del test lo que la sesión publica (hace de hilo FX). */
  private record Pump(LinkedBlockingQueue<Runnable> queue) {
    void until(Supplier<Boolean> done) throws InterruptedException {
      while (!done.get()) {
        Runnable r = queue.poll(120, TimeUnit.SECONDS);
        if (r == null) {
          throw new AssertionError("Sin publicaciones en 120 s");
        }
        r.run();
      }
    }
  }
}
