<div align="center">

# Rora Studio

Editor de fotos de escritorio, no destructivo, con ajustes en luz lineal y pila de pasos editable.

[![Java](https://img.shields.io/badge/Java-25-orange)](https://openjdk.org/)
[![JavaFX](https://img.shields.io/badge/JavaFX-25-1f8acb)](https://openjfx.io/)
[![Licencia](https://img.shields.io/badge/licencia-MIT-green)](LICENSE)
[![CI](https://github.com/Erenriquezp/ImageProcessor/actions/workflows/ci.yml/badge.svg)](https://github.com/Erenriquezp/ImageProcessor/actions/workflows/ci.yml)

![Captura de pantalla de Rora Studio](docs/img/captura.png)

<sub>Foto de la captura: retrato oficial de la NASA (S69-31741), dominio público, vía Wikimedia Commons.</sub>

</div>

---

## Características

- **Edición no destructiva**: cada ajuste es un paso de una receta; la imagen original no se toca.
- **Ajustes básicos**: exposición, contraste, luces, sombras, blancos, negros, temperatura, tinte, intensidad y saturación, en una sola pasada en luz lineal.
- **Pila de ajustes**: activar, reordenar, borrar y volver a editar cualquier paso.
- **Comparar**: cortina arrastrable, lado a lado, original, o mantener una tecla.
- **Histograma en vivo** con avisos de luces y sombras recortadas, también sobre la foto.
- **Recortar y rotar**: asas, regla de tercios, proporciones fijas, enderezar, voltear y redimensionar.
- **Filtros**: 15 entradas con buscador.
- **Abrir fotos reales**: JPEG, PNG, TIFF, WebP, BMP y GIF, con perfiles de color, orientación EXIF, CMYK, grises y 16 bits.
- **Exportar**: JPEG, PNG y TIFF con calidad, límite de lado y de peso, plantillas de nombre, estimación de tamaño y escritura atómica.
- **Wallpapers**: una foto se convierte en fondos con la resolución exacta de varios dispositivos a la vez: encuadre automático que no corta caras, relleno desenfocado cuando la proporción no encaja, aviso de calidad y zonas seguras del móvil. Ver [docs/WALLPAPERS.md](docs/WALLPAPERS.md).
- **Mejorar con IA**: ampliación ×2 o ×4 con un modelo local (Real-ESRGAN), sin red y solo cuando lo pides. Al exportar, el archivo puede indicar que se usó IA. Ver [docs/AI.md](docs/AI.md).
- **Privacidad**: por defecto se elimina la ubicación, y la miniatura incrustada no se copia nunca.
- **Trabajo en segundo plano**: la interfaz no se bloquea con fotos de 24 MP.

## Descargar e instalar (Windows)

Descarga la última versión en [Releases](https://github.com/Erenriquezp/ImageProcessor/releases). Hay dos formatos:

| Archivo | Para qué |
|---|---|
| `RoraStudio-X.Y.Z-win-x64.msi` | Instalador. No pide permisos de administrador: se instala solo para tu usuario en `%LOCALAPPDATA%\Programs\Rora Studio`, con acceso en el menú Inicio y, si quieres, en el escritorio |
| `RoraStudio-X.Y.Z-win-x64.zip` | Portable. Descomprímelo donde quieras y abre `Rora Studio\Rora Studio.exe` |
| `SHA256SUMS` | Huellas para comprobar que la descarga está íntegra |

Para abrir una foto directamente, arrástrala sobre el icono o el acceso directo.

**Aviso de SmartScreen.** Los archivos no están firmados todavía, así que Windows puede mostrar "Windows protegió su PC" con un editor desconocido. Comprueba primero la huella y, si coincide, pulsa **Más información → Ejecutar de todas formas**.

**Comprobar la huella**, en PowerShell, en la carpeta de descargas:

```powershell
Get-FileHash .\RoraStudio-X.Y.Z-win-x64.msi -Algorithm SHA256
```

El valor (en minúsculas) debe ser igual al de la línea correspondiente de `SHA256SUMS`. El ZIP es reproducible: compilado desde la misma etiqueta da exactamente la misma huella.

**Actualizar.** Instala el MSI nuevo encima: sustituye al anterior y conserva tus datos. **Desinstalar**: Configuración → Aplicaciones. Tus registros, tamaños de Wallpapers y preferencias se quedan; [docs/PRIVACY.md](docs/PRIVACY.md) explica dónde están y cómo borrarlos.

### Requisitos para usarla

| | Mínimo | Recomendado |
|---|---|---|
| Sistema | Windows 10 u 11 de 64 bits (x64) | Windows 11 |
| Memoria | 4 GB | 8 GB o más para fotos grandes y la IA |
| Disco | 90 MB para el programa | Espacio para tus exportaciones |
| GPU | No hace falta | — |

Incluye su propio Java y las bibliotecas que necesita: no hay que instalar nada más. Si el motor de IA no puede cargar (por ejemplo, porque falta un componente de Visual C++), la aplicación sigue funcionando sin IA ni detección de caras y te dice qué instalar.

### Límites conocidos (beta)

- Solo hay binarios para Windows x64. En macOS y Linux se puede compilar y ejecutar desde el código.
- No abre HEIC, HEIF, AVIF ni RAW. Se edita en sRGB de 8 bits.
- La IA usa solo la CPU: ampliar una foto pequeña tarda segundos, pero un resultado de 64 MP puede tardar un par de minutos. La aplicación avisa antes de las tareas largas y deja cancelarlas.
- Los archivos no están firmados (ver SmartScreen arriba).

### Privacidad

Sin cuentas, sin telemetría y sin envíos automáticos. Tus fotos no salen del equipo. La única conexión es **Ayuda → Buscar actualizaciones…**, y solo cuando la pulsas. Los registros redactan tu nombre y las rutas. Todo el detalle está en [docs/PRIVACY.md](docs/PRIVACY.md).

### Informar de un problema

1. **Ayuda → Informe de diagnóstico…**: revisa el texto (no debería contener tu nombre ni rutas) y pulsa **Copiar**.
2. Abre una [incidencia](https://github.com/Erenriquezp/ImageProcessor/issues/new?template=bug_report.md) con los pasos para reproducirlo y pega el informe.

Nada se envía solo: tú decides qué compartes.

## Compilar desde el código

| Requisito | Versión |
|---|---|
| JDK | **25** para la aplicación. `core-imaging` compila y se prueba también con JDK 21 |
| Maven | No hace falta: el wrapper `mvnw` descarga Maven 3.9.11 |
| Sistema operativo | Windows, macOS o Linux (GTK 3.20 o superior) |

### Inicio rápido

```bash
git clone https://github.com/Erenriquezp/ImageProcessor.git
cd ImageProcessor

./mvnw install -DskipTests         # Windows (PowerShell): .\mvnw.cmd install -DskipTests
./mvnw -pl app javafx:run          # el editor
./mvnw -pl lab-plugin javafx:run   # el editor con el Laboratorio
./mvnw verify                      # compila, tests, Checkstyle y cobertura
```

Para generar el instalador y el ZIP en Windows: [docs/RELEASING.md](docs/RELEASING.md).

## Uso

Abre o arrastra una foto, mueve los **Ajustes** de la derecha y compara con la **Cortina**. Para añadir efectos, abre **Filtros** en el riel izquierdo: cada uno entra en la pila y se puede editar después. **Mejorar** amplía la foto con IA. **Wallpapers** la exporta a la vez para tus pantallas, móviles y tabletas. **Exportar** guarda en JPEG, PNG o TIFF.

Los atajos y la arquitectura de la interfaz están en [docs/UI.md](docs/UI.md).

## Formatos y privacidad

| Formato | Abrir | Exportar |
|---|---|---|
| JPEG, PNG, TIFF | Sí | Sí |
| WebP, BMP, GIF | Sí | No |
| HEIC, HEIF, AVIF, RAW | No | No |

Se edita en sRGB de 8 bits: los archivos de 16 bits o gamut ancho se convierten al abrirlos, y la aplicación lo avisa. Al exportar, un archivo existente no se sustituye sin confirmación, y los metadatos se filtran según tres modos: mantener, quitar la ubicación (por defecto) o quitar todo. Detalle en [docs/IO.md](docs/IO.md).

## Módulos

| Módulo | Contenido |
|---|---|
| `core-imaging` | Núcleo puro, solo `java.base`: imagen, operaciones, recetas y render |
| `imaging-io` | Abrir y exportar: códecs sobre `ImageIO`, color y metadatos |
| `wallpapers` | Lógica pura de Wallpapers: presets, encuadre, remuestreo en luz lineal, rellenos y exportación por lotes |
| `ai-engine` | Inferencia local con ONNX Runtime por tiles, los modelos incluidos (Real-ESRGAN y el detector de caras YuNet) y la caché de resultados |
| `app` | Interfaz JavaFX y sesión de edición |
| `lab-plugin` | Opcional, fuera del instalador: operaciones didácticas, generador, blending y código heredado de referencia |
| `bench` | Benchmarks JMH (perfil `-Pbench`) |

Más detalle en [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/TESTING.md](docs/TESTING.md), [docs/PERFORMANCE.md](docs/PERFORMANCE.md), [docs/PRIVACY.md](docs/PRIVACY.md) y [docs/RELEASING.md](docs/RELEASING.md).

## Stack

Java 25 · JavaFX 25 LTS · AtlantaFX 2.1.0 · Ikonli 12.4.0 · ONNX Runtime 1.31.0 · IBM Plex Sans · JUnit 5 · ArchUnit · JMH.

## Estado del proyecto

Nació como trabajo académico (conservado en el tag `v0.1.0-academic` y la rama `legacy/academic-v0`) y evoluciona hacia un producto. El [CHANGELOG](CHANGELOG.md) registra lo hecho. El nombre y la licencia son provisionales.

## Licencia

**MIT** (provisional): consulta [LICENSE](LICENSE). Para contribuir: [CONTRIBUTING.md](CONTRIBUTING.md).

## English summary

Rora Studio is a non-destructive desktop photo editor built with Java 25 and JavaFX 25: linear-light adjustments, an editable step stack, before/after comparison, a live histogram, local AI upscaling and multi-device wallpaper export. Windows users can download a per-user MSI or a portable ZIP from Releases (unsigned for now: verify the SHA-256 in `SHA256SUMS`). No accounts, no telemetry; the only network request is the manual update check ([docs/PRIVACY.md](docs/PRIVACY.md)). To build from source: JDK 25, `./mvnw install -DskipTests`, then `./mvnw -pl app javafx:run`.
