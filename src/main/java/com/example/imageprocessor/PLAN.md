# PLAN DE IMPLEMENTACIÓN — ImageProcessor (JavaFX)

## 1. Visión del producto

Aplicación de escritorio en Java con JavaFX para edición no destructiva de imágenes, con interfaz oscura y profesional inspirada en Adobe Lightroom.

Capacidades principales:

- Carga de imágenes en formatos comunes (`png`, `jpg`, `jpeg`, `bmp`, `gif`).
- Edición no destructiva: la imagen original nunca se modifica en memoria.
- Aplicación parametrizable de todos los filtros implementados.
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
  - **Top**: acciones globales (Abrir, Guardar, Reset, Comparar original).
  - **Left**: panel de herramientas con controles dinámicos por filtro.
  - **Center**: área de preview con pestañas (Editor / Generador).
  - **Bottom**: barra de estado.
- Controles dinámicos: solo se muestran los parámetros del filtro seleccionado.
- Comparador antes/después con checkbox.

---

## 3. Stack técnico

| Tecnología | Versión | Rol |
|---|---|---|
| Java | 21 (LTS) | Lenguaje base |
| JavaFX | 21.0.6 | UI de escritorio |
| javafx-swing | 21.0.6 | Puente `BufferedImage ↔ Image` via `SwingFXUtils` |
| java.desktop (AWT) | JDK 21 | Procesamiento de imagen (`BufferedImage`, `Color`, `ConvolveOp`) |
| Maven | 3.x | Build y gestión de dependencias |
| JUnit Jupiter | 5.12.1 | Tests unitarios |

---

## 4. Arquitectura

### 4.1 Estructura de paquetes

```
com.example.imageprocessor
├── app/                         ← Arranque y coordinación
│   ├── ImageProcessorApp.java   ← Punto de entrada JavaFX (Application)
│   └── ui/                      ← Componentes de interfaz reutilizables
│       ├── EditorFilterPane.java
│       ├── GradientGeneratorPane.java
│       └── ImageFileChooserFactory.java
├── domain/                      ← Tipos y enumeraciones del dominio
│   ├── FilterType.java
│   ├── ConvolutionKernel.java
│   ├── GradientType.java
│   └── StretchMode.java
└── service/                     ← Lógica de procesamiento puro
    ├── ImageProcessor.java      ← Fachada pública (API estable)
    ├── ColorFilters.java        ← Filtros de color básicos
    ├── ArtisticFilters.java     ← Efectos artísticos y cuantización
    ├── ConvolutionFilters.java  ← Convolución espacial
    ├── GradientGenerator.java   ← Generación de degradados
    ├── ImageIOService.java      ← Lectura/escritura de archivos
    └── PixelMath.java           ← Utilidades matemáticas internas
```

### 4.2 Capas y responsabilidades

#### `app` — Presentación y coordinación

`ImageProcessorApp` es el orquestador principal. Mantiene el estado de sesión (`originalImage`, `processedImage`) y delega la construcción del layout a los componentes de `app/ui`. No contiene lógica de procesamiento de imagen.

`EditorFilterPane` encapsula todos los controles del panel izquierdo: selector de filtro, sliders, spinners y color pickers. Expone getters tipados para que la app lea los parámetros sin conocer los detalles de la UI. Gestiona internamente la visibilidad dinámica de controles por tipo de filtro.

`GradientGeneratorPane` encapsula la pestaña de generador completa. Se comunica con la app a través de dos callbacks: uno para entregar la imagen generada al editor, y otro para reportar mensajes de estado.

`ImageFileChooserFactory` centraliza la construcción de `FileChooser` para abrir y guardar imágenes, evitando duplicación.

#### `domain` — Tipos del dominio

Enumeraciones que tipan las opciones del sistema:

- `FilterType`: todos los filtros disponibles con su etiqueta de visualización.
- `ConvolutionKernel`: kernels predefinidos (Blur, Sharpen, Bordes, Emboss) con sus matrices.
- `GradientType`: las cinco variantes de degradado.
- `StretchMode`: modos de estiramiento de 4 bits (Binario, Decimal, Hexadecimal).

#### `service` — Procesamiento puro

`ImageProcessor` es la **fachada pública** del servicio. Expone todos los métodos estáticos con firma estable y delega internamente a las clases especializadas. Es el único punto de contacto que la capa `app` usa para procesar imágenes.

`ColorFilters` contiene los filtros de color básicos que operan canal a canal: escala de grises, negativo, brillo, ajuste HSV y umbral B/N.

`ArtisticFilters` contiene efectos que manipulan el canal alpha o realizan cuantización: vidrio esmerilado, desvanecimiento circular, retro1 (cuantización RGB), recolorización por luminancia y compresión a 4 bits con estiramiento.

`ConvolutionFilters` aplica matrices de convolución 3×3 usando `java.awt.image.ConvolveOp`.

`GradientGenerator` genera imágenes sintéticas pixel a pixel interpolando entre dos colores según el tipo de degradado seleccionado.

`PixelMath` provee utilidades matemáticas de visibilidad de paquete (`clamp`, `clamp01`, `lerp`, `quantize`) usadas internamente por los filtros para evitar duplicación.

`ImageIOService` abstrae la lectura y escritura de archivos detectando el formato por extensión.

### 4.3 Principios de diseño

- **Edición no destructiva**: `originalImage` nunca se modifica. Cada aplicación de filtro produce una nueva imagen independiente.
- **Fachada estable**: toda la capa `app` interactúa únicamente con `ImageProcessor`. Las clases internas del servicio (`ColorFilters`, `ArtisticFilters`, etc.) son de visibilidad de paquete y no forman parte de la API pública.
- **Sin estado en servicios**: todos los métodos de procesamiento son estáticos y puros — no guardan estado ni producen efectos secundarios.
- **Separación UI/lógica**: los paneles de UI solo coordinan interacciones y leen parámetros; no calculan nada sobre píxeles.

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
ImageProcessorApp.applySelectedFilter()
       ↓
ImageProcessor.<filtro>(originalImage, parámetros) → processedImage
       ↓
SwingFXUtils.toFXImage(processedImage) → ImageView (preview)
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

#### Estética retro y cuantización
| Funcionalidad | Clase | Enum |
|---|---|---|
| Retro 1 — cuantización RGB a N niveles | `ArtisticFilters` | `FilterType.RETRO1` |
| Compresión a 4 bits + estiramiento (Binario / Decimal / Hexadecimal) | `ArtisticFilters` | `FilterType.STRETCH_4_BITS` |

#### Filtros espaciales (convolución 3×3)
| Funcionalidad | Clase | Enum |
|---|---|---|
| Blur (desenfoque por promedio) | `ConvolutionFilters` | `ConvolutionKernel.BLUR` |
| Sharpen (enfoque por contraste de bordes) | `ConvolutionFilters` | `ConvolutionKernel.SHARPEN` |
| Detección de bordes | `ConvolutionFilters` | `ConvolutionKernel.EDGES` |
| Emboss (relieve 3D) | `ConvolutionFilters` | `ConvolutionKernel.EMBOSS` |

---

### ❌ Pendiente de implementar

#### Efectos de transparencia
| Funcionalidad | Descripción y ubicación sugerida |
|---|---|
| Transparencia global | Slider de alpha uniforme (0–255) aplicado a toda la imagen. Agregar `FilterType.ALPHA_GLOBAL`, implementar en `ColorFilters`, exponer slider en `EditorFilterPane`. |

#### Estética retro y cuantización
| Funcionalidad | Descripción y ubicación sugerida |
|---|---|
| Retro 2 — canales parciales ("glitch") | Cuantizar solo 1 o 2 canales seleccionados (R, G, B), dejando los demás intactos o en cero. Agregar `FilterType.RETRO2`, implementar en `ArtisticFilters`, agregar checkboxes de canal en `EditorFilterPane`. |
| Grises cuantizados con bandas | Escala de grises limitada a N niveles, generando bandas de tono marcadas. Combina `grayscale` + `quantize`. Agregar `FilterType.GRAYSCALE_QUANTIZED`, implementar en `ColorFilters`. |

#### Filtros adicionales sugeridos
| Funcionalidad | Descripción y ubicación sugerida |
|---|---|
| Sepia | Variante directa de `recolor` con tonos fijos (R=112, G=66, B=20). Agregar `FilterType.SEPIA`, implementar en `ArtisticFilters` como preset de `recolor`. No requiere controles adicionales en el panel. |
| Temperatura (frío/cálido) | Suma valores al canal B (frío) o a los canales R+G (cálido). Agregar `FilterType.TEMPERATURE`, implementar en `ColorFilters`, exponer un slider de temperatura (negativo=frío, positivo=cálido) en `EditorFilterPane`. |
| Animación / secuencia de Hue | Genera N variaciones de la imagen desplazando el canal H (Tono) gradualmente y las guarda en una carpeta. Requiere un diálogo de exportación de secuencia. Implementar en `ArtisticFilters` iterando el desplazamiento de H en el espacio HSV. |

---

## 7. Roadmap

### Fase actual — Completada ✅
- Arquitectura limpia en capas separadas (`app`, `domain`, `service`).
- UI modular con panel de filtros, panel de generador y barra de estado.
- Implementados: 5 degradados, 6 ajustes básicos, 2 efectos alpha, 2 efectos retro, 4 kernels de convolución.
- Compilación y arranque verificados con Java 21 + JavaFX 21.

### Siguiente fase — Implementación pendiente
1. **Transparencia global** — `FilterType.ALPHA_GLOBAL` + `ColorFilters` + control en `EditorFilterPane`.
2. **Retro 2 (canales parciales)** — `FilterType.RETRO2` + `ArtisticFilters` + checkboxes de canal en `EditorFilterPane`.
3. **Grises cuantizados** — `FilterType.GRAYSCALE_QUANTIZED` + combinación en `ColorFilters`.
4. **Sepia** — `FilterType.SEPIA` + preset en `ArtisticFilters.recolor`.
5. **Temperatura** — `FilterType.TEMPERATURE` + ajuste de canales en `ColorFilters` + slider en `EditorFilterPane`.
6. **Animación / secuencia de Hue** — diálogo de exportación + iteración de H en `ArtisticFilters`.
