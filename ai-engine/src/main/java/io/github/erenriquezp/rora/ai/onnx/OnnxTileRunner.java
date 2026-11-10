package io.github.erenriquezp.rora.ai.onnx;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import io.github.erenriquezp.rora.ai.api.TileRunner;
import io.github.erenriquezp.rora.core.exec.CancellationToken;
import io.github.erenriquezp.rora.core.exec.CancelledException;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure;
import io.github.erenriquezp.rora.core.io.ImagingException.AiFailure.Reason;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/** Una sesión de ORT. La usa un solo hilo. */
final class OnnxTileRunner implements TileRunner {

  private static final long WATCH_NANOS = 2_000_000L;

  private final OrtEnvironment env;
  private final OrtSession session;
  private final String inputName;
  private final int scale;
  private final Runnable onRunStart;
  private FloatBuffer input;
  private boolean closed;

  OnnxTileRunner(OrtEnvironment env, OrtSession session, int scale, Runnable onRunStart)
      throws OrtException {
    this.env = env;
    this.session = session;
    this.inputName = session.getInputNames().iterator().next();
    this.scale = scale;
    this.onRunStart = onRunStart;
  }

  @Override
  public float[] run(float[] chw, int h, int w, CancellationToken token) throws AiFailure {
    token.throwIfCancelled();
    if (closed) {
      throw new AiFailure(Reason.FAILURE, "Sesión cerrada", null);
    }
    if (chw.length != 3 * h * w) {
      throw new AiFailure(Reason.SHAPE_MISMATCH, "Entrada de " + chw.length + " valores", null);
    }
    try (OrtSession.RunOptions options = new OrtSession.RunOptions();
        OnnxTensor in = OnnxTensor.createTensor(env, direct(chw), new long[] {1, 3, h, w})) {
      AtomicBoolean done = new AtomicBoolean();
      AtomicBoolean terminated = new AtomicBoolean();
      Thread watcher = watch(token, options, done, terminated);
      try (OrtSession.Result result = session.run(Map.of(inputName, in), options)) {
        return output(result, h, w);
      } catch (OrtException e) {
        if (terminated.get() || token.isCancelled()) {
          throw new CancelledException();
        }
        throw classifyRun(e);
      } finally {
        done.set(true);
        joinQuietly(watcher);
      }
    } catch (OrtException e) {
      throw classifyRun(e);
    } catch (OutOfMemoryError e) {
      throw new AiFailure(Reason.OUT_OF_MEMORY, "Sin memoria durante la inferencia", e);
    }
  }

  private Thread watch(
      CancellationToken token,
      OrtSession.RunOptions options,
      AtomicBoolean done,
      AtomicBoolean terminated) {
    onRunStart.run();
    return Thread.ofVirtual()
        .name("rora-ai-watch")
        .start(
            () -> {
              while (!done.get()) {
                if (token.isCancelled()) {
                  try {
                    options.setTerminate(true);
                    terminated.set(true);
                  } catch (OrtException e) {
                    // Sin efecto: el tile terminará y la cancelación se atenderá entre tiles.
                  }
                  return;
                }
                LockSupport.parkNanos(WATCH_NANOS);
              }
            });
  }

  private static void joinQuietly(Thread t) {
    boolean interrupted = false;
    while (true) {
      try {
        t.join();
        break;
      } catch (InterruptedException e) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private FloatBuffer direct(float[] chw) {
    if (input == null || input.capacity() != chw.length) {
      input =
          ByteBuffer.allocateDirect(chw.length * Float.BYTES)
              .order(ByteOrder.nativeOrder())
              .asFloatBuffer();
    }
    input.clear();
    input.put(chw);
    input.flip();
    return input;
  }

  private float[] output(OrtSession.Result result, int h, int w) throws OrtException, AiFailure {
    if (!(result.get(0) instanceof OnnxTensor t)) {
      throw new AiFailure(Reason.SHAPE_MISMATCH, "La salida no es un tensor", null);
    }
    long[] shape = t.getInfo().getShape();
    long[] expected = {1, 3, (long) h * scale, (long) w * scale};
    if (!Arrays.equals(shape, expected)) {
      throw new AiFailure(Reason.SHAPE_MISMATCH, "Salida " + Arrays.toString(shape), null);
    }
    FloatBuffer out = t.getFloatBuffer();
    float[] values = new float[out.remaining()];
    out.get(values);
    return values;
  }

  private static AiFailure classifyRun(OrtException e) {
    if (OnnxEngine.isMemory(e)) {
      return new AiFailure(Reason.OUT_OF_MEMORY, "Sin memoria durante la inferencia", e);
    }
    if (e.getCode() == OrtException.OrtErrorCode.ORT_INVALID_ARGUMENT) {
      return new AiFailure(Reason.SHAPE_MISMATCH, "El modelo rechazó la entrada", e);
    }
    return new AiFailure(Reason.FAILURE, "Fallo de ONNX Runtime", e);
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    try {
      session.close();
    } catch (OrtException e) {
      // Al cerrar no hay nada que recuperar.
    }
  }
}
