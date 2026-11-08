<div align="center">

# Rora Studio

Editor de fotos de escritorio, no destructivo, con ajustes en luz lineal y pila de pasos editable.

[![Java](https://img.shields.io/badge/Java-25-orange)](https://openjdk.org/)
[![JavaFX](https://img.shields.io/badge/JavaFX-25-1f8acb)](https://openjfx.io/)
[![Licencia](https://img.shields.io/badge/licencia-MIT-green)](LICENSE)
[![CI](https://github.com/Erenriquezp/ImageProcessor/actions/workflows/ci.yml/badge.svg)](https://github.com/Erenriquezp/ImageProcessor/actions/workflows/ci.yml)

![Captura de pantalla de Rora Studio](docs/img/captura.png)

<sub>Foto de la captura: Pixel.la Free Stock Photos, CC0 1.0, vía Wikimedia Commons.</sub>

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
- **Privacidad**: por defecto se elimina la ubicación, y la miniatura incrustada no se copia nunca.
- **Trabajo en segundo plano**: la interfaz no se bloquea con fotos de 24 MP.

## Requisitos

| Requisito | Versión |
|---|---|
| JDK | **25** para la aplicación. `core-imaging` compila y se prueba también con JDK 21 |
| Maven | No hace falta: el wrapper `mvnw` descarga Maven 3.9.11 |
| Sistema operativo | Windows, macOS o Linux (GTK 3.20 o superior) |

## Inicio rápido

```bash
git clone https://github.com/Erenriquezp/ImageProcessor.git
cd ImageProcessor

./mvnw install -DskipTests         # Windows (PowerShell): .\mvnw.cmd install -DskipTests
./mvnw -pl app javafx:run          # el editor
./mvnw -pl lab-plugin javafx:run   # el editor con el Laboratorio
./mvnw verify                      # compila, tests, Checkstyle y cobertura
```

## Uso

Abre o arrastra una foto, mueve los **Ajustes** de la derecha y compara con la **Cortina**. Para añadir efectos, abre **Filtros** en el riel izquierdo: cada uno entra en la pila y se puede editar después. **Exportar** guarda en PNG; JPG llegará con la exportación avanzada.

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
| `app` | Interfaz JavaFX y sesión de edición |
| `lab-plugin` | Opcional, fuera del instalador: operaciones didácticas, generador, blending y código heredado de referencia |
| `bench` | Benchmarks JMH (perfil `-Pbench`) |

Más detalle en [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/TESTING.md](docs/TESTING.md) y [docs/PERFORMANCE.md](docs/PERFORMANCE.md).

## Stack

Java 25 · JavaFX 25 LTS · AtlantaFX 2.1.0 · Ikonli 12.4.0 · IBM Plex Sans · JUnit 5 · ArchUnit · JMH.

## Estado del proyecto

Nació como trabajo académico (conservado en el tag `v0.1.0-academic` y la rama `legacy/academic-v0`) y evoluciona hacia un producto. El [CHANGELOG](CHANGELOG.md) registra lo hecho. El nombre y la licencia son provisionales.

## Licencia

**MIT** (provisional): consulta [LICENSE](LICENSE). Para contribuir: [CONTRIBUTING.md](CONTRIBUTING.md).

## English summary

Rora Studio is a non-destructive desktop photo editor built with Java 25 and JavaFX 25: linear-light adjustments, an editable step stack, before/after comparison and a live histogram. Requires JDK 25; run `./mvnw install -DskipTests` then `./mvnw -pl app javafx:run`.
