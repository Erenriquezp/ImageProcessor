# PLAN DE IMPLEMENTACIÓN — ImageProcessor (JavaFX)

## 1. Visión del producto

Aplicación de escritorio en Java con JavaFX para edición no destructiva de imágenes, con interfaz oscura y profesional inspirada en Adobe Lightroom / DaVinci Resolve.

Capacidades principales:

- Carga de imágenes en formatos comunes (`png`, `jpg`, `jpeg`, `bmp`, `gif`).
- Edición no destructiva: la imagen original nunca se modifica en memoria.
- Aplicación parametrizable y apilable de todos los filtros implementados.
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

### 2.4 Experiencia de usuario

- Layout oscuro profesional:
  - **Top**: acciones globales (Abrir, Guardar, Reset, Comparar original, Zoom).
  - **Left**: panel de herramientas con controles dinámicos por filtro.
  - **Center**: área de preview con pestañas (Editor / Generador).
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
| java.desktop (AWT) | JDK 21 | Procesamiento de imagen (`BufferedImage`, `Color`, `ConvolveOp`) |
| Ikonli (FontAwesome 6) | 12.4.0 | Iconos vectoriales en la toolbar |
| Maven | 3.x | Build y gestión de dependencias |
| JUnit Jupiter | 5.12.1 | Tests unitarios |

---

## 4. Arquitectura

### 4.1 Estructura de paquetes

```
com.example.imageprocessor
├── app/                             ← Arranque y coordinación
│   ├── ImageProcessorApp.java       ← Coordinador principal (Application)
│   └── ui/                          ← Componentes de interfaz reutilizables
│       ├── EditorPreviewPane.java   ← Panel de preview, compare mode y zoom
│       ├── TopBar.java              ← Barra de herramientas y controles de zoom
│       ├── EditorFilterPane.java    ← Panel izquierdo: filtros y parámetros
│       ├── GradientGeneratorPane.java
│       └── ImageFileChooserFactory.java
├── domain/                          ← Tipos y enumeraciones del dominio
│   ├── FilterType.java
│   ├── ConvolutionKernel.java
│   ├── GradientType.java
│   └── StretchMode.java
└── service/                         ← Lógica de procesamiento puro
    ├── ImageProcessor.java          ← Fachada pública (API estable)
    ├── ColorFilters.java
    ├── ArtisticFilters.java
    ├── ConvolutionFilters.java
    ├── GradientGenerator.java
    ├── ImageIOService.java
    └── PixelMath.java
```

### 4.2 Capas y responsabilidades

#### `app` — Presentación y coordinación

`ImageProcessorApp` es el coordinador principal. Mantiene el estado de sesión (`originalImage`, `processedImage`) y cablea los componentes de UI mediante callbacks. No contiene lógica de procesamiento de imagen.

`EditorPreviewPane` encapsula todo el área de visualización: los dos `ScrollPane` (original / resultado), el modo comparar 50/50 con borde de foco, y el sistema de zoom independiente por panel.

`TopBar` construye la barra superior recibiendo callbacks como `Runnable`. Embebe el `Label` del indicador de zoom propiedad de `EditorPreviewPane`.

`EditorFilterPane` encapsula los controles del panel izquierdo. Expone getters tipados para que la app lea los parámetros sin conocer los detalles de la UI.

`GradientGeneratorPane` se comunica con la app a través de dos callbacks: entregar la imagen generada al editor, y reportar mensajes de estado.

`ImageFileChooserFactory` centraliza la construcción de `FileChooser`.

#### `domain` — Tipos del dominio

Enumeraciones que tipan las opciones del sistema:
- `FilterType`, `ConvolutionKernel`, `GradientType`, `StretchMode`.

#### `service` — Procesamiento puro

`ImageProcessor` es la **fachada pública**. Todos los métodos son estáticos y puros; delega a las clases internas de paquete.

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
ImageIOService.read(file) → originalImage
       ↓
Usuario selecciona filtro + ajusta parámetros en EditorFilterPane
       ↓
ImageProcessorApp.applySelectedFilter()        (o preview instantáneo para filtros simples)
       ↓
ImageProcessor.<filtro>(processedImage*, parámetros) → nuevo processedImage
       ↓                                              (* o originalImage en modo no apilado)
EditorPreviewPane.showProcessed(processedImage) → ImageView
       ↓
Usuario guarda
       ↓
ImageIOService.write(processedImage, file)
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

#### Filtros espaciales (convolución 3×3)
| Funcionalidad | Clase | Enum |
|---|---|---|
| Blur (desenfoque por promedio) | `ConvolutionFilters` | `ConvolutionKernel.BLUR` |
| Sharpen (enfoque por contraste de bordes) | `ConvolutionFilters` | `ConvolutionKernel.SHARPEN` |
| Detección de bordes | `ConvolutionFilters` | `ConvolutionKernel.EDGES` |
| Emboss (relieve 3D) | `ConvolutionFilters` | `ConvolutionKernel.EMBOSS` |

#### UX / Interfaz
| Funcionalidad | Descripción |
|---|---|
| Zoom independiente por panel | Scroll de rueda, botones −/+/fit en toolbar. Cada panel (original/resultado) mantiene su propio nivel de zoom (100 %–800 %). |
| Click-to-focus en modo comparar | Clic en un panel lo marca como activo (borde azul `#4a8fd6`); los botones de zoom actúan sobre él. |
| Modo comparar 50/50 | Divide el área central en dos `ScrollPane` enlazados al 50 % del ancho con borde de foco. |
| Checkboxes de canal con estilos premium | Retro 2 muestra checkboxes coloreados (R rojo, G verde, B azul) con gradiente de fondo al activarse. |
| Supresión de warnings JVM | `--enable-native-access=javafx.graphics` + `--sun-misc-unsafe-memory-access=allow` en `pom.xml` y `.mvn/jvm.config`. |
| Refactorización en capas SRP | `ImageProcessorApp` (coordinador) · `EditorPreviewPane` (preview + zoom) · `TopBar` (barra de herramientas). |

---

### ❌ Pendiente de implementar

#### Filtros adicionales
| Funcionalidad | Descripción y ubicación sugerida |
|---|---|
| Sepia | Preset de `recolor` con tonos fijos (R=112, G=66, B=20). Agregar `FilterType.SEPIA`, implementar en `ArtisticFilters`. No requiere controles adicionales. |
| Temperatura (frío/cálido) | Suma al canal B (frío) o a R+G (cálido). Agregar `FilterType.TEMPERATURE` en `ColorFilters`, slider de temperatura (−100 frío / +100 cálido) en `EditorFilterPane`. |
| Animación / secuencia de Hue | Genera N imágenes desplazando H en el espacio HSV y las guarda en carpeta. Diálogo de exportación + iteración en `ArtisticFilters`. |

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
- Arquitectura limpia en capas separadas (`app`, `domain`, `service`).
- UI modular: `EditorPreviewPane`, `TopBar`, `EditorFilterPane`, `GradientGeneratorPane`.
- **15 filtros** implementados: 6 ajustes básicos, 3 transparencia, 4 retro/cuantización, 4 kernels de convolución.
- **5 degradados** sintéticos.
- Zoom independiente por panel (100 %–800 %), compare mode 50/50 con borde de foco.
- Checkboxes premium de canal para Retro 2.
- Supresión de warnings JVM de JavaFX en `pom.xml` y `.mvn/jvm.config`.
- Documentación completa: README.md y PLAN.md actualizados.

### Fase 2 — Próxima iteración
1. **Pipeline de filtros + historial** — pila de resultados + botón Revertir en `TopBar`.
2. **Preview instantáneo** — campo `instantPreview` en `FilterType` + listeners en `EditorFilterPane`.
3. **Mejoras de estilo del panel de herramientas** — labels de valor, tarjetas, indicador de categoría.
4. **Sepia** — preset de recolor, sin parámetros.
5. **Temperatura** — slider frío/cálido en `ColorFilters`.

### Fase 3 — Mejoras avanzadas
6. **Animación / secuencia de Hue** — diálogo de exportación de secuencia de frames.
