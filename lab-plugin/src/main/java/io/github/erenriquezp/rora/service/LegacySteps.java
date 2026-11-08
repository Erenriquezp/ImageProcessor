package io.github.erenriquezp.rora.service;

import io.github.erenriquezp.rora.app.imaging.SourceFormat;
import io.github.erenriquezp.rora.core.image.Img;
import io.github.erenriquezp.rora.core.op.BandOperation;
import io.github.erenriquezp.rora.core.op.Cost;
import io.github.erenriquezp.rora.core.op.OpContext;
import io.github.erenriquezp.rora.core.op.OpKind;
import io.github.erenriquezp.rora.core.op.OpMeta;
import io.github.erenriquezp.rora.core.op.OpRegistry;
import io.github.erenriquezp.rora.core.op.OpResult;
import io.github.erenriquezp.rora.core.op.Operation;
import io.github.erenriquezp.rora.core.op.ParamDef;
import io.github.erenriquezp.rora.core.op.ParamDef.BoolParam;
import io.github.erenriquezp.rora.core.op.ParamDef.ColorParam;
import io.github.erenriquezp.rora.core.op.ParamDef.DecimalParam;
import io.github.erenriquezp.rora.core.op.ParamDef.EnumParam;
import io.github.erenriquezp.rora.core.op.ParamDef.IntParam;
import io.github.erenriquezp.rora.core.op.ParamSchema;
import io.github.erenriquezp.rora.core.op.Params;
import io.github.erenriquezp.rora.domain.ColorSpaceType;
import io.github.erenriquezp.rora.domain.ConvolutionKernel;
import io.github.erenriquezp.rora.domain.DepthSource;
import io.github.erenriquezp.rora.domain.LogicOpType;
import io.github.erenriquezp.rora.domain.StencilPattern;
import io.github.erenriquezp.rora.domain.StretchMode;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.BiFunction;

/**
 * Operaciones heredadas que aún no están en el núcleo, envueltas como {@link Operation} para que
 * entren en una receta (fase 2). Cada paso convierte {@code Img → BufferedImage}, llama a la
 * fachada {@link ImageProcessor} con los mismos argumentos que la app y vuelve a {@code Img}.
 *
 * <p>Son {@link OpKind#GLOBAL} (no se trocean ni se fusionan) y <b>no son cancelables por
 * dentro</b>: el token se comprueba antes y después; un resultado obsoleto se descarta. Los ids son
 * {@code legacy.*}; la fase 3 los sustituye al portarlos.
 */
public final class LegacySteps {

  public static final String CONVOLUTION = "legacy.convolution";

  private LegacySteps() {}

  /** Los 15 pasos legacy; {@code nativeSource} da el original para {@code sourceFormat=native}. */
  public static List<Operation> all(SourceFormat.NativeSource nativeSource) {
    Objects.requireNonNull(nativeSource, "nativeSource");
    List<Operation> l = new ArrayList<>();
    l.add(step("legacy.frosted", schema(), (bi, p) -> ImageProcessor.frosted(bi)));
    l.add(
        step(
            "legacy.alpha-global",
            schema(intParam("alpha", 0, 255, 128)),
            (bi, p) -> ImageProcessor.alphaGlobal(bi, p.getInt("alpha"))));
    l.add(
        step(
            "legacy.stretch4",
            schema(enumParam("mode", StretchMode.values())),
            (bi, p) -> ImageProcessor.stretch4Bits(bi, decode(StretchMode.class, p, "mode"))));
    l.add(new ConvolutionStep(nativeSource));
    l.add(
        step(
            "legacy.z-buffer",
            schema(
                enumParam("source", DepthSource.values()),
                intParam("threshold", 0, 255, 128),
                colorParam("plane", 0xFF4FC3F7)),
            (bi, p) -> {
              int c = p.getColor("plane");
              return ImageProcessor.zBuffer(
                  bi,
                  decode(DepthSource.class, p, "source"),
                  p.getInt("threshold"),
                  r(c),
                  g(c),
                  b(c));
            }));
    l.add(
        step(
            "legacy.raster-grid",
            schema(
                IntParam.pixels(
                    "block", key("legacy.raster-grid", "block"), 2, 64, 8, OptionalInt.empty())),
            (bi, p) -> ImageProcessor.rasterGrid(bi, p.getInt("block"))));
    l.add(
        step(
            "legacy.depth-interpolate",
            schema(
                enumParam("source", DepthSource.values()),
                colorParam("near", 0xFF4FC3F7),
                colorParam("far", 0xFF1A237E)),
            (bi, p) -> {
              int n = p.getColor("near");
              int f = p.getColor("far");
              return ImageProcessor.depthInterpolate(
                  bi, decode(DepthSource.class, p, "source"), r(n), g(n), b(n), r(f), g(f), b(f));
            }));
    l.add(
        step(
            "legacy.w-buffer",
            schema(
                enumParam("source", DepthSource.values()),
                intParam("threshold", 0, 255, 128),
                colorParam("plane", 0xFF4FC3F7)),
            (bi, p) -> {
              int c = p.getColor("plane");
              return ImageProcessor.wBuffer(
                  bi,
                  decode(DepthSource.class, p, "source"),
                  p.getInt("threshold"),
                  r(c),
                  g(c),
                  b(c));
            }));
    l.add(
        step(
            "legacy.multisample",
            schema(
                IntParam.pixels(
                    "samples", key("legacy.multisample", "samples"), 2, 8, 3, OptionalInt.empty())),
            (bi, p) -> ImageProcessor.multisample(bi, p.getInt("samples"))));
    l.add(
        step(
            "legacy.alpha-test",
            schema(
                intParam("threshold", 0, 255, 128),
                new BoolParam(
                    "luminance", key("legacy.alpha-test", "luminance"), false, Optional.empty())),
            (bi, p) ->
                ImageProcessor.alphaTest(bi, p.getInt("threshold"), p.getBool("luminance"))));
    l.add(
        step(
            "legacy.stencil-test",
            schema(
                enumParam("pattern", StencilPattern.values()),
                intParam("threshold", 0, 255, 128),
                IntParam.pixels(
                    "cell", key("legacy.stencil-test", "cell"), 2, 64, 8, OptionalInt.empty())),
            (bi, p) ->
                ImageProcessor.stencilTest(
                    bi,
                    decode(StencilPattern.class, p, "pattern"),
                    p.getInt("threshold"),
                    p.getInt("cell"))));
    l.add(
        step(
            "legacy.logic-op",
            schema(enumParam("op", LogicOpType.values()), intParam("mask", 0, 255, 128)),
            (bi, p) ->
                ImageProcessor.logicOp(bi, decode(LogicOpType.class, p, "op"), p.getInt("mask"))));
    l.add(
        step(
            "legacy.point-interpolate",
            schema(decimalParam("t", 0, 1, 0.01, 0.5), colorParam("target", 0xFF4FC3F7)),
            (bi, p) -> {
              int c = p.getColor("target");
              return ImageProcessor.pointInterpolate(bi, p.getFloat("t"), r(c), g(c), b(c));
            }));
    l.add(
        step(
            "legacy.point-extrapolate",
            schema(decimalParam("t", -1, 2, 0.01, 1.5), colorParam("target", 0xFF4FC3F7)),
            (bi, p) -> {
              int c = p.getColor("target");
              return ImageProcessor.pointExtrapolate(bi, p.getFloat("t"), r(c), g(c), b(c));
            }));
    l.add(
        step(
            "legacy.color-space",
            schema(enumParam("type", ColorSpaceType.values())),
            (bi, p) ->
                ImageProcessor.colorSpaceConvert(bi, decode(ColorSpaceType.class, p, "type"))));
    return List.copyOf(l);
  }

  /** Registro con el núcleo ({@code ServiceLoader}) y los pasos legacy. */
  public static OpRegistry registryWithLegacy(SourceFormat.NativeSource nativeSource) {
    List<Operation> ops = new ArrayList<>(CoreBridge.REGISTRY.all());
    ops.addAll(all(nativeSource));
    return OpRegistry.of(ops);
  }

  // ── Construcción ──────────────────────────────────────────────────────────

  private static final OpMeta META = new OpMeta(OpKind.GLOBAL, true, false, Cost.EXPENSIVE, 255);

  private static Operation step(
      String id, ParamSchema schema, BiFunction<BufferedImage, Params, BufferedImage> fn) {
    return new LegacyStepOperation(id, schema, fn);
  }

  private static String key(String id, String param) {
    return "op." + id + "." + param;
  }

  private static ParamSchema schema(ParamDef... defs) {
    return ParamSchema.of(defs);
  }

  private static IntParam intParam(String k, int min, int max, int def) {
    return new IntParam(k, "op.legacy." + k, min, max, def, OptionalInt.empty());
  }

  private static DecimalParam decimalParam(
      String k, double min, double max, double step, double def) {
    return new DecimalParam(k, "op.legacy." + k, min, max, step, def, OptionalDouble.empty());
  }

  private static ColorParam colorParam(String k, int def) {
    return new ColorParam(k, "op.legacy." + k, def, false, OptionalInt.empty());
  }

  private static EnumParam enumParam(String k, Enum<?>[] values) {
    List<String> ids = Arrays.stream(values).map(LegacySteps::enumId).toList();
    return new EnumParam(k, "op.legacy." + k, ids, ids.get(0), Optional.empty());
  }

  /** {@code SRC_OVER → "src-over"} (mismo criterio que {@link CoreBridge#enumId}). */
  public static String enumId(Enum<?> e) {
    return CoreBridge.enumId(e);
  }

  static <E extends Enum<E>> E decode(Class<E> type, Params p, String key) {
    return Enum.valueOf(type, p.getEnum(key).toUpperCase(Locale.ROOT).replace('-', '_'));
  }

  private static int r(int c) {
    return (c >> 16) & 0xFF;
  }

  private static int g(int c) {
    return (c >> 8) & 0xFF;
  }

  private static int b(int c) {
    return c & 0xFF;
  }

  // ── Operaciones ───────────────────────────────────────────────────────────

  /** Paso legacy genérico: {@code Img → INT_ARGB → fachada → Img}. */
  static class LegacyStepOperation implements Operation {
    private final String id;
    private final ParamSchema schema;
    private final BiFunction<BufferedImage, Params, BufferedImage> fn;

    LegacyStepOperation(
        String id, ParamSchema schema, BiFunction<BufferedImage, Params, BufferedImage> fn) {
      this.id = id;
      this.schema = schema;
      this.fn = fn;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public ParamSchema schema() {
      return schema;
    }

    @Override
    public OpMeta meta() {
      return META;
    }

    BufferedImage input(Img src, Params params) {
      return AwtImg.toBufferedImage(src);
    }

    @Override
    public OpResult apply(Img src, Params params, OpContext ctx) {
      Objects.requireNonNull(src, "src");
      BandOperation.checkSchema(this, params);
      ctx.token().throwIfCancelled();
      BufferedImage out = fn.apply(input(src, params), params);
      ctx.token().throwIfCancelled();
      return OpResult.of(AwtImg.toImg(out));
    }
  }

  /** Convolución 3×3 legacy con el formato de origen oculto {@code sourceFormat}. */
  static final class ConvolutionStep extends LegacyStepOperation {
    private final SourceFormat.NativeSource nativeSource;

    ConvolutionStep(SourceFormat.NativeSource nativeSource) {
      super(
          CONVOLUTION,
          ParamSchema.of(
              enumParam("kernel", ConvolutionKernel.values()),
              new EnumParam(
                  "sourceFormat",
                  key(CONVOLUTION, "sourceFormat"),
                  SourceFormat.IDS,
                  SourceFormat.ARGB,
                  Optional.empty(),
                  true)),
          (bi, p) -> ImageProcessor.convolution(bi, decode(ConvolutionKernel.class, p, "kernel")));
      this.nativeSource = nativeSource;
    }

    @Override
    BufferedImage input(Img src, Params params) {
      return SourceFormat.materialize(src, params.getEnum("sourceFormat"), nativeSource);
    }
  }
}
