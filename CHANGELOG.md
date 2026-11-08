# Changelog

Todos los cambios relevantes de este proyecto se documentan aquí.
El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el proyecto usa [SemVer](https://semver.org/lang/es/).

## [Unreleased]

### Added
- Gobernanza del repositorio: `CONTRIBUTING.md`, plantillas de GitHub, `CODEOWNERS`, Dependabot.
- CI con GitHub Actions (Ubuntu y Windows).
- Calidad: maven-enforcer, Spotless, Checkstyle, SpotBugs (manual) y JaCoCo.
- Hooks locales de Git (Conventional Commits) en `tools/`.
- `.gitignore`, `.gitattributes` y `.editorconfig`.
- Red de seguridad de pruebas (fase 0): JUnit 5, AssertJ, tests parametrizados de caracterización con goldens y propiedades, defectos conocidos documentados (`docs/TESTING.md`).
- CI con matriz Ubuntu/Windows × JDK 21/25, informes de JaCoCo y surefire como artefactos, job de SpotBugs.
- Fase 1 — núcleo de imagen `core-imaging` (módulo `io.github.erenriquezp.rora.core`, solo `java.base`): `Img`, SPI `Operation` con `ParamSchema`, `OpRegistry` con `ServiceLoader`, `TileExecutor` con bandas paralelas, cancelación y progreso, `Hsb` sin AWT y `SrgbLut`.
- 25 operaciones portadas al núcleo con resultados **idénticos bit a bit** al código anterior (puntuales, de vecindad, con estado y de análisis); la fachada `ImageProcessor` delega en ellas sin cambiar sus firmas.
- Tests de equivalencia exacta (etiquetas `equivalence` y `exhaustive`, incluidos los 2^24 colores) y propiedades generadas desde el registro de operaciones.
- Módulo `bench` con JMH (perfil `-Pbench`) y `docs/PERFORMANCE.md`.

### Changed
- Proyecto renombrado a **Rora Studio**: groupId `io.github.erenriquezp`, paquete base `io.github.erenriquezp.rora`, módulo JPMS `io.github.erenriquezp.rora.app`.
- Estructura Maven multimódulo: `rora-parent` en la raíz, `core-imaging`, `app` (código anterior, movido con historial) y `bench`. Para lanzar la app: `./mvnw install -DskipTests && ./mvnw -pl app javafx:run`.
- Las operaciones portadas del paquete `service` están `@Deprecated(forRemoval = true)`; solo las usan los tests diferenciales.
- Maven wrapper actualizado a Maven 3.9.11.
- README actualizado: requisito real de JDK 21+.
- Captura de pantalla movida a `docs/img/`.
- Surefire en modo headless; Spotless solo comprueba el código modificado (`ratchetFrom`).

### Removed
- Archivos de IDE (`.idea/`) y notas de planeación sacados del control de versiones.
- `.mvn/jvm.config`: no era necesario y provocaba avisos en Maven.

## [0.1.0-academic] - versión académica

Estado del código en el tag `v0.1.0-academic` (rama `legacy/academic-v0`): editor con 20 filtros, pipeline apilable, histograma, blending de 2 y 3 capas y generador de degradados.

[Unreleased]: https://github.com/Erenriquezp/ImageProcessor/compare/v0.1.0-academic...HEAD
[0.1.0-academic]: https://github.com/Erenriquezp/ImageProcessor/releases/tag/v0.1.0-academic
