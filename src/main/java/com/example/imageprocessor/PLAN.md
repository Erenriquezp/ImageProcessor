# PLAN DE IMPLEMENTACIÓN — ImageProcessor (JavaFX)

## 1. Visión del producto

Aplicación de escritorio en Java con JavaFX para edición no destructiva de imágenes, con interfaz oscura y profesional inspirada en Adobe Lightroom / DaVinci Resolve.

Capacidades principales:

- Carga de imágenes en formatos comunes (`png`, `jpg`, `jpeg`, `bmp`, `gif`).
- Edición no destructiva: la imagen original nunca se modifica en memoria.
- Aplicación parametrizable de todos los filtros implementados.
- Análisis de distribución de color mediante histograma RGB.
- Composición de imágenes mediante blending de dos o tres capas con pesos independientes.
- Generación de imágenes sintéticas (degradados lineales y radial).
- Exportación en formato detectado automáticamente por extensión.

---

## 2. Objetivos funcionales

### 2.1 Carga, visualización y exportación

- Abrir imagen con `FileChooser` filtrando por extensión.
- Vista previa central con relación de aspecto preservada.
- Exportar al formato detectado por la extensión del archivo destino (`png`, `jpg`, `bmp`, `gif`).

### 2.2 Módulo de edición (filtros)

Ver sección **6. Estado de implementación** para el detalle completo.

### 2.3 Generador de imágenes

- Degradados lineales en 4 direcciones y degradado radial.
- Control de dimensiones (ancho/alto), color inicial y color final.
- Posibilidad de enviar la imagen generada directamente al editor.

### 2.4 Histograma RGB

- Análisis de la distribución de frecuencias de los canales R, G y B.
- Genera una imagen de 800×600 px con las tres curvas superpuestas sobre fondo negro.
- El eje Y se normaliza al pico máximo global de los tres canales.
- Botón **"Usar en editor"** para cargar el gráfico como imagen de trabajo y exportarlo.

### 2.5 Blending (2 imágenes)

- Mezcla el estado actual del editor (frente) con una imagen de fondo cargada por el usuario.
- Factor alpha ajustable en tiempo real con slider: `0 → frente puro`, `1 → fondo puro`.
- Preview automático al cargar el fondo; si falta alguna imagen, un overlay contextual indica exactamente qué cargar.

### 2.6 Triple Blending (3 imágenes)

- Combina tres imágenes (frente del editor + dos fondos) con pesos independientes por canal.
- Fórmula: `canal = clamp(ch₁·α₁ + ch₂·α₂ + ch₃·α₃)` — pesos directos, no normalizados.
- Los tres sliders actualizan el preview en tiempo real.
- Preview progresivo: cubre las 8 combinaciones posibles de disponibilidad de imágenes con overlays informativos.

### 2.7 Experiencia de usuario

- Layout oscuro profesional:
  - **Top**: acciones globales (Abrir, Guardar, Reset, Comparar original, Zoom).
  - **Left**: panel de herramientas con controles dinámicos por filtro.
  - **Center**: área de preview con 5 pestañas (Editor · Generador · Histograma · Blending · Triple Blending).
  - **Bottom**: barra de estado.
- Controles dinámicos: solo se muestran los parámetros del filtro seleccionado.
- Comparador antes/después con zoom independiente por panel.

---

## 3. Stack técnico

| Tecnología | Versión | Rol |
|---|---|---|
| Java | 21 (LTS) | Lenguaje base |
| JavaFX | 21.0.6 | UI de escritorio |
| javafx-swing | 21.0.6 | Puente `BufferedImage ↔ Image` via `SwingFXUtils` |
| java.desktop (AWT) | JDK 21 | Procesamiento de imagen (`BufferedImage`, `Color`, `ConvolveOp`, `Graphics2D`) |
| Ikonli (FontAwesome 6) | 12.4.0 | Iconos vectoriales en la toolbar |
| Maven | 3.x | Build y gestión de dependencias |
| JUnit Jupiter | 5.12.1 | Tests unitarios |

---

## 4. Arquitectura

### 4.1 Estructura de paquetes

```
com.example.imageprocessor
├── app/                               ← Arranque y coordinación
│   ├── ImageProcessorApp.java         ← Coordinador principal (Application)
│   └── ui/                            ← Componentes de interfaz reutilizables
│       ├── EditorPreviewPane.java     ← Panel de preview, compare mode y zoom
│       ├── TopBar.java                ← Barra de herramientas y controles de zoom
│       ├── EditorFilterPane.java      ← Panel izquierdo: filtros y parámetros
│       ├── GradientGeneratorPane.java ← Pestaña generador de degradados
│       ├── HistogramPane.java         ← Pestaña análisis de histograma RGB
│       ├── BlendingPane.java          ← Pestaña blending de 2 imágenes
│       ├── TripleBlendingPane.java    ← Pestaña triple blending de 3 imágenes
│       └── ImageFileChooserFactory.java
├── domain/                            ← Tipos y enumeraciones del dominio
│   ├── FilterType.java                ← Enum con label + dot color por categoría
│   ├── ConvolutionKernel.java
│   ├── GradientType.java
│   └── StretchMode.java
└── service/                           ← Lógica de procesamiento puro
    ├── ImageProcessor.java            ← Fachada pública (API estable)
    ├── ColorFilters.java              ← Filtros de color básicos + matrices de color
    ├── ArtisticFilters.java           ← Efectos artísticos y cuantización
    ├── ConvolutionFilters.java        ← Kernels de convolución 3×3
    ├── HistogramService.java          ← Generación del gráfico histograma RGB
    ├── BlendingService.java           ← Alpha blending de 2 y 3 imágenes
    ├── GradientGenerator.java         ← Degradados sintéticos
    ├── ImageIOService.java            ← Lectura y escritura de archivos
    └── PixelMath.java                 ← Utilidades internas (clamp, lerp, quantize)
```

### 4.2 Capas y responsabilidades

#### `app` — Presentación y coordinación

`ImageProcessorApp` es el coordinador principal. Mantiene el estado de sesión (`originalImage`, `processedImage`) y cablea los componentes de UI mediante callbacks. No contiene lógica de procesamiento de imagen.

`EditorPreviewPane` encapsula todo el área de visualización: los dos `ScrollPane` (original / resultado), el modo comparar 50/50 con borde de foco, y el sistema de zoom independiente por panel.

`TopBar` construye la barra superior recibiendo callbacks como `Runnable`. Embebe el `Label` del indicador de zoom propiedad de `EditorPreviewPane`.

`EditorFilterPane` encapsula los controles del panel izquierdo. Expone getters tipados para que la app lea los parámetros sin conocer los detalles de la UI.

`GradientGeneratorPane`, `HistogramPane`, `BlendingPane` y `TripleBlendingPane` siguen el mismo patrón de cableado: reciben todos sus colaboradores externos como callbacks (`Supplier`, `Consumer`) en el constructor y no mantienen referencia a `ImageProcessorApp` ni a `Stage`.

`ImageFileChooserFactory` centraliza la construcción de `FileChooser`.

#### `domain` — Tipos del dominio

Enumeraciones que tipan las opciones del sistema:
- `FilterType` (con dot color por categoría), `ConvolutionKernel`, `GradientType`, `StretchMode`.

#### `service` — Procesamiento puro

`ImageProcessor` es la **fachada pública**. Todos los métodos son estáticos y puros; delega a las clases internas de paquete.

`BlendingService` expone dos métodos:
- `blend()` — mezcla lineal α de dos imágenes usando `PixelMath.lerp()`.
- `tripleBlend()` — suma ponderada directa de tres imágenes, reusando el helper privado `scaleToFit()`.

`HistogramService` calcula las frecuencias de los 256 niveles por canal y renderiza el gráfico con `Graphics2D` con anti-aliasing y capas round.

### 4.3 Principios de diseño

- **Edición no destructiva**: `originalImage` nunca se modifica.
- **Fachada estable**: la capa `app` interactúa únicamente con `ImageProcessor`.
- **Sin estado en servicios**: métodos estáticos y puros.
- **Separación UI / lógica**: los paneles no calculan nada sobre píxeles.
- **SRP**: cada clase de UI tiene una sola razón para cambiar.
- **Dependency Inversion**: `TopBar` recibe callbacks (`Runnable`), no referencias directas a la app.

---

## 5. Flujo de datos principal

```
Usuario selecciona archivo
       ↓
ImageProcessorApp.openImage()
       ↓
ImageIOService.read(file) → originalImage / processedImage
       ↓
Usuario selecciona filtro + ajusta parámetros en EditorFilterPane
       ↓
ImageProcessorApp.applySelectedFilter()
       ↓
ImageProcessor.<filtro>(originalImage, parámetros) → nuevo processedImage
       ↓
EditorPreviewPane.showProcessed(processedImage) → ImageView
       ↓
Usuario guarda → ImageIOService.write(processedImage, file)

── Histograma ────────────────────────────────────────────────────────────
processedImage → HistogramPane → ImageProcessor.generateHistogram()
              → gráfico 800×600 en pestaña Histograma
              → (opc.) Usar en editor → setSessionImages(histograma)

── Blending ──────────────────────────────────────────────────────────────
processedImage + backgroundImage → BlendingPane (slider alpha en tiempo real)
  → ImageProcessor.blend(fg, bg, alpha) → preview
  → (opc.) Usar en editor → setSessionImages(blendResult)

── Triple Blending ────────────────────────────────────────────────────────
processedImage + bg1 + bg2 → TripleBlendingPane (3 sliders en tiempo real)
  → ImageProcessor.tripleBlend(fg, bg1, bg2, α₁, α₂, α₃) → preview
  → (opc.) Usar en editor → setSessionImages(tripleBlendResult)
```

---

## 6. Estado de implementación

### ✅ Implementado y funcional

#### Generadores
| Funcionalidad | Clase | Enum |
|---|---|---|
| Degradado izquierda→derecha | `GradientGenerator` | `GradientType.LEFT_TO_RIGHT` |
| Degradado derecha→izquierda | `GradientGenerator` | `GradientType.RIGHT_TO_LEFT` |
| Degradado arriba→abajo | `GradientGenerator` | `GradientType.TOP_TO_BOTTOM` |
| Degradado abajo→arriba | `GradientGenerator` | `GradientType.BOTTOM_TO_TOP` |
| Degradado radial | `GradientGenerator` | `GradientType.RADIAL` |

#### Ajustes básicos
| Funcionalidad | Clase | Enum |
|---|---|---|
| Escala de grises | `ColorFilters` | `FilterType.GRAYSCALE` |
| Blanco y negro por umbral | `ColorFilters` | `FilterType.BW_THRESHOLD` |
| Negativo | `ColorFilters` | `FilterType.NEGATIVE` |
| Brillo / oscuridad (con clamping) | `ColorFilters` | `FilterType.BRIGHTNESS` |
| Ajuste HSV (saturación + valor) | `ColorFilters` | `FilterType.HSV` |
| Recolorización por luminancia BT.709 | `ArtisticFilters` | `FilterType.RECOLOR` |

#### Efectos de transparencia (canal alpha)
| Funcionalidad | Clase | Enum |
|---|---|---|
| Vidrio esmerilado (alpha por brillo local) | `ArtisticFilters` | `FilterType.FROSTED` |
| Desvanecimiento circular / viñeta | `ArtisticFilters` | `FilterType.CIRCULAR_FADE` |
| Transparencia global (alpha uniforme) | `ColorFilters` | `FilterType.ALPHA_GLOBAL` |

#### Estética retro y cuantización
| Funcionalidad | Clase | Enum |
|---|---|---|
| Retro 1 — cuantización RGB a N niveles | `ArtisticFilters` | `FilterType.RETRO1` |
| Compresión a 4 bits + estiramiento (Binario / Decimal / Hexadecimal) | `ArtisticFilters` | `FilterType.STRETCH_4_BITS` |
| Retro 2 — canales parciales ("glitch") | `ArtisticFilters` | `FilterType.RETRO2` |
| Grises cuantizados con bandas | `ColorFilters` | `FilterType.GRAYSCALE_QUANTIZED` |

#### Filtros espaciales (convolución 3×3)  `#9a6adc`
| Funcionalidad | Clase | Enum |
|---|---|---|
| Blur (desenfoque por promedio) | `ConvolutionFilters` | `ConvolutionKernel.BLUR` |
| Sharpen (enfoque por contraste de bordes) | `ConvolutionFilters` | `ConvolutionKernel.SHARPEN` |
| Detección de bordes | `ConvolutionFilters` | `ConvolutionKernel.EDGES` |
| Emboss (relieve 3D) | `ConvolutionFilters` | `ConvolutionKernel.EMBOSS` |

#### Matrices de color  `#e84a8f`
| Filtro | Enum | Descripción |
|---|---|---|
| Sepia | `SEPIA` | Matriz W3C estándar — tonos cálidos marrones |
| Tono Frío | `COOL_TONE` | Atenúa canal rojo, amplifica azul |
| Tono Cálido | `WARM_TONE` | Amplifica rojo, recorta azul |
| Polaroid | `POLAROID` | Alto contraste, leve cross-process |
| Kodachrome | `KODACHROME` | Emulación película Kodak — altas luces cálidas, sombras profundas |

> Todas usan el helper privado `colorMatrix(BufferedImage, float[][])` en `ColorFilters`. Sin parámetros adicionales — aparecen en el ComboBox con dot rosa `#e84a8f`.

#### Análisis — Histograma RGB
| Funcionalidad | Clase | Descripción |
|---|---|---|
| Histograma RGB | `HistogramService` | 256 niveles/canal; curvas anti-aliasing R/G/B sobre fondo negro 800×600 px; eje Y normalizado al pico global |

**UI** — pestaña **"Histograma"** (`HistogramPane`): analiza `processedImage`, botón **"Generar histograma"** + botón **"Usar en editor"**.

#### Composición — Blending de 2 imágenes
| Funcionalidad | Clase | Fórmula |
|---|---|---|
| Alpha blending | `BlendingService.blend()` | `result = lerp(fg, bg, alpha)` vía `PixelMath.lerp()` |

**UI** — pestaña **"Blending"** (`BlendingPane`): slider alpha `[0.0–1.0]` en tiempo real; preview automático al cargar fondo; 4 overlays contextuales según qué imagen(es) faltan; canal alpha del frente preservado.

#### Composición — Triple Blending de 3 imágenes
| Funcionalidad | Clase | Fórmula |
|---|---|---|
| Triple blending | `BlendingService.tripleBlend()` | `canal = clamp(ch₁·α₁ + ch₂·α₂ + ch₃·α₃)` |

**UI** — pestaña **"Triple Blending"** (`TripleBlendingPane`): 3 sliders de peso independientes (α₁=0.5, α₂=0.3, α₃=0.2); re-blend en tiempo real; preview progresivo para las 8 combinaciones de disponibilidad de imágenes.

#### UX / Interfaz
| Funcionalidad | Descripción |
|---|---|
| Zoom independiente por panel | Scroll de rueda, botones −/+/fit en toolbar. Rango 100 %–800 %. |
| Click-to-focus en modo comparar | Clic activa el panel (borde azul `#4a8fd6`); los botones de zoom actúan sobre él. |
| Modo comparar 50/50 | `ScrollPane` doble con divisor y etiquetas ORIGINAL / RESULTADO. |
| Checkboxes de canal coloreados | Retro 2: checkboxes con gradiente R/G/B al activarse. |
| Dot color en ComboBox de filtros | Indicador de categoría coloreado junto a cada filtro. |
| Overlay contextual en paneles de composición | Mensajes flotantes en `BlendingPane` y `TripleBlendingPane` mientras faltan imágenes. |
| 5 pestañas en el área central | Editor · Generador · Histograma · Blending · Triple Blending. |
| Dark-theme en sub-ventanas | Listener de `Window.getWindows()` inyecta CSS en cualquier `Stage` secundario. |
| Supresión de warnings JVM | `--enable-native-access=javafx.graphics` + `--sun-misc-unsafe-memory-access=allow` en `pom.xml`. |

---

### ❌ Pendiente de implementar

#### Filtros adicionales
| Funcionalidad | Descripción |
|---|---|
| Temperatura (frío/cálido) | Slider −100/+100: suma al canal B (frío) o a R+G (cálido). `FilterType.TEMPERATURE` en `ColorFilters`. |
| Animación / secuencia de Hue | N imágenes desplazando H en HSV, exportadas a carpeta. Diálogo de exportación + iteración en `ArtisticFilters`. |

---

#### Pipeline de filtros (apilado)

**Objetivo**: permitir aplicar varios filtros secuencialmente — cada filtro actúa sobre el resultado del anterior — y poder deshacer paso a paso hasta la imagen original.

**Plan de implementación**:

1. **Historial de resultados** (`FilterHistoryService` o lista en `ImageProcessorApp`):
   - Mantener un `Deque<BufferedImage> filterHistory` (pila LIFO, máx. ~20 entradas).
   - Cada vez que se aplica un filtro, `process(processedImage)` y `filterHistory.push(processedImage)`.
   - `originalImage` sigue inalterada y es el fondo de la pila.

2. **Botón "Revertir último filtro"** en `TopBar`:
   - Icono `fas-undo`. Activo solo si hay entradas en la pila.
   - Al pulsarlo: `processedImage = filterHistory.pop()` y refresca la vista.
   - Si la pila queda vacía, `processedImage = originalImage`.

3. **Indicador de pila** en la barra de estado o en `TopBar`:
   - Texto tipo `"Filtros aplicados: 3"` que se actualiza en cada push/pop.

4. **Archivos a modificar**:
   - `ImageProcessorApp.java` — añadir `filterHistory`, lógica de push/pop, callback `onUndo`.
   - `TopBar.java` — añadir botón Revertir con referencia a `onUndo` y `BooleanProperty undoEnabled`.
   - `EditorFilterPane` — sin cambios (los filtros siguen invocándose igual).

---

#### Preview instantáneo para filtros simples

**Objetivo**: los filtros sin parámetros o con parámetros de slider/spinner muestran el efecto en tiempo real al mover el control; el botón **Aplicar filtro** queda reservado para operaciones más costosas o para confirmar filtros avanzados antes de añadirlos al historial.

**Criterio de clasificación**:

| Tipo | Comportamiento | Filtros |
|---|---|---|
| **Simple / instantáneo** | El preview se actualiza en tiempo real al cambiar el parámetro | `GRAYSCALE`, `NEGATIVE`, `BRIGHTNESS`, `BW_THRESHOLD`, `ALPHA_GLOBAL`, `GRAYSCALE_QUANTIZED`, `SEPIA` (futuro) |
| **Avanzado / manual** | Requiere pulsar **Aplicar filtro** para confirmar y añadir al historial | `HSV`, `RETRO1`, `RETRO2`, `RECOLOR`, `STRETCH_4_BITS`, `CONVOLUTION`, `FROSTED`, `CIRCULAR_FADE`, `TEMPERATURE` (futuro) |

**Plan de implementación**:

1. **Clasificación en `FilterType`**: añadir un campo `boolean instantPreview` al enum.
   ```java
   GRAYSCALE("Escala de grises", true),
   BRIGHTNESS("Brillo", true),
   RETRO1("Retro1", false), ...
   ```

2. **Listeners en `EditorFilterPane`**: para cada control de filtro simple, exponer un método `setOnParamChanged(Runnable callback)` que la app registre.

3. **`ImageProcessorApp`**: al registrar el callback, ejecutar `applyPreview()` (aplica el filtro sobre `originalImage` al igual que ahora pero SIN añadir al historial). `applySelectedFilter()` sigue siendo el único punto que hace `filterHistory.push()`.

4. **Archivos a modificar**:
   - `FilterType.java` — añadir campo `instantPreview`.
   - `EditorFilterPane.java` — exponer `setOnParamChanged(Runnable)` y conectar listeners a los sliders/spinners de filtros simples.
   - `ImageProcessorApp.java` — `applyPreview()` sin historial; `applySelectedFilter()` con historial.

---

#### Mejoras de estilo en el panel de herramientas

**Objetivo**: elevar la calidad visual del panel izquierdo al nivel del resto de la interfaz.

**Plan de implementación**:

1. **Separadores visuales entre secciones de parámetros**:
   - Añadir un `Separator` con `opacity: 0.2` entre el selector de filtro y la zona de parámetros.
   - Añadir un título de sección dinamico (ej. `"PARÁMETROS"`) que aparezca solo cuando hay parámetros visibles.

2. **Labels de valor en tiempo real junto a cada slider**:
   - Cada `Slider` muestra su valor actual a la derecha (ej. `"Brillo: +40"`).
   - Implementar con un `Label` enlazado mediante `Bindings.format()`.

3. **Botón Reset por parámetro**:
   - Pequeño botón circular `↺` junto a cada control que restaura el valor por defecto.

4. **Agrupación visual con tarjetas**:
   - Cada `VBox` de sección (`brightnessBox`, `hsvBox`, etc.) recibe estilo de "tarjeta" (`-fx-background-color`, `border-radius`, `padding`) para separarse visualmente.

5. **Indicador del filtro activo**:
   - El `ComboBox` de filtros muestra un punto de color a la izquierda del ítem seleccionado según la categoría (azul = básico, naranja = retro, verde = transparencia, púrpura = convolución).

6. **Archivos a modificar**:
   - `EditorFilterPane.java` — añadir labels de valor, botones reset y grupos visuales.
   - `styles.css` — añadir estilos `.filter-card`, `.param-value-label`, `.param-reset-btn`.

---

## 7. Roadmap

### Fase 1 — Completada ✅
- Arquitectura limpia en 3 capas (`app`, `domain`, `service`).
- UI modular con 5 componentes de panel.
- **15 filtros** clásicos (ajustes básicos, transparencia, retro/cuantización, convolución) + **5 degradados** sintéticos.
- Zoom 100 %–800 %, compare mode 50/50, checkboxes premium de canal.
- Supresión de warnings JVM en `pom.xml` y `.mvn/jvm.config`.

### Fase 2 — Completada ✅
- **5 filtros de matrices de color** (Sepia, Tono Frío, Tono Cálido, Polaroid, Kodachrome) con helper genérico `colorMatrix()` y nueva categoría dot `#e84a8f`.
- **Histograma RGB** (`HistogramService` + `HistogramPane`).
- **Blending** (`BlendingService.blend()` + `BlendingPane`) con preview automático, overlay contextual en tiempo real y 4 mensajes de guía.
- **Triple Blending** (`BlendingService.tripleBlend()` + `TripleBlendingPane`) con 3 sliders, preview progresivo y 8 estados de overlay.
- Total: **20 filtros** en el editor · **3 herramientas** de análisis/composición · **5 pestañas** en el área central.

### Fase 3 — Próxima iteración
1. **Pipeline de filtros + historial** — pila LIFO `Deque<BufferedImage>` + botón **"↩ Revertir"** en `TopBar`.
2. **Preview instantáneo** — campo `instantPreview` en `FilterType` + `setOnParamChanged(Runnable)` en `EditorFilterPane`.
3. **Temperatura** — slider frío/cálido en `ColorFilters` + `FilterType.TEMPERATURE`.

### Fase 4 — Mejoras avanzadas
4. **Animación / secuencia de Hue** — exportación de N frames desplazando H en HSV.
