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
- Fase 2 — pipeline no destructivo: recetas (`EditRecipe`, `RecipeHistory`), `Renderer` con preview sobre proxy y render completo exacto, `ResultCache` acotada en MB, fusión exacta de operaciones puntuales, `ParamUnit`.
- Capa de sesión sin JavaFX (`app.session`): `EditSession`, carriles PREVIEW y FULL con cancelación, "último gana" y debounce, y pasos de receta para las 15 operaciones aún no portadas.
- Monitor de bloqueos del hilo FX (`-Drora.fxStallMonitor=true`), test de memoria con 24 MP (`-Xmx1536m`) y reglas de arquitectura con ArchUnit.
- Cuentagotas: al pasar el ratón muestra el valor del preview marcado con "≈"; al hacer clic, el valor exacto.
- Fase 3 — interfaz nueva: visor único con zoom hacia el cursor, cortina, lado a lado, inspector con histograma en vivo y avisos de recorte, Ajustes básicos, pila editable, catálogo de 15 filtros, recorte y geometría, avisos no bloqueantes, atajos y persistencia de la disposición.
- Operaciones nuevas: `adjust.basic`, `blur.gaussian`, `detail.unsharp-mask` y `transform.*` (recortar, voltear, rotar, enderezar, redimensionar).
- Módulo opcional `lab-plugin` con las operaciones didácticas, generador y blending.

### Removed
- Interfaz antigua: pestañas, lista de 44 filtros y comparación 50/50.

### Changed
- La aplicación requiere JDK 25 y JavaFX 25; `core-imaging` sigue en Java 21.
- Exportar guarda en PNG hasta que llegue la exportación avanzada.
- Restablecer ahora se puede deshacer.
- Proyecto renombrado a **Rora Studio**: groupId `io.github.erenriquezp`, paquete base `io.github.erenriquezp.rora`, módulo JPMS `io.github.erenriquezp.rora.app`.
- Estructura Maven multimódulo: `rora-parent` en la raíz, `core-imaging`, `app` (código anterior, movido con historial) y `bench`. Para lanzar la app: `./mvnw install -DskipTests && ./mvnw -pl app javafx:run`.
- Las operaciones portadas del paquete `service` están `@Deprecated(forRemoval = true)`; solo las usan los tests diferenciales.
- Ningún trabajo pesado en el hilo FX: filtros, blending, histograma, degradados, carga y guardado corren en segundo plano. El historial guarda recetas en lugar de imágenes (de unos 2,9 GB a KB con 24 MP) y su tope pasa de 30 a 200 pasos.
- Los errores del preview se muestran en la barra de estado en lugar de descartarse.
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
