# Pruebas

Red de seguridad de la fase 0 (caracterización del comportamiento heredado) y, desde la fase 1, equivalencia exacta del núcleo `core-imaging` con ese comportamiento.
Los tests corren en modo headless, salvo el humo de interfaz (perfil `ui-smoke`).

## Ejecutar

```bash
./mvnw test                       # todos los tests (Windows PowerShell: .\mvnw.cmd test)
./mvnw verify                     # tests + Checkstyle (informe) + JaCoCo (informe por módulo en <módulo>/target/site/jacoco/ y agregado en lab-plugin/target/site/jacoco-aggregate/)
./mvnw test -Dgroups=property     # solo una etiqueta
./mvnw test -DexcludedGroups=known-issue
./mvnw test -Dtest=LegacyPinTest
./mvnw test -DexcludedGroups=exhaustive   # sin los tests de 2^24 colores (~2 min menos)
./mvnw test -pl core-imaging              # solo el núcleo
./mvnw verify -Dskip.memory=true          # sin los tests de memoria (JVM aparte con -Xmx1536m)
./mvnw -Pui-smoke -pl app -am test        # humo con el toolkit real (pantalla o xvfb-run)
```

Surefire usa el module path (JPMS): los tests viven en los mismos paquetes que el código y no requieren cambios en `module-info.java`.
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
| `golden` | Fase 3. Goldens exactos de las operaciones nuevas, que no tienen referencia heredada. |
| `ai-real` | Fase 5. Modelo real de IA; excluida por defecto (`-Dgroups=ai-real -DexcludedGroups=`). |
| `known-deviation` | Fase 2. Diferencia conocida y aceptada con la cadena anterior, fijada con su tamaño exacto. Hoy: convolución→convolución sobre gris de 8 bits. |

## Goldens

Los PNG de referencia están en `lab-plugin/src/test/resources/golden/<operacion>/<caso>__<imagen>.png` (peso total < 2 MB).

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

## Utilidades (`lab-plugin/src/test/java/.../testsupport`)

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

## Interfaz (fase 3)

| Test | Módulo | Qué comprueba |
|---|---|---|
| `AdjustBasicOpTest`, `AdjustBasicAllColorsTest` | core | Identidad exacta con neutros sobre los 2^24 colores, ida y vuelta lineal exacta, curva monótona, gris estable, un paso pequeño no salta |
| `BlurAndSharpenTest`, `GeometryOpsTest`, `OutputSizeContractTest`, `RendererPreviewScaleTest` | core | Operaciones nuevas, tamaño de salida de todo el registro, escala por paso |
| `ViewTransformTest`, `PureModelsTest` | app | Zoom, cortina, controles, recorte y orden de la receta, sin JavaFX |
| `StudioViewModelTest`, `EditSessionRecipeTest` | app | ViewModels sobre una sesión real, con hilos y tiempo manuales |
| `CatalogAndLabelsTest`, `TokenContrastTest` | app | Catálogo completo, textos sin faltas ni huérfanos, contraste AA |
| `ArchitectureRulesTest` | app | Vistas → ViewModels → sesión; ViewModels sin nodos |
| `StudioUiSmoke` | app | Perfil `ui-smoke`: arranca, abre, ajusta y exporta con el toolkit real |
| `LabLabelsTest` | lab-plugin | El módulo se descubre y sus textos están completos |

Las vistas no tienen tests unitarios: las cubren el humo, las capturas de `SnapshotTool` y las pruebas manuales.

Los tests diferenciales con el legacy viven en `lab-plugin`, junto a ese código. Los tres reenvíos del paquete `service` (`AwtImg`, `ImageIOService`, `SourceFormat`) existen para no editarlos.

## Entrada y salida (fase 4)

| Test | Módulo | Qué comprueba |
|---|---|---|
| `PureIoTest`, `ExifCodecTest` | core | Orientación 1–8, aplanado en los 65536 pares (α, c), redondeo de 16 bits, nombres, búsqueda de calidad, detección de formato, EXIF con anomalías, política de metadatos |
| `DecodeTest` | imaging-io | Gris exacto, 16 bits, paleta, CMYK/YCCK, ICC contra el cálculo analítico (≤ 2 niveles), orientación, WebP, multipágina, rutas con emojis, cancelación |
| `ExportTest` | imaging-io | Ida y vuelta exacta en PNG y TIFF, PSNR de JPEG, aplanado, **escaneo de privacidad** en 18 combinaciones, oráculo independiente, escritura atómica con inyección de fallos, colisiones |
| `RobustnessTest` | imaging-io | Truncados, cabeceras enormes, basura, EXIF y XMP malformados, sin temporales, todo con `assertTimeout` |
| `IoMemoryTest` | imaging-io | 12 y 24 MP en JVM aparte con `-Xmx1536m`; informe en `imaging-io/target/io-memory-report.txt` |
| `StudioViewModelTest` | app | Diálogo, presets, preferencias, estimación, cambios sin exportar, colisiones, cancelación |

Los fixtures (ICC, EXIF, CMYK, 16 bits) se generan por código en `Fixtures`; los únicos binarios son dos WebP de menos de 200 bytes hechos aquí.

Fuera de las pruebas automáticas: `tools/jlink-check.sh` y los archivos reales de cámara, que se comprueban a mano.

## Motor de IA (fase 5)

| Test | Módulo | Qué comprueba |
|---|---|---|
| `TilerTest`, `OnnxEngineTest`, `OrtNativeTest` | ai-engine | Tiles idénticos bit a bit a la imagen completa con modelos sintéticos, costuras, errores de forma y de modelo, cancelación, nativos que fallan cerrado |
| `ArtifactStoreTest` | ai-engine | Caché acotada, expulsión, en uso, borrado y huérfanos |
| `AiEngineArchitectureTest`, `AiArchitectureRulesTest` | ai-engine, app | Sin `java.net`; ORT solo en `ai.onnx`; el motor solo desde `app.session.ai` |
| `AiJobServiceTest`, `SampledPhysicalMemoryTest` | app | Un trabajo a la vez, estados, ETA, límites, memoria leída fuera del hilo FX |
| `EnhanceIntegrationTest`, `EnhanceViewModelTest` | app | El motor nunca arranca solo; insignias, obsoleto y deshacer; pregunta al exportar; marca de procedencia |
| `ProvenanceXmpTest`, `ProvenanceExportTest` | core, imaging-io | La marca en JPEG, PNG y TIFF con cada política; con "Quitar todo" no queda rastro |
| `AiRealTest` (`ai-real`) | ai-engine | Modelo real: tiempo, memoria, costuras y cancelación. Fuera de `verify`; ver [AI.md](AI.md) |

Los modelos sintéticos los genera `tools/models/make_test_models.py`, con su hash en `SHA256SUMS`.

## Wallpapers (fase 6)

| Test | Módulo | Qué comprueba |
|---|---|---|
| `JsonTest`, `PresetCodecTest`, `PresetCatalogTest`, `UserPresetStoreTest` | wallpapers | Lector JSON estricto; los 35 integrados válidos y coherentes; archivos propios rotos, duplicados, enormes, sin UTF-8, enlaces (con `Assumptions` donde el sistema no deja crearlos); ids que nunca son rutas |
| `FitTest`, `ResamplerTest` | wallpapers | Tamaño exacto para la matriz de fuentes (1×1 a 8000×1200) × todos los presets y extremos; identidad bit a bit; líneas en su sitio (≤ 0,02 px, salida de 8 bits); simetría; mismo resultado con 1 o N hilos |
| `FacePreservingFramerTest` | wallpapers | Encuadre v3: sin caras, centrado exacto; caras dentro cuando caben; la mayor si no; ojos y boca si ni la mayor cabe; invariancia de escala |
| `FillsTest`, `PlannerTest`, `WallpaperJobTest` | wallpapers | Rellenos deterministas y sin costura; banding < 0,01 niveles con tramado; Auto, insignias, escala de mejora requerida; fallos y cancelación de la tanda; nombres que no salen de la carpeta |
| `YuNetDecoderTest`, `YuNetDetectorTest` | ai-engine | Decodificación y NMS con tensores sintéticos; modelo incluido con su hash; ORT real con imágenes sin caras, repetible |
| `YuNetRealTest` (`ai-real`) | ai-engine | Retrato de la NASA (dominio público, `faces/NOTICE.md`): una cara, bit a bit en dos ejecuciones |
| `WallpaperExportTest`, `WallpaperRecipeIntegrationTest` | app | Archivos con tamaño y nombre exactos, colisiones, cancelación sin restos, un destino fallido no para los demás, carpeta de solo lectura, metadatos por escaneo de bytes; parte del resultado de la receta y nunca lanza la IA |
| `WallpapersViewModelTest` | app | Selección inicial, "Mi pantalla", análisis en caché por receta, encuadre manual y Auto, reinicio al cambiar la proporción, rellenos, presets propios, exportación con una sola pregunta de colisión |
| `WallpaperMemoryTest` (`memory`) | app | 24 MP × 8 destinos con `-Xmx1536m`: todos escritos y pico de heap < 1 GB |
| `WallpapersArchitectureTest`, `WallpaperArchitectureRulesTest` | wallpapers, app | Sin AWT, JavaFX, ORT ni red en la lógica; render, tanda y detectores solo en la capa de sesión |

## Distribución, registro y errores (fase 8)

| Test | Módulo | Qué comprueba |
|---|---|---|
| `RedactorTest`, `LogsTest` | app | Redacción con tildes, espacios, emoji, UNC, rutas de más de 260 caracteres, forma 8.3, barras mezcladas y nombres de usuario cortos que no tocan otras palabras; rotación (5 archivos, ninguno de más de 1 MiB), 8 hilos escribiendo a la vez sin líneas mezcladas, nivel por propiedad e interruptor |
| `NoImageDataInLogsTest`, `RenderLaneLogTest` | app | Un fallo de render y otro de exportación con una foto con GPS: el registro no contiene valores de la receta, nombres ni GPS; solo los ids de las operaciones |
| `DiagnosticReportTest`, `HelpViewModelTest` | app | Secciones en orden; casos adversariales (ruta de usuario con tildes, nombre de la foto en el registro y en el error, ruta UNC, nombre del equipo) y opciones de la JVM sin rutas; nada se copia ni guarda hasta pulsar; "Borrar registros y temporales" solo dentro de sus carpetas; un `hs_err` anterior se ofrece una vez, como advertencia, y queda como visto |
| `GlobalErrorsTest`, `EditSessionMemoryReleaseTest`, `HsErrScannerTest` | app | Errores no capturados agrupados; `OutOfMemoryError` libera la reserva, cancela y vacía cachés, y la sesión sigue; cabecera de `hs_err` redactada |
| `GitHubReleasesClientTest`, `ReleaseFeedTest`, `UpdateViewModelTest` | app | Servidor `com.sun.net.httpserver` en 127.0.0.1: versión nueva, igual, `major` mayor, etiquetas raras y `-academic`, JSON inválido, cuerpo enorme, límite de GitHub, tiempo agotado, redirección, cabeceras exactas; ninguna petición al arrancar ni con `-Drora.updates=off` |
| `NetworkIsolationRulesTest`, `OrtTelemetryTest` | app, ai-engine | Red solo en `GitHubReleasesClient`; telemetría de ORT apagada |
| `VcRuntimeMessageTest`, `FileArgumentTest`, `AppInfoTest`, `AppTempTest` | app | Aviso de Visual C++ ante un fallo de biblioteca nativa; selección de la ruta por argumento; versión única; temporal propio |
| `WallpaperExportEstimateTest`, `WallpapersEstimateTest` | app | Estimación de tamaño dentro de un factor 2 del archivo real; fuera del hilo FX y cancelable |

Fuera de `verify`, en Windows (herramientas de desarrollo; ver [RELEASING.md](RELEASING.md)):

| Herramienta | Qué comprueba |
|---|---|
| `tools/package/verify-package.ps1` | Árbol del MSI y del ZIP: solo nativos win-x64, avisos legales, modelos, versión coincidente, opciones de la JVM, sin `java.sql` ni volcados (lo llama el empaquetado) |
| `tools/package/verify-repro.ps1` | El ZIP sale idéntico en dos empaquetados |
| `tools/package/smoke-package.ps1` (`PackageSmoke`) | App-image sin JDK en el PATH desde una carpeta con tildes y emoji: abrir 24 MP y WebP, YuNet, x4v3, wallpapers, DLL cargadas solo desde la app, lanzador real con una foto por argumento, cierre limpio, nada en `%TEMP%` |
| `tools/package/test-msi.ps1` | `msiexec` en silencio y sin administrador: instalar, arrancar, actualizar dejando una sola entrada, desinstalar y listar lo que queda |
| `tools/sandbox/GUION.md` | Prueba manual de 14 pasos en una máquina limpia |

## Defectos conocidos verificados

Interpolación = Extrapolación, modos de Reducción 4 bits idénticos (incluido DECIMAL), marco de 1 px sin procesar en la convolución, Blending que deforma el fondo, Escala de grises por promedio y matrices de color sin offsets.
