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

- `AwtImg.toBufferedImage` hace dos copias (`toArray` + `setDataElements`). Se puede reducir a una escribiendo directamente en el `DataBufferInt` del destino, a cambio de que Java2D deje de acelerar esa imagen. Pendiente de medir en la fase 2.
- El destino de cada operación es un `int[]` nuevo (puesto a cero por la JVM). Con un *pool* de buffers en `OpContext` (fase 2) se evitaría.
