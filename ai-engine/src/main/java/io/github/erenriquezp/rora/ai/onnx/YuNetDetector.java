package io.github.erenriquezp.rora.ai.onnx;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtLoggingLevel;
import ai.onnxruntime.OrtSession;
import io.github.erenriquezp.rora.ai.face.YuNetDecoder;
import io.github.erenriquezp.rora.ai.face.YuNetModel;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.TileExecutor;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure.Reason;
import io.github.erenriquezp.rora.wallpapers.subject.Subject;
import io.github.erenriquezp.rora.wallpapers.subject.SubjectDetector;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * YuNet sobre ORT. La sesión se abre en la primera detección y usa un solo hilo: así el orden de
 * las sumas no cambia y el resultado es el mismo en cada ejecución.
 */
public final class YuNetDetector implements SubjectDetector {

  private OrtSession session;
  private String inputName;
  private boolean closed;

  @Override
  public String id() {
    return YuNetModel.ID;
  }

  @Override
  public synchronized List<Subject> detect(Img image, CancellationToken token) throws AiFailure {
    token.throwIfCancelled();
    if (closed) {
      throw new AiFailure(Reason.FAILURE, "Detector cerrado", null);
    }
    open();
    YuNetDecoder.Letterbox lb = YuNetDecoder.input(image, TileExecutor.sequential(), token);
    token.throwIfCancelled();
    OrtEnvironment env = OrtEnvironment.getEnvironment();
    try (OnnxTensor in =
            OnnxTensor.createTensor(
                env, direct(lb.chw()), new long[] {1, 3, YuNetDecoder.SIZE, YuNetDecoder.SIZE});
        OrtSession.Result r = session.run(Map.of(inputName, in))) {
      Map<String, float[]> outputs = new HashMap<>();
      for (Map.Entry<String, ai.onnxruntime.OnnxValue> e : r) {
        outputs.put(e.getKey(), flat(e.getValue().getValue()));
      }
      return YuNetDecoder.decode(
          name -> {
            float[] v = outputs.get(name);
            if (v == null) {
              throw new IllegalArgumentException("falta la salida " + name);
            }
            return v;
          },
          lb.contentWidth(),
          lb.contentHeight());
    } catch (OrtException e) {
      throw OnnxEngine.isMemory(e)
          ? new AiFailure(Reason.OUT_OF_MEMORY, "Sin memoria al detectar caras", e)
          : new AiFailure(Reason.FAILURE, "Fallo al detectar caras", e);
    } catch (IllegalArgumentException e) {
      throw new AiFailure(Reason.SHAPE_MISMATCH, e.getMessage(), e);
    } catch (OutOfMemoryError e) {
      throw new AiFailure(Reason.OUT_OF_MEMORY, "Sin memoria al detectar caras", e);
    }
  }

  private void open() throws AiFailure {
    if (session != null) {
      return;
    }
    OrtNative.prepare();
    byte[] bytes = YuNetModel.readVerified();
    try {
      OrtEnvironment env =
          OrtEnvironment.getEnvironment(OrtLoggingLevel.ORT_LOGGING_LEVEL_FATAL, "rora");
      try (OrtSession.SessionOptions o = new OrtSession.SessionOptions()) {
        o.setIntraOpNumThreads(1);
        o.setInterOpNumThreads(1);
        o.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        o.setCPUArenaAllocator(false);
        o.addConfigEntry("session.intra_op.allow_spinning", "0");
        session = env.createSession(bytes, o);
        inputName = session.getInputNames().iterator().next();
      }
    } catch (OrtException e) {
      throw OnnxEngine.isMemory(e)
          ? new AiFailure(Reason.OUT_OF_MEMORY, "Sin memoria al crear la sesión", e)
          : new AiFailure(Reason.MODEL_CORRUPT, "ONNX Runtime rechazó el modelo", e);
    } catch (UnsatisfiedLinkError | ExceptionInInitializerError | NoClassDefFoundError e) {
      throw new AiFailure(Reason.ENGINE_UNAVAILABLE, "No se pudo cargar ONNX Runtime", e);
    }
  }

  private static FloatBuffer direct(float[] v) {
    FloatBuffer b =
        ByteBuffer.allocateDirect(v.length * Float.BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
    b.put(v).flip();
    return b;
  }

  static float[] flat(Object v) {
    if (v instanceof float[] f) {
      return f;
    }
    if (v instanceof float[][] f2) {
      int n = f2.length;
      int m = n == 0 ? 0 : f2[0].length;
      float[] out = new float[n * m];
      for (int i = 0; i < n; i++) {
        System.arraycopy(f2[i], 0, out, i * m, m);
      }
      return out;
    }
    if (v instanceof float[][][] f3 && f3.length == 1) {
      return flat(f3[0]);
    }
    throw new IllegalArgumentException("salida de tipo inesperado: " + v.getClass().getName());
  }

  @Override
  public synchronized void close() {
    closed = true;
    if (session != null) {
      try {
        session.close();
      } catch (OrtException e) {
        // nada que hacer al cerrar
      }
      session = null;
    }
  }
}
