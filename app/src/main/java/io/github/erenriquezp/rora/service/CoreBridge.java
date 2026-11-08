package io.github.erenriquezp.rora.service;

import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.OpResult;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.Params;
import java.awt.image.BufferedImage;
import java.util.function.UnaryOperator;

/**
 * Adaptador entre la fachada {@link ImageProcessor} (API de {@code BufferedImage}) y el núcleo.
 * Ejecuta de forma síncrona en un pool compartido de {@link TileExecutor#defaultParallelism()}
 * hilos daemon (el procesamiento fuera del hilo FX y la cancelación llegan en la fase 2).
 */
final class CoreBridge {

  /** Operaciones del núcleo, descubiertas con {@code ServiceLoader}. */
  static final OpRegistry REGISTRY = OpRegistry.load();

  private CoreBridge() {}

  /** Pool perezoso: se crea en el primer uso y vive lo que el proceso (hilos daemon). */
  private static final class Shared {
    static final TileExecutor EXECUTOR = TileExecutor.create(TileExecutor.defaultParallelism());
  }

  static OpContext defaultContext() {
    return OpContext.of(Shared.EXECUTOR);
  }

  static Operation op(String id) {
    return REGISTRY.require(id);
  }

  static Params params(String id, UnaryOperator<Params.Builder> values) {
    return values.apply(Params.builder(op(id).schema())).build();
  }

  static OpResult run(String id, Img src, Params params, OpContext ctx) {
    return op(id).apply(src, params, ctx);
  }

  /** Aplica {@code id} con el pool compartido. */
  static BufferedImage apply(String id, BufferedImage in, UnaryOperator<Params.Builder> values) {
    return apply(id, in, values, defaultContext());
  }

  /**
   * Aplica {@code id}. Si la operación devuelve su entrada sin cambios, devuelve <b>el mismo</b>
   * {@code in} (como hacía el legacy en esos casos).
   */
  static BufferedImage apply(
      String id, BufferedImage in, UnaryOperator<Params.Builder> values, OpContext ctx) {
    Img src = AwtImg.toImg(in);
    OpResult r = run(id, src, params(id, values), ctx);
    return r.image() == src ? in : AwtImg.toBufferedImage(r.image());
  }
}
