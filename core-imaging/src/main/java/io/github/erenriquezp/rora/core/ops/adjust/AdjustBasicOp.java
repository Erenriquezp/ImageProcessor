package io.github.erenriquezp.rora.core.ops.adjust;

import io.github.erenriquezp.rora.core.color.SrgbLut;
import io.github.erenriquezp.rora.core.exec.BandKernel;
import io.github.erenriquezp.rora.core.op.Cost;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.core.op.PointBandOperation;
import io.github.erenriquezp.rora.core.ops.LinearPack;
import java.util.OptionalDouble;

/**
 * Ajustes básicos de revelado en un solo paso: exposición, curva tonal, balance de blancos,
 * vibrance y saturación. Convierte a luz lineal una vez, trabaja en {@code float} y cuantiza una
 * vez al final; encadenar operaciones de 8 bits perdería luces y crearía bandas.
 *
 * <p>Una etapa con sus parámetros neutros no se ejecuta; con todos neutros la salida es la entrada,
 * exacta. La tabla tonal se calcula con {@link StrictMath} para no depender del JVM.
 */
public final class AdjustBasicOp extends PointBandOperation {

  public static final String ID = "adjust.basic";

  private static final float LUMA_R = 0.2126f;
  private static final float LUMA_G = 0.7152f;
  private static final float LUMA_B = 0.0722f;

  /** Entradas de la tabla tonal, indexada por {@code sqrt(Y)} para tener resolución en sombras. */
  static final int TONE_SIZE = 4096;

  /** Ganancia máxima por píxel; el resto se añade como gris para poder levantar negros puros. */
  private static final float MAX_GAIN = 64f;

  private static final double WB_STOPS = 0.6;
  private static final double BLACK_WHITE_RANGE = 0.15;
  // Fuerzas limitadas por la monotonía de la curva con las dos máscaras a la vez: con 0,25 la
  // pendiente mínima es positiva; hundir sombras (pendiente de la máscara 27/4 en 0) exige 0,14.
  private static final double HIGHLIGHT_AMOUNT = 0.25;
  private static final double SHADOW_LIFT_AMOUNT = 0.25;
  private static final double SHADOW_CRUSH_AMOUNT = 0.14;

  private static final ParamSchema SCHEMA =
      ParamSchema.of(
          new DecimalParam("exposure", label("exposure"), -5, 5, 0.01, 0, OptionalDouble.of(0)),
          percent("contrast"),
          percent("highlights"),
          percent("shadows"),
          percent("whites"),
          percent("blacks"),
          percent("temperature"),
          percent("tint"),
          percent("vibrance"),
          percent("saturation"));

  private static final OpMeta META = OpMeta.point().withCost(Cost.MODERATE);

  private static String label(String key) {
    return "op.adjust.basic." + key;
  }

  private static DecimalParam percent(String key) {
    return new DecimalParam(key, label(key), -100, 100, 1, 0, OptionalDouble.of(0));
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public ParamSchema schema() {
    return SCHEMA;
  }

  @Override
  public OpMeta meta() {
    return META;
  }

  @Override
  public BandKernel pointKernel(Params params, int width, int height) {
    double exposure = params.getDouble("exposure");
    double contrast = params.getDouble("contrast") / 100;
    double highlights = params.getDouble("highlights") / 100;
    double shadows = params.getDouble("shadows") / 100;
    double whites = params.getDouble("whites") / 100;
    double blacks = params.getDouble("blacks") / 100;
    double temperature = params.getDouble("temperature") / 100;
    double tint = params.getDouble("tint") / 100;
    float vibrance = (float) (params.getDouble("vibrance") / 100);
    float saturation = (float) (1 + params.getDouble("saturation") / 100);

    boolean balance = temperature != 0 || tint != 0;
    boolean tone =
        exposure != 0
            || contrast != 0
            || highlights != 0
            || shadows != 0
            || whites != 0
            || blacks != 0;
    boolean color = vibrance != 0 || saturation != 1f;
    if (!balance && !tone && !color) {
      return (in, out, w, h, y0, y1) -> System.arraycopy(in, y0 * w, out, y0 * w, (y1 - y0) * w);
    }

    float[] gains = whiteBalanceGains(temperature, tint);
    float gainR = gains[0];
    float gainG = gains[1];
    float gainB = gains[2];
    float[] toneTable =
        tone ? toneTable(exposure, contrast, highlights, shadows, whites, blacks) : null;

    return (in, out, w, h, y0, y1) -> {
      for (int i = y0 * w, end = y1 * w; i < end; i++) {
        int p = in[i];
        float r = SrgbLut.toLinear((p >> 16) & 0xFF);
        float g = SrgbLut.toLinear((p >> 8) & 0xFF);
        float b = SrgbLut.toLinear(p & 0xFF);
        if (balance) {
          r *= gainR;
          g *= gainG;
          b *= gainB;
        }
        float luma = LUMA_R * r + LUMA_G * g + LUMA_B * b;
        if (tone) {
          float target = applyTable(toneTable, luma);
          float gain = luma > 0 ? Math.min(target / luma, MAX_GAIN) : 1f;
          float lift = target - gain * luma;
          r = r * gain + lift;
          g = g * gain + lift;
          b = b * gain + lift;
          luma = target;
        }
        if (color) {
          float max = Math.max(r, Math.max(g, b));
          float min = Math.min(r, Math.min(g, b));
          float chroma = max > 0 ? (max - min) / max : 0f;
          float s = saturation * (1 + vibrance * (1 - Math.min(chroma, 1f)));
          r = luma + s * (r - luma);
          g = luma + s * (g - luma);
          b = luma + s * (b - luma);
        }
        out[i] = LinearPack.pack(p & 0xFF000000, r, g, b, luma);
      }
    };
  }

  /** Ganancias lineales normalizadas: un gris conserva su luminancia. */
  static float[] whiteBalanceGains(double temperature, double tint) {
    double r = StrictMath.pow(2, WB_STOPS * temperature);
    double g = StrictMath.pow(2, -WB_STOPS * tint);
    double b = StrictMath.pow(2, -WB_STOPS * temperature);
    double luma = LUMA_R * r + LUMA_G * g + LUMA_B * b;
    return new float[] {(float) (r / luma), (float) (g / luma), (float) (b / luma)};
  }

  /**
   * {@code Y' = tabla(Y)}, con la exposición dentro: las luces mayores de 1 se pueden recuperar.
   */
  static float[] toneTable(
      double exposure,
      double contrast,
      double highlights,
      double shadows,
      double whites,
      double blacks) {
    double multiplier = StrictMath.pow(2, exposure);
    float[] table = new float[TONE_SIZE + 2];
    for (int i = 0; i <= TONE_SIZE; i++) {
      double u = (double) i / TONE_SIZE;
      double t = encode(u * u * multiplier);
      table[i] = (float) decode(curve(t, contrast, highlights, shadows, whites, blacks));
    }
    table[TONE_SIZE + 1] = table[TONE_SIZE];
    return table;
  }

  private static float applyTable(float[] table, float luma) {
    float u = (float) Math.sqrt(Math.max(0f, luma)) * TONE_SIZE;
    int index = Math.min((int) u, TONE_SIZE);
    float fraction = u - index;
    return table[index] + fraction * (table[index + 1] - table[index]);
  }

  /** Curva monótona en el dominio perceptual: negros y blancos, sombras y luces, contraste. */
  static double curve(
      double t, double contrast, double highlights, double shadows, double whites, double blacks) {
    double blackPoint = -BLACK_WHITE_RANGE * blacks;
    double whitePoint = 1 - BLACK_WHITE_RANGE * whites;
    double v = (t - blackPoint) / (whitePoint - blackPoint);

    double m = Math.max(0, Math.min(1, v));
    double shadowMask = m * (1 - m) * (1 - m) * 27 / 4;
    double highlightMask = m >= 2.0 / 3 ? 1 : m * m * (1 - m) * 27 / 4;
    double shadowAmount = shadows >= 0 ? SHADOW_LIFT_AMOUNT : SHADOW_CRUSH_AMOUNT;
    v += shadowAmount * shadows * shadowMask + HIGHLIGHT_AMOUNT * highlights * highlightMask;

    double c = Math.max(0, Math.min(1, v));
    double smooth = c * c * (3 - 2 * c);
    v += contrast * (smooth - c);
    return Math.max(0, v);
  }

  /** sRGB extendido: la misma fórmula por encima de 1, para no perder las luces sobreexpuestas. */
  private static double encode(double linear) {
    return linear <= 0.0031308 ? linear * 12.92 : 1.055 * StrictMath.pow(linear, 1 / 2.4) - 0.055;
  }

  private static double decode(double encoded) {
    return encoded <= 0.04045 ? encoded / 12.92 : StrictMath.pow((encoded + 0.055) / 1.055, 2.4);
  }
}
