# Rendimiento

Medidas de la fase 1: implementaciones heredadas frente al núcleo `core-imaging`. Las operaciones portadas dan resultados **idénticos bit a bit** (ver `docs/TESTING.md`); la exactitud no se ha sacrificado para ganar velocidad.

## Cómo reproducirlo

```bash
./mvnw -Pbench -pl bench -am package -DskipTests
java -jar bench/target/benchmarks.jar -rf json -rff resultado.json
```

`CoreVsLegacyBenchmark` comprueba antes de medir que el legacy y la fachada dan exactamente el mismo resultado. La salida cruda de JMH está en `.planning/jmh-fase1-jdk21.{txt,json}` y `.planning/jmh-fase1-jdk25.{txt,json}` (local, no versionada).

## Entorno

| | |
|---|---|
| CPU | Intel Core i5-1035G1 (portátil): 4 núcleos / 8 hilos, 1,0 GHz base, hasta 3,6 GHz |
| RAM | 11,7 GB |
| SO | Windows 11 Home 10.0.26200 |
| JDK | Temurin 21.0.13+9 y Temurin 25.0.5+8 |
| JMH | 1.37 · 1 fork · calentamiento 3 × 2 s · medida 5 × 2 s · `-Xms4g -Xmx4g` · modo tiempo medio |
| Imagen | 4000 × 3000 = 12 MP, `TYPE_INT_ARGB`, ruido con semilla fija |
| Hilos (N) | `TileExecutor.defaultParallelism()` = 7 (8 hilos lógicos − 1) |

Es un portátil: hay variación por temperatura y gestión de energía (columna ± de JMH, intervalo del 99,9 %).

## Variantes medidas

| Variante | Qué incluye |
|---|---|
| `legacy` | Código heredado: `getRGB/setRGB` por píxel, 1 hilo |
| `core1Thread` | Núcleo sobre `Img`, 1 hilo → efecto del **acceso directo al buffer** |
| `coreNThreads` | Núcleo sobre `Img`, 7 hilos → efecto añadido del **paralelismo** |
| `facade1Thread` | `BufferedImage → Img`, núcleo con 1 hilo, `Img → BufferedImage` |
| `facadeNThreads` | La llamada real de la app (`ImageProcessor.x`): conversión + núcleo con 7 hilos |

## Resultados (JDK 21.0.13, ms por operación)

| Operación | legacy | core 1 hilo | core 7 hilos | fachada 1 hilo | fachada 7 hilos (app) |
|---|---|---|---|---|---|
| Escala de grises (`color.grayscale-average`) | 196,1 ± 39,4 | 11,4 ± 0,1 | 12,4 ± 0,6 | 46,3 ± 0,6 | 47,1 ± 3,0 |
| Saturación 1,3 (`point.saturation`) | 592,6 ± 40,3 | 428,8 ± 10,1 | 93,5 ± 10,9 | 459,3 ± 9,0 | 122,7 ± 32,8 |
| Pixelar bloque 8 (`raster.pixelate`, vecindad) | 160,8 ± 3,4 | 40,0 ± 4,6 | 16,2 ± 4,5 | 74,4 ± 2,8 | 49,9 ± 1,8 |

### Aceleración respecto al legacy (JDK 21)

| Operación | Buffer directo (core 1 hilo) | + paralelismo (core 7 hilos) | App de punta a punta (fachada 7 hilos) |
|---|---|---|---|
| Escala de grises | **17,2×** | 15,8× | 4,2× |
| Saturación | 1,4× | 6,3× | 4,8× |
| Pixelar | 4,0× | **9,9×** | 3,2× |

Con JDK 25.0.5 los resultados son equivalentes dentro del ruido (grises: legacy 193,9 → core 14,6 ms; saturación: 577,8 → 91,5 ms con 7 hilos; pixelar: 181,9 → 16,9 ms con 7 hilos). Las tablas completas están en la salida cruda.

## Objetivo del roadmap: ≥ 10× en grises y saturación — **no alcanzado de punta a punta**

| | Grises | Saturación |
|---|---|---|
| Núcleo sobre `Img` | **17,2× (cumple)** | 6,3× (no cumple) |
| Llamada real de la app | 4,2× (no cumple) | 4,8× (no cumple) |

### Causas medidas

1. **Grises: el coste está en la conversión, no en el cálculo.** El cálculo baja de 196 a 11 ms (17×). La fachada tarda 47 ms porque por cada llamada hace tres copias de 48 MB y crea dos arrays nuevos: `BufferedImage → int[]`, `Img → int[]` (`toArray`) e `int[] → raster` (`setDataElements`). La diferencia `fachada − núcleo` es de ~35 ms en grises, ~30 ms en saturación y ~34 ms en pixelar: es un coste fijo de unos 3 ms/MP. Paralelizar no ayuda (11,4 → 12,4 ms): una operación de 1–2 instrucciones por píxel está limitada por la memoria (lectura, escritura y puesta a cero del destino), no por la CPU.
2. **Saturación: el coste está en la aritmética HSB, que debe ser idéntica.** El acceso directo solo aporta 1,4×, porque en el legacy `getRGB/setRGB` era una parte pequeña del tiempo: domina la conversión RGB↔HSB en `float`, con tres divisiones por píxel. Para ser exactos hay que hacer las mismas operaciones en el mismo orden, así que no se puede simplificar la fórmula. El paralelismo da 4,6× (428,8 → 93,5 ms) en 4 núcleos físicos con *hyper-threading*. Para llegar a 10× solo con paralelismo harían falta unos 8 núcleos físicos.

### Qué haría falta (no implementado: cambiaría el alcance o la exactitud)

- **Quitar la conversión por operación** (afecta a grises y a todas): en la fase 2, el `Renderer` mantendrá la imagen como `Img` durante toda la receta y convertirá una sola vez al mostrar o exportar. Con eso, el número relevante para la app pasa a ser el del núcleo (17× en grises). Como mejora intermedia, ver la deuda técnica más abajo.
- **Saturación más rápida y exacta**: una formulación algebraica alternativa con verificación exhaustiva (el test de 2^24 colores ya existe), pero solo es demostrable para factores concretos, no para todo el rango continuo; o la Vector API (aún en *incubator* en JDK 21/25). Ambas necesitan tu aprobación.

## Deuda técnica de rendimiento detectada

- `AwtImg.toBufferedImage` hace dos copias (`toArray` + `setDataElements`). Se puede reducir a una escribiendo directamente en el `DataBufferInt` del destino, a cambio de que Java2D deje de acelerar esa imagen. Tras la fase 2 solo afecta a los pasos legacy y a la exportación.
- El destino de cada operación es un `int[]` nuevo (puesto a cero por la JVM). Con un *pool* de buffers en `OpContext` (fase 2) se evitaría.

## Fase 2: pipeline

Entorno: el de arriba, JDK 25.0.5, JMH 1.37. Salida cruda en `.planning/jmh-fase2-jdk25*` (local). Reproducir: `java -jar bench/target/benchmarks.jar "PipelineBenchmark|FusionProxyBenchmark|FxImagesBenchmark"`.

**Tick de slider** sobre un proxy de 2 MP (fuente de 24 MP) con un prefijo de 10 pasos núcleo+legacy en caché (`PipelineBenchmark`, ms). Meta del roadmap: < 50 ms en operaciones puntuales.

| Paso pendiente | 1 hilo | 7 hilos |
|---|---|---|
| Saturación (HSB) | 58,6 ± 14,6 | **15,6 ± 1,8** |
| Brillo | 2,7 ± 2,5 | 2,4 ± 0,4 |
| Escala y bias | 31,3 ± 4,0 | 9,2 ± 0,9 |
| Pixelar (vecindad) | 8,2 ± 2,9 | 3,9 ± 1,5 |

Se cumple con 7 hilos. Con un solo hilo la saturación no llega (la aritmética HSB exacta domina). Hay que sumar la conversión a imagen FX (abajo) y hasta 30 ms de debounce.

**Fusión** de 5 operaciones puntuales sobre 12 MP (`FusionProxyBenchmark`, ms): 1 hilo 325,2 ± 20,1 fusionada frente a 333,7 ± 62,6 paso a paso; 7 hilos 107,2 ± 15,6 frente a 129,2 ± 14,3. Ganancia pequeña, de la magnitud del margen de error; está activada porque es exacta.

**Proxy** desde 24 MP (ms): a 2 MP, 292,8 ± 10,9 con 1 hilo y 89,9 ± 11,0 con 7; a 4 MP, 401,1 ± 24,4 y 117,3 ± 22,7. Se paga una vez por imagen y tamaño de visor.

**`Img` → `WritableImage` reutilizada** (`FxImagesBenchmark`, ms), en el worker:

| | `INT_ARGB` | `INT_ARGB_PRE` |
|---|---|---|
| Opaca, 2 MP | 4,0 ± 2,1 | 3,1 ± 0,3 |
| Opaca, 4 MP | 6,6 ± 1,1 | 6,4 ± 0,9 |
| Con alpha, 2 MP | 11,7 ± 0,6 | 12,7 ± 1,5 |
| Con alpha, 4 MP | 23,2 ± 1,5 | 28,2 ± 11,5 |

`INT_ARGB` nunca fue peor fuera del margen de error: es el formato por defecto (`-Drora.fx.format=pre` para cambiarlo).

## Fase 2: memoria

`SessionMemoryTest`, JVM aparte con `-Xmx1536m` y G1 (región de 1 MB por defecto: no hizo falta cambiarla; sin fallos de evacuación ni Full GC ajenos a los `System.gc()` del test, pausas jóvenes ≤ 6 ms). 6000×4000 `3BYTE_BGR`, receta de 10 pasos núcleo+legacy, FULL, exportación PNG e historial de 35 pasos con 35 deshacer y 35 rehacer.

| | JDK 25 | JDK 21 |
|---|---|---|
| Máximo de heap vivo tras GC (meta < 1,5 GB) | 475 MB | 475 MB |
| Crecimiento por el historial de 35 pasos | 10 KB | 9 KB |

Antes de la fase 2, el historial de imágenes ocupaba por sí solo unos 2,9 GB con 24 MP.

Panel de blending con 24 MP: 627 MB vivos publicando la imagen completa a la interfaz frente a 323 MB con una copia de 4 MP. El umbral era el 25 % del heap (384 MB), así que los paneles publican la copia y conservan la imagen exacta para "Usar en editor".

Pausas del hilo FX con la app real, 24 MP y `-Drora.fxStallMonitor=true` (> 100 ms): arranque de la ventana (1,9 s), CSS de los controles al seleccionar el primer filtro (0,5 s) y subida a GPU de la textura de 24 MP al pasar a zoom con la imagen completa (0,14 s, una vez). Mover sliders, aplicar, deshacer, comparar y exportar no registraron pausas.

## Fase 3: interfaz

JMH con `AdjustBenchmark` (JDK 25.0.5, i5-1035G1 4C/8T). Ruido sintético, todos los ajustes activos, ms por operación:

| Operación | 1 hilo | 7 hilos |
|---|---|---|
| `adjust.basic`, tick en proxy de 2 MP (meta < 50 ms) | 156,3 ± 4,1 | **35,5 ± 1,7** |
| `adjust.basic`, 12 MP | 951,7 ± 65,5 | 187,7 ± 12,0 |
| `detail.unsharp-mask`, 2 MP | 137,9 ± 4,9 | 38,8 ± 4,0 |
| `blur.gaussian` radio 8, 2 MP | 309,9 ± 10,9 | 110,0 ± 23,2 |
| `blur.gaussian` radio 40, 2 MP | 1327,1 ± 45,0 | 434,9 ± 45,4 |

El desenfoque no llega a 50 ms por tick; "último gana" evita que bloquee, pero el slider no va fluido con radios grandes.

Hilo FX con `StressTool` y el monitor de bloqueos (3 ejecuciones, `-Xmx1536m`): **0 pausas de más de 100 ms** al arrastrar sliders, hacer zoom y pan, mover la cortina, deshacer y rehacer, y abrir una imagen de 24 MP con receta de 10 pasos. El arranque de la ventana sí tiene una pausa de unos 1 s (CSS, fuentes, Prism).

Arranque en frío, mediana de 8 ejecuciones: interfaz nueva 3077 ms, antigua 3527 ms (medido antes de retirar la antigua).
