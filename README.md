# ImageProcessor

Aplicación de escritorio en **Java 21 + JavaFX 21** para edición no destructiva de imágenes, con interfaz oscura inspirada en Adobe Lightroom.

---

## Características

- **Edición no destructiva**: la imagen original nunca se altera en memoria.
- **Amplio catálogo de filtros**: ajustes básicos, efectos artísticos, convolución espacial y retro.
- **Generador de degradados**: crea imágenes sintéticas lineales y radiales desde cero.
- **Comparador antes/después** con un solo click.
- **Exportación flexible**: detecta el formato por la extensión elegida (`png`, `jpg`, `bmp`, `gif`).

---

## Requisitos

| Requisito | Versión mínima |
|---|---|
| JDK | 21 |
| Maven | 3.6+ (o usar `mvnw` incluido) |
| Sistema operativo | Windows / macOS / Linux con soporte OpenGL |

> No se requiere instalar JavaFX por separado. Maven descarga las dependencias automáticamente.

---

## Instalación y ejecución

### 1. Clonar el repositorio

```bash
git clone <url-del-repositorio>
cd ImageProcessor
```

### 2. Compilar

```bash
./mvnw clean compile
```

En Windows:

```powershell
.\mvnw.cmd clean compile
```

### 3. Ejecutar la aplicación

```bash
./mvnw javafx:run
```

En Windows:

```powershell
.\mvnw.cmd javafx:run
```

### 4. Ejecutar los tests

```bash
./mvnw test
```

---

## Uso básico

### Abrir una imagen

1. Haz clic en **Abrir** en la barra superior.
2. Selecciona un archivo `png`, `jpg`, `jpeg`, `bmp` o `gif`.
3. La imagen se muestra en el área central (pestaña **Editor**).

### Aplicar un filtro

1. Selecciona un filtro en el ComboBox **Filtro** del panel izquierdo.
2. Ajusta los parámetros que aparecen dinámicamente (slider, spinner o color picker).
3. Haz clic en **Aplicar filtro**.

### Comparar antes y después

- Activa el checkbox **Mostrar original** para alternar entre la imagen original y la procesada.

### Resetear

- Haz clic en **Reset** para descartar los cambios y volver a la imagen original cargada.

### Guardar

1. Haz clic en **Guardar**.
2. Escribe el nombre del archivo con la extensión deseada (`.png`, `.jpg`, `.bmp`, `.gif`).
3. El formato se detecta automáticamente por la extensión.

---

## Generador de degradados

1. Navega a la pestaña **Generador**.
2. Selecciona el **Tipo** de degradado (Izquierda→Derecha, Arriba→Abajo, Radial, etc.).
3. Ajusta el **Ancho** y el **Alto** en píxeles.
4. Elige el **Color inicial** y el **Color final** con los color pickers.
5. Haz clic en **Generar degradado** para previsualizar.
6. Usa **Usar en editor** para enviar el degradado al editor y aplicarle filtros.

---

## Filtros disponibles

| Categoría | Filtro | Parámetro |
|---|---|---|
| Ajustes básicos | Escala de grises | — |
| Ajustes básicos | Negativo | — |
| Ajustes básicos | Brillo / Oscuridad | Slider de valor (−100 a +100) |
| Ajustes básicos | Ajuste HSV | Sliders de Saturación y Valor |
| Ajustes básicos | Blanco y negro (umbral) | Slider de umbral (0–255) |
| Ajustes básicos | Recolorización | Color picker de tinte |
| Transparencia | Vidrio esmerilado | — |
| Transparencia | Desvanecimiento circular | — |
| Retro y cuantización | Retro 1 (cuantización RGB) | Spinner de niveles (2–255) |
| Retro y cuantización | Reducción 4 bits + estiramiento | Combo: Binario / Decimal / Hexadecimal |
| Convolución | Convolución 3×3 | Combo: Blur / Sharpen / Bordes / Emboss |

---

## Estructura del proyecto

```
src/main/java/com/example/imageprocessor/
├── app/
│   ├── ImageProcessorApp.java       # Punto de entrada (JavaFX Application)
│   └── ui/
│       ├── EditorFilterPane.java    # Panel de filtros y parámetros
│       ├── GradientGeneratorPane.java  # Panel del generador de degradados
│       └── ImageFileChooserFactory.java  # Fábrica de diálogos de archivo
├── domain/
│   ├── FilterType.java              # Enum de filtros disponibles
│   ├── ConvolutionKernel.java       # Kernels predefinidos
│   ├── GradientType.java            # Tipos de degradado
│   └── StretchMode.java             # Modos de estiramiento
└── service/
    ├── ImageProcessor.java          # Fachada pública del procesamiento
    ├── ColorFilters.java            # Filtros de color básicos
    ├── ArtisticFilters.java         # Efectos artísticos y cuantización
    ├── ConvolutionFilters.java      # Convolución espacial
    ├── GradientGenerator.java       # Generación de degradados
    ├── ImageIOService.java          # Lectura y escritura de archivos
    └── PixelMath.java               # Utilidades matemáticas internas
```

---

## Tecnologías

- **Java 21** — lenguaje base
- **JavaFX 21.0.6** — interfaz gráfica
- **AWT / java.desktop** — procesamiento de `BufferedImage`
- **SwingFXUtils** — puente entre `BufferedImage` y `javafx.scene.image.Image`
- **Maven 3** — build y dependencias
- **JUnit Jupiter 5.12.1** — tests unitarios

