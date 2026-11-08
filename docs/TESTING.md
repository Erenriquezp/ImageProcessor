# Pruebas

Red de seguridad de la fase 0 (caracterización del comportamiento heredado) y, desde la fase 1, equivalencia exacta del núcleo `core-imaging` con ese comportamiento.
Ni el núcleo ni `service/` y `domain/` dependen de JavaFX: los tests corren en modo headless.

## Ejecutar

```bash
./mvnw test                       # todos los tests (Windows PowerShell: .\mvnw.cmd test)
./mvnw verify                     # tests + Checkstyle (informe) + JaCoCo (informe por módulo en <módulo>/target/site/jacoco/ y agregado en app/target/site/jacoco-aggregate/)
./mvnw test -Dgroups=property     # solo una etiqueta
./mvnw test -DexcludedGroups=known-issue
./mvnw test -Dtest=LegacyPinTest
./mvnw test -DexcludedGroups=exhaustive   # sin los tests de 2^24 colores (~2 min menos)
./mvnw test -pl core-imaging              # solo el núcleo
./mvnw verify -Dskip.memory=true          # sin el test de memoria (JVM aparte con -Xmx1536m)
```

Surefire usa el module path (JPMS): los tests viven en los mismos paquetes que el código (`...service`, `...domain`, `...core.*`) y no requieren cambios en `module-info.java`.
En `core-imaging`, **solo los tests** leen `java.desktop` (`--add-reads` en el POM) para comparar con `java.awt.Color`; el módulo en sí solo requiere `java.base`.
JaCoCo no tiene umbral de bloqueo todavía.

## Etiquetas JUnit

| Etiqueta | Significado |
|---|---|
| `legacy-pin` | Nivel A. Fija con golden exacto el comportamiento heredado. Una fase posterior puede actualizarlo **de forma deliberada**. |
| `property` | Nivel B (y propiedades comunes). Solo propiedades: dimensiones, alpha, rango, determinismo, identidad con parámetros neutros. |
| `known-issue` | Defecto documentado de la auditoría. El test describe el comportamiento **actual** (o está `@Disabled` si describe el correcto). |
| `equivalence` | Fase 1. Núcleo vs legacy **bit a bit** (y el puente `BufferedImage ↔ Img`). |
| `exhaustive` | Equivalencia sobre los **2^24 colores RGB** (HSB, operaciones puntuales y cadenas fusionadas). Incluida en `verify`; tarda ~2 min. |
| `memory` | Fase 2. `SessionMemoryTest`: 24 MP en un JVM aparte con `-Xmx1536m`. Ejecución propia de surefire dentro de `verify`; informes en `app/target/memory-*.txt` y log de GC en `app/target/memory-gc.log`. Se salta con `-Dskip.memory=true`. |
| `known-deviation` | Fase 2. Diferencia conocida y aceptada con la cadena anterior, fijada con su tamaño exacto. Hoy: convolución→convolución sobre gris de 8 bits. |

## Goldens

Los PNG de referencia están en `app/src/test/resources/golden/<operacion>/<caso>__<imagen>.png` (peso total < 2 MB).

- Sin flags, un golden ausente **hace fallar** el test; nunca se crea en silencio.
- Para regenerarlos, de forma deliberada: `./mvnw test -Dtest=LegacyPinTest -Dgolden.update=true`.
- Si un golden cambia, explica el motivo en el mensaje del commit.

## Clasificación de operaciones

| Nivel | Qué se prueba | Operaciones |
|---|---|---|
| **A** (pin exacto) | Golden exacto con parámetros neutro, típico y extremo | Negativo, A luminancia, Retro 1, Re-coloración, Pixelar, Ajuste de color, Escala y bias, Umbral suave, Saturación, Diagnóstico de exposición |
| **B** (propiedades) | Comportamiento que cambiará a propósito | Grises, Brillo, HSV, B/N por umbral, Grises cuantizados, Sepia/Frío/Cálido/Polaroid/Kodachrome, Desvanecimiento circular, Retro 2, Convolución, Buffer ADD/ACUM/MULT, Mapa de profundidad, Muestreo de textura, Blending de fragmentos, Ecualización, Rotación de tono, Degradados, Histograma, Blending 2 imágenes |
| **C** (humo) | Solo que no lanza y conserva dimensiones | Transparencia global, Esmerilado, Reducción 4 bits, Z/W-Buffer, Rasterizar, Interpolación en profundidad, MSAA, Alpha Test, Stencil, Logic Op, Interpolación, Extrapolación, Espacio de color, Triple blending |

El detalle por operación (justificación y parámetros) está en el plan local de la fase 0.

## Utilidades (`app/src/test/java/.../testsupport`)

- `TestImages`: imágenes deterministas (degradado, tablero, ruido con semilla fija, bordes duros), RGB y ARGB con alpha parcial.
- `ImageAssert`: igualdad exacta o con tolerancia por canal, dimensiones, alpha, rango.
- `GoldenSupport`: comparación con goldens.
- `OpCatalog` / `OpCase`: catálogo de operaciones con sus casos de parámetros (rangos tomados de `EditorFilterPane`).
- `EquivalenceImages`: imágenes de los tests diferenciales (tipos, tamaños, contenidos, semillas) y la imagen de 2^24 colores.

## Equivalencia del núcleo (fase 1)

Las operaciones portadas al núcleo deben dar **exactamente** el mismo resultado que el código heredado (tolerancia 0).

| Test | Módulo | Qué comprueba |
|---|---|---|
| `CoreEquivalenceTest` | app | Cada caso de `PortedCases` (neutro, típico, extremos de la UI y valores fuera de rango) × 7 tipos de `BufferedImage` × 24 imágenes (8 tamaños de 1×1 a 129×200 × ruido, valores frontera y color sólido, semilla fija): legacy = fachada = núcleo con 1 hilo = 7 hilos = bandas de 1 fila. Mismo tipo, dimensiones, `getRGB` y misma instancia cuando el legacy devuelve la entrada. |
| `AllColorsEquivalenceTest` | app | Los casos marcados `exhaustive` sobre una imagen 4096×4096 con los 2^24 colores y alpha con semilla. |
| `AnalysisEquivalenceTest` | app | Diagnóstico de exposición (registro y texto, con varios locales) e histograma (imagen exacta y recuentos con 1/N hilos). |
| `AwtImgTest` | app | Puente `AwtImg`: `toImg == getRGB` en 13 tipos, subimágenes y `INT_RGB` con byte alto sucio; la salida es idéntica a `setRGB`. |
| `HsbExhaustiveTest` | core | `Hsb` vs `java.awt.Color` en los 2^24 colores y en las ternas que usan saturación, HSV y rotación de tono. |
| `RegistryPropertiesTest` | core | Propiedades de **todas** las operaciones del registro, generadas de su esquema y metadatos (ver `docs/ARCHITECTURE.md`). |
| `TileExecutorTest`, `OperationSpiTest`, `ParamsTest`, `ImgTest`, `ModuleAndServiceLoaderTest` | core | Ejecutor (hilos, bandas, cancelación, progreso, strict halo), SPI, parámetros, `Img`, módulo y `ServiceLoader`. |

Un fallo indica operación, parámetros, camino (fachada / 1 hilo / N hilos / bandas de 1 fila), tipo, tamaño, contenido, semilla y el primer píxel distinto. Para reproducirlo basta con el nombre del caso.

`legacy-pin` y `known-issue` no se han tocado y ahora pasan **a través del núcleo**, porque `OpCatalog` llama a la fachada.

## Pipeline y concurrencia (fase 2)

| Test | Módulo | Qué comprueba |
|---|---|---|
| `RecipeEquivalenceTest` | core | 300 recetas con semilla (1–12 pasos, puntuales/vecindad/globales): FULL ≡ aplicación secuencial, con y sin fusión, caché fría y caliente, 1 y 7 hilos. |
| `FusionEquivalenceTest`, `FusionAllColorsTest` | core | Fusión exacta: 1000 cadenas con semilla y cadenas HSB sobre los 2^24 colores. |
| `FullRecipeEquivalenceTest`, `FilterSpecsTest` | app | Recetas que mezclan núcleo y legacy ≡ encadenar `processFilter` (copia literal en `ReferenceFilters`), en 6 tipos de imagen. |
| `ConvolutionFormatTest` | app | La tabla de tipos de la convolución y la desviación conocida. |
| `RenderLaneTest`, `LatestWinsTest`, `EditSessionTest` | app | Último gana por propósito, debounce, obsoletos descartados, errores, cierre. Con tiempo virtual (`ManualScheduler`) y colas manuales (`ManualExecutor`): sin `sleep` ni reloj real. |
| `RenderCoordinatorTest` | app | Hilos reales sincronizados solo con latches: un FULL bloqueado no bloquea el PREVIEW. |
| `ArchitectureRulesTest` | app | ArchUnit: la UI no depende de `service` ni invoca el proceso del núcleo; la sesión no usa JavaFX. |

## Defectos conocidos verificados

JPG con ARGB (reproducido; el test correcto está `@Disabled`, se corrige en la fase 4), Interpolación = Extrapolación, modos de Reducción 4 bits idénticos (incluido DECIMAL), marco de 1 px sin procesar en la convolución, Blending que deforma el fondo, Escala de grises por promedio y matrices de color sin offsets.
