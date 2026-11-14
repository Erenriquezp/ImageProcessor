package io.github.erenriquezp.rora.ai.onnx;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import io.github.erenriquezp.rora.ai.api.AiEngine;
import io.github.erenriquezp.rora.ai.api.ModelSource;
import io.github.erenriquezp.rora.ai.api.TileRunner;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure.Reason;
import java.util.Locale;

/**
 * Motor local en CPU sobre ONNX Runtime. Sin arena ni espera activa (medido: pico de 218 MB por
 * sesión), un hilo entre operadores y patrón de memoria activado.
 */
public final class OnnxEngine implements AiEngine {

  /** Se invoca antes de que empiece la vigilancia de cancelación; solo para pruebas. */
  private final Runnable onRunStart;

  public OnnxEngine() {
    this(() -> {});
  }

  OnnxEngine(Runnable onRunStart) {
    this.onRunStart = onRunStart;
  }

  @Override
  public TileRunner open(ModelSource model, int threads) throws AiFailure {
    OrtNative.prepare();
    byte[] bytes = model.readVerified();
    try {
      OrtEnvironment env = OrtEnvironments.quiet();
      try (OrtSession.SessionOptions o = new OrtSession.SessionOptions()) {
        o.setIntraOpNumThreads(threads);
        o.setInterOpNumThreads(1);
        o.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        o.setMemoryPatternOptimization(true);
        o.setCPUArenaAllocator(false);
        o.addConfigEntry("session.intra_op.allow_spinning", "0");
        OrtSession session = env.createSession(bytes, o);
        try {
          checkInput(session);
        } catch (AiFailure e) {
          session.close();
          throw e;
        }
        return new OnnxTileRunner(env, session, model.info().scale(), onRunStart);
      }
    } catch (OrtException e) {
      throw classifyOpen(e);
    } catch (UnsatisfiedLinkError | ExceptionInInitializerError | NoClassDefFoundError e) {
      throw new AiFailure(Reason.ENGINE_UNAVAILABLE, "No se pudo cargar ONNX Runtime", e);
    } catch (OutOfMemoryError e) {
      throw new AiFailure(Reason.OUT_OF_MEMORY, "Sin memoria al crear la sesión", e);
    }
  }

  private static void checkInput(OrtSession session) throws OrtException, AiFailure {
    if (session.getInputInfo().size() != 1) {
      throw new AiFailure(Reason.SHAPE_MISMATCH, "Se esperaba una sola entrada", null);
    }
    NodeInfo in = session.getInputInfo().values().iterator().next();
    if (!(in.getInfo() instanceof TensorInfo t)
        || t.getShape().length != 4
        || t.getShape()[1] != 3) {
      throw new AiFailure(Reason.SHAPE_MISMATCH, "Entrada inesperada: " + in, null);
    }
  }

  private static AiFailure classifyOpen(OrtException e) {
    if (isMemory(e)) {
      return new AiFailure(Reason.OUT_OF_MEMORY, "Sin memoria al crear la sesión", e);
    }
    return new AiFailure(Reason.MODEL_CORRUPT, "ONNX Runtime rechazó el modelo", e);
  }

  /** ORT notifica la falta de memoria nativa como error genérico: solo queda leer el mensaje. */
  static boolean isMemory(OrtException e) {
    String m = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
    return m.contains("bad allocation") || m.contains("bad_alloc") || m.contains("out of memory");
  }
}
