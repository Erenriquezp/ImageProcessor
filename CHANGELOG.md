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

### Changed
- Maven wrapper actualizado a Maven 3.9.11.
- README actualizado: requisito real de JDK 21+.
- Captura de pantalla movida a `docs/img/`.

### Removed
- Archivos de IDE (`.idea/`) y notas de planeación sacados del control de versiones.
- `.mvn/jvm.config`: no era necesario y provocaba avisos en Maven.

## [0.1.0-academic] - versión académica

Estado del código en el tag `v0.1.0-academic` (rama `legacy/academic-v0`): editor con 20 filtros, pipeline apilable, histograma, blending de 2 y 3 capas y generador de degradados.

[Unreleased]: https://github.com/Erenriquezp/ImageProcessor/compare/v0.1.0-academic...HEAD
[0.1.0-academic]: https://github.com/Erenriquezp/ImageProcessor/releases/tag/v0.1.0-academic
