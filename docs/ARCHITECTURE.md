# Arquitectura

## Módulos (Maven multi-módulo, JPMS)

```
rora-parent (pom raíz: versiones, plugins, calidad)
├── core-imaging   módulo io.github.erenriquezp.rora.core — solo requiere java.base (ni JavaFX ni AWT)
├── app            módulo io.github.erenriquezp.rora.app  — JavaFX: UI, fachada ImageProcessor, puente AWT
└── bench          benchmarks JMH (perfil -Pbench, fuera del build por defecto, no se distribuye)
```

`app` depende de `core-imaging`. Un test (`ModuleAndServiceLoaderTest`) comprueba que el núcleo solo requiere `java.base` y que no exporta sus implementaciones.

### `core-imaging`

| Paquete | Contenido |
|---|---|
| `core.image` | `Img`: píxeles ARGB **no premultiplicados** en `int[]` (mismo formato que `BufferedImage.getRGB`), `stride == width`, sin setters públicos. `copyOf` copia, `adopt` toma posesión del array. |
| `core.op` | SPI: `Operation`, `ParamSchema`/`ParamDef` (entero, decimal, booleano, enum, color, selección; rango, defecto, neutro, clave de etiqueta), `Params`, `OpMeta`, `OpKind` (puntual / vecindad(radio, alineación) / global), `OpResult` (imagen + datos auxiliares como `ExposureStats` o `ChannelHistogram`), `OpContext`, `OpRegistry`, `BandOperation`. |
| `core.exec` | `TileExecutor` (pool propio, bandas de filas), `CancellationToken`, `CancelledException`, `Progress`. |
| `core.color` | `Hsb` (RGB↔HSB sin AWT, idéntico bit a bit a `java.awt.Color`), `SrgbLut` (sRGB↔lineal; aún no la usa ninguna operación). |
| `core.ops.*` | Implementaciones (no exportadas; se publican con `provides`). |
| `core.internal` | Acceso sin copia al array de `Img` dentro del módulo (no exportado). |

### `app`

- `app/`, `app/ui/`: JavaFX, sin cambios en esta fase.
- `service/ImageProcessor`: fachada estática que usa la UI. Sus firmas no cambian; las operaciones portadas delegan en el núcleo a través de `CoreBridge`.
- `service/AwtImg`: puente exacto `BufferedImage ↔ Img` (lee como `getRGB`, escribe `TYPE_INT_ARGB` como `setRGB`).
- `service/CoreBridge`: registro (`OpRegistry.load()`) y un `TileExecutor` compartido de N−1 hilos daemon.
- Implementaciones heredadas en `service/`: las portadas están `@Deprecated(forRemoval = true)` y solo las usan los tests diferenciales (se borran en la fase 3). Siguen en uso: convolución, blending de 2 y 3 imágenes, degradados y todas las operaciones de nivel C.

## Modelo de ejecución

- Una operación es **pura**: no modifica su entrada ni guarda estado; recibe `(Img, Params, OpContext)` y devuelve un `OpResult`.
- `TileExecutor` divide la imagen en **bandas de filas completas** (64 por defecto, alineadas al `rowAlign` del `OpKind`). La partición no depende del número de hilos, cada banda escribe filas disjuntas y las reducciones son enteras y se combinan en orden: **mismo resultado con cualquier número de hilos**.
- El halo (`OpKind.haloRows()`) es la ventana de lectura que una banda puede usar. El modo `TileExecutor.strictHalo` envenena las filas de fuera para comprobarlo en tests.
- La cancelación se comprueba antes de cada banda (`CancelledException`); el progreso se informa por banda, serializado y monótono.
- La fachada sigue siendo síncrona y se llama desde el hilo FX como antes; mover el trabajo fuera del hilo FX y cancelar desde la UI es la fase 2.

## Añadir una operación

1 clase + 1 línea:

```java
// core-imaging/src/main/java/io/github/erenriquezp/rora/core/ops/color/InvertGreenOp.java
public final class InvertGreenOp extends SimpleBandOp {
  public InvertGreenOp() {
    super("color.invert-green", ParamSchema.EMPTY, OpMeta.point());
  }

  @Override
  protected BandKernel prepare(Img src, Params params) {
    return (in, out, w, h, y0, y1) -> {
      for (int i = y0 * w, end = y1 * w; i < end; i++) {
        out[i] = in[i] ^ 0x0000FF00;
      }
    };
  }
}
```

```java
// core-imaging/src/main/java/module-info.java
provides io.github.erenriquezp.rora.core.op.Operation with
    ...,
    io.github.erenriquezp.rora.core.ops.color.InvertGreenOp;
```

`RegistryPropertiesTest` la cubre automáticamente (dimensiones, alpha, determinismo, hilos, halo, neutro, cancelación, progreso). Hay que añadir su id a la lista explícita de ese test y, si sustituye a una operación heredada, un caso en `PortedCases` (test diferencial).

## Formato de imagen y exactitud

- El núcleo trabaja en sRGB de 8 bits, igual que el código heredado. El pipeline en float lineal llegará en fases posteriores; `SrgbLut` ya está disponible y probado.
- Cada operación portada reproduce **exactamente** la aritmética heredada (mismos tipos, mismo orden de operaciones, mismos casos especiales, incluido devolver la misma instancia cuando el legacy lo hacía). La equivalencia se demuestra con tests, no se supone (ver `docs/TESTING.md`).

## Dirección prevista (fases siguientes)

- Fase 2: recetas (`EditRecipe`), `Renderer` con proxy de preview, `ResultCache`, procesamiento fuera del hilo FX con cancelación y debounce.
- Fase 3: UI generada desde `ParamSchema`; eliminación del código heredado portado.
- Motor de IA opcional en su propio módulo; distribución con `jlink` + `jpackage`.
