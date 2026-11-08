# Arquitectura

## Módulos (Maven multi-módulo, JPMS)

```
rora-parent (pom raíz: versiones, plugins, calidad)
├── core-imaging   módulo ...rora.core — solo requiere java.base (ni JavaFX ni AWT); --release 21
├── imaging-io     módulo ...rora.imaging.io — códecs sobre ImageIO; --release 21, sin JavaFX
├── app            módulo ...rora.app  — JavaFX 25: interfaz, sesión, puente AWT; --release 25
├── lab-plugin     módulo ...rora.lab  — opcional: didácticas, generador, blending y código heredado
└── bench          benchmarks JMH (perfil -Pbench, fuera del build por defecto, no se distribuye)
```

`app` depende de `core-imaging`; `lab-plugin` depende de los dos y `app` no lo conoce: lo descubre con `ServiceLoader` (`LabBundle`) si el módulo está resuelto. Un test (`ModuleAndServiceLoaderTest`) comprueba que el núcleo solo requiere `java.base` y que no exporta sus implementaciones.

### `core-imaging`

| Paquete | Contenido |
|---|---|
| `core.image` | `Downscale` (promedio por área con alpha ponderado), `ProxyPolicy`. `Img`: píxeles ARGB **no premultiplicados** en `int[]` (mismo formato que `BufferedImage.getRGB`), `stride == width`, sin setters públicos. `copyOf` copia, `adopt` toma posesión del array. |
| `core.op` | Receta y render: `OpSpec`, `EditRecipe`, `RecipeHistory`, `Renderer`, `ResultCache`, `RenderKey`, `PointFusion`. SPI: `Operation`, `ParamSchema`/`ParamDef` (entero, decimal, booleano, enum, color, selección; rango, defecto, neutro, clave de etiqueta), `Params`, `OpMeta`, `OpKind` (puntual / vecindad(radio, alineación) / global), `OpResult` (imagen + datos auxiliares como `ExposureStats` o `ChannelHistogram`), `OpContext`, `OpRegistry`, `BandOperation`. |
| `core.exec` | `TileExecutor` (pool propio, bandas de filas), `CancellationToken`, `CancelledException`, `Progress`. |
| `core.color` | `Hsb` (RGB↔HSB sin AWT, idéntico bit a bit a `java.awt.Color`), `SrgbLut` (sRGB↔lineal). |
| `core.io` | SPI `ImageDecoder`/`ImageEncoder`, `ExportSettings`, `ImagingException`, y la lógica pura de E/S: orientación, aplanado, EXIF, política de metadatos, nombres, presupuesto de memoria. |
| `core.ops.*` | Implementaciones (no exportadas; se publican con `provides`). |
| `core.internal` | Acceso sin copia al array de `Img` dentro del módulo (no exportado). |

### `app`

Paquetes de interfaz en [`docs/UI.md`](UI.md).

- `app/session/` (sin JavaFX, se prueba en headless): `EditSession` (fuente, receta, historial, proxy), `RenderLane`/`RenderCoordinator`, `LatestWins`, `FramePool`, `StallMonitor`.
- `app/imaging/`: puente exacto `BufferedImage ↔ Img` (`AwtImg`, `ImageBridge`), lectura y escritura heredadas (`ImageIOService`, `@Deprecated`: solo Laboratorio y tests diferenciales) y formato de origen (`SourceFormat`).
- `app/lab/`: SPI del Laboratorio.

### `imaging-io`

`ImageIoDecoder`, `JpegEncoder`, `PngEncoder`, `TiffEncoder`, `Exporter` (reducción, peso objetivo, metadatos y publicación atómica) y `Codecs` (elige lector y escritor por clase y publica el SPI). La interfaz solo llega aquí a través de `app.session.StudioIo`; ArchUnit lo comprueba. Detalle en [`docs/IO.md`](IO.md).

### `lab-plugin`

- `lab/`: el `LabBundle` y los paneles de Generador, Blending y Triple Blending.
- `service/`: fachada `ImageProcessor`, los 15 pasos `legacy.*` (`LegacySteps`) e implementaciones heredadas. Las portadas siguen `@Deprecated(forRemoval)` y solo las usan los tests diferenciales: varias clases mezclan métodos portados con otros en uso, por eso están aquí y no en `src/test`.
- `domain/`: enums de esas operaciones.

## Modelo de ejecución

- Una operación es **pura**: no modifica su entrada ni guarda estado; recibe `(Img, Params, OpContext)` y devuelve un `OpResult`.
- `TileExecutor` divide la imagen en **bandas de filas completas** (64 por defecto, alineadas al `rowAlign` del `OpKind`). La partición no depende del número de hilos, cada banda escribe filas disjuntas y las reducciones son enteras y se combinan en orden: **mismo resultado con cualquier número de hilos**.
- El halo (`OpKind.haloRows()`) es la ventana de lectura que una banda puede usar. El modo `TileExecutor.strictHalo` envenena las filas de fuera para comprobarlo en tests.
- La cancelación se comprueba antes de cada banda (`CancelledException`); el progreso se informa por banda, serializado y monótono.
- La fachada sigue siendo síncrona, pero ya solo se llama desde hilos de trabajo.

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

- Las operaciones portadas trabajan en sRGB de 8 bits, igual que el código heredado. Las nuevas (`adjust.basic`, `blur.gaussian`, `detail.unsharp-mask`) trabajan en luz lineal con `SrgbLut`.
- Cada operación portada reproduce **exactamente** la aritmética heredada (mismos tipos, mismo orden de operaciones, mismos casos especiales, incluido devolver la misma instancia cuando el legacy lo hacía). La equivalencia se demuestra con tests, no se supone (ver `docs/TESTING.md`).

## Pipeline no destructivo (fase 2)

**Receta.** `EditRecipe` es una lista inmutable de `OpSpec(opId, Params, enabled)`. `RecipeHistory` guarda recetas, nunca imágenes (tope 200). `Params` tiene igualdad por valor y forma canónica estable; el mapeo de la UI pasa el mismo `float` que recibía la fachada, así que dos posiciones de slider que acaban en el mismo `float` son la misma clave. "Aplicar filtro" añade un paso; el paso pendiente (lo que muestran los sliders) no entra en el historial.

**Renderer.** Niveles `FULL` (exacto, idéntico bit a bit a encadenar los filtros con la fachada) y `Preview` (proxy reducido, aproximado por diseño). Reanuda desde el prefijo cacheado más largo y fusiona tramos de operaciones puntuales consecutivas (`PointFusion`: los mismos kernels, banda a banda). Cachea solo puntos de control: tras cada paso no puntual, la cabeza confirmada y el resultado final; un render cancelado no deja entradas.

**Caché.** `ResultCache`: LRU en bytes (`maxMemory/4`, entre 64 MB y 1 GB; `-Drora.cache.mb`), clave estructural encadenada (fuente + nivel + pasos), peso contado una vez por instancia de `Img`. Un `OutOfMemoryError` en un render la vacía y se informa en la barra de estado.

**Proxy.** Lado ≈ visor × DPI × zoom en múltiplos de 256 px, tope de 4 MP, sin proxy si la escala sería ≥ 0,75, con histéresis. Los parámetros `ParamUnit.PIXELS` se escalan por el factor del proxy.

### Hilos

**Regla: ningún trabajo pesado en el hilo FX.** El hilo FX lee controles, crea pasos de receta y muestra lo que le publican.

| Hilo | Qué hace |
|---|---|
| FX | Interfaz; `EditSession` (estado). Los resultados llegan con `Platform.runLater`, inmutables. |
| `rora-preview` + pool N−1 | Carril PREVIEW: último gana, debounce de 30 ms con arranque inmediato. |
| `rora-full` + pool (N−1)/2, prioridad menor | Carril FULL: solo exportar, zoom mayor que el proxy, diagnóstico, entrada de blending/histograma y cuentagotas. Último gana **por propósito**; la exportación está protegida. Un paso legacy lento aquí nunca bloquea un preview. |
| `rora-aux` | Imagen original del modo comparar. |
| `rora-pane-*` | Blending, histograma, degradado y lectura de fondos (`LatestWins`). |
| `rora-io` | Carga y guardado (`Task`). |

Un resultado sustituido nunca se publica (se comprueba al preparar en el worker y dentro de la tarea publicada). Los errores se publican y se registran con `System.Logger`. Los pasos legacy no se pueden cancelar por dentro: su resultado obsoleto se descarta.

La conversión `Img → WritableImage` se hace en el worker (`FxImages`) y reutiliza imágenes con `FramePool`: una imagen retirada no se vuelve a escribir hasta pasados dos pulsos de la escena.

Monitor de bloqueos (solo desarrollo): `-Drora.fxStallMonitor=true` registra las pausas del hilo FX mayores de 100 ms con su traza.

### Convolución y formato de origen

`ConvolveOp` da resultados distintos según el tipo de `BufferedImage` (sobre ARGB convoluciona también el alpha); solo `INT_ARGB` y `4BYTE_ABGR` coinciden. El paso `legacy.convolution` lleva un parámetro oculto `sourceFormat`, que `EditSession` calcula recorriendo la receta y que forma parte de la clave de caché. Los tipos con pérdida (gris, paleta propia, `TYPE_CUSTOM`) conservan el `BufferedImage` original. La exportación reconstruye el tipo final de la cadena. Una imagen abierta por los códecs nuevos es siempre `argb`, también en gris: solo cambia el resultado de esta convolución del Laboratorio. Desviación conocida: convolución→convolución sobre gris (`known-deviation`), se corrige al reescribir la convolución en la fase 3.

### Diferencias visibles del preview respecto al FULL

Solo existen cuando hay proxy. Lo exportado, el histograma, el blending, el diagnóstico de exposición y el cuentagotas al hacer clic son siempre exactos.

1. **Resolución**: la imagen reducida por promedio de área; con zoom mayor que el proxy se ve el proxy ampliado hasta que llega el FULL.
2. **Operaciones puntuales**: `f(reducir(src))` en lugar de `reducir(f(src))`; pequeñas diferencias en bordes y texturas finas, sobre todo en umbrales (B/N, umbral suave, posterizar, alpha test, stencil por umbral).
3. **Posicionales** (desvanecimiento circular, mapa de profundidad, Z/W-buffer, interpolación en profundidad): misma forma, muestreada en la rejilla del proxy.
4. **Pixelar, rejilla, stencil por celdas, MSAA**: tamaño redondeado (`round(v·s)`, con el mínimo del parámetro); la línea de la rejilla mide 1 px del proxy (más gruesa); con `s` pequeño MSAA y bloques quedan en su mínimo y el preview es más nítido que el FULL.
5. **Buffer de acumulación**: desplazamiento `8·s` redondeado, estela de longitud aproximada.
6. **Convolución 3×3**: el núcleo no se escala; en el preview el efecto abarca 1/s píxeles de la imagen completa (más fuerte), y el marco sin procesar (defecto conocido) mide 1 px del proxy.
7. **Muestreo de textura**: misma escala relativa; el aliasing del vecino más cercano depende de la rejilla.
8. **Ecualización**: el histograma se calcula sobre el proxy; curva ligeramente distinta y marcas evaluadas por píxel del proxy.
9. **Cuentagotas al pasar el ratón**: valor del proxy, marcado con "≈"; coordenadas de la imagen completa.
10. **Diagnóstico de exposición**: nunca aproximado ("Cargando diagnóstico..." hasta el valor exacto).

## Operaciones nuevas (fase 3)

**`adjust.basic`.** Un solo paso y un solo kernel: pasa a luz lineal una vez, aplica balance de blancos, exposición y curva tonal sobre la luminancia, intensidad y saturación, y cuantiza una vez al final. Hacerlo con diez operaciones cuantizaría a 8 bits entre cada una (bandas y luces perdidas). Una etapa con sus parámetros neutros no se ejecuta, así que con todos neutros la salida es exactamente la entrada. La curva actúa sobre la luminancia y aplica ganancia a RGB, lo que preserva el tono.

**Gamut (`LinearPack`).** Un color fuera de rango mezcla recorte por canal, para excesos mínimos, con compresión hacia la luminancia, que conserva el tono en excesos grandes. La compresión sola era demasiado sensible cerca de `Y = 1`.

**Geometría.** `Operation.outputSize` declara el tamaño de salida y `Operation.forPreview` adapta los parámetros al proxy. La escala de los parámetros en píxeles se calcula en cada paso, no solo con la fuente. El recorte es normalizado a 0..1, independiente de la resolución.

## Dirección prevista

- Motor de IA opcional en su propio módulo; distribución con `jlink` + `jpackage`.
