# Changelog

Todos los cambios relevantes de este proyecto se documentan aquí.
El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el proyecto usa [SemVer](https://semver.org/lang/es/).

## [Unreleased]

### Changed
- El repositorio pasa a llamarse `rora-studio` (antes `ImageProcessor`): actualizados la comprobación de actualizaciones, los enlaces de ayuda y la documentación. La 0.1.0 sigue funcionando gracias a las redirecciones de GitHub.

## [0.1.0] - 2026-11-14

Primera versión distribuible (beta) para Windows x64: instalador MSI por usuario y ZIP portable. Reúne las fases 0 a 8 desde la versión académica.

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
- Fase 4 — entrada y salida: módulo `imaging-io`; abrir JPEG, PNG, TIFF, WebP, BMP y GIF con perfiles ICC, orientación EXIF, CMYK, grises y 16 bits, con progreso y cancelación.
- Exportar JPEG, PNG y TIFF: calidad, límite de lado y de peso, plantillas de nombre, estimación de tamaño, preajustes, tres políticas de metadatos y escritura atómica.
- Aviso de cambios sin exportar, notificación con "Mostrar en carpeta" y `THIRD_PARTY_LICENSES.md`.
- Fase 5 — Mejorar con IA: módulo `ai-engine` con ONNX Runtime 1.31.0 y el modelo `realesr-general-x4v3` incluido, por tiles y solo en CPU, sin red.
- Panel Mejorar (×2/×4, intensidad, estimación, cancelación y aviso sobre lo que hace la IA), insignias del paso en la pila y pregunta al exportar sin resultado. El motor solo se ejecuta cuando el usuario lo pide.
- Marca XMP de procedencia en JPEG, PNG y TIFF, opcional y nunca con "Quitar todo".
- `tools/jlink-check.sh` comprueba el motor en una imagen jlink solo con los nativos de win-x64.
- Fase 6 — Wallpapers: herramienta que exporta la foto editada con la resolución exacta de varios dispositivos a la vez, en carpetas por grupo, con una sola pregunta por colisiones y un informe final.
- Módulo `wallpapers` (lógica pura): 35 presets con fuente y fecha, tamaños propios en la carpeta de datos, remuestreo exacto en luz lineal, rellenos (desenfoque, espejo, color) con tramado determinista en PNG, insignias de calidad y sugerencia de mejora con IA.
- Encuadre automático que no corta caras, con el detector YuNet (OpenCV Zoo, MIT) incluido en `ai-engine`; validado con hojas ciegas.
- Zonas seguras orientativas del móvil, "Mi pantalla" por monitor y ajuste manual del encuadre con anclajes.
- Fase 8 — distribución para Windows x64: instalador MSI por usuario (sin administrador, en `%LOCALAPPDATA%\Programs\Rora Studio`, actualización encima con una sola entrada) y ZIP portable reproducible byte a byte, con `SHA256SUMS`. Runtime propio con `jlink` (Temurin y JavaFX 25.0.5): no hace falta instalar Java. `tools/package-windows.ps1` y sus comprobaciones (`verify-package`, `verify-repro`, `smoke-package`, `test-msi`).
- Abrir una foto pasada por argumento, también al arrastrarla sobre el icono o el acceso directo.
- Registro en `%LOCALAPPDATA%\RoraStudio\logs` con rotación y redacción automática de rutas, nombres de fotos y nombre de usuario; nunca píxeles, metadatos ni valores de la receta.
- Manejo global de errores: diálogo con informe para errores inesperados, recuperación ante falta de memoria (cancela, libera la caché y sigue) y aviso al arrancar si la sesión anterior murió por un fallo interno.
- Menú de ayuda: Acerca de (versiones, atribuciones, modelos y licencias), informe de diagnóstico que se revisa y edita antes de copiarlo o guardarlo, carpetas de registros y datos, borrar registros y temporales, registro detallado y cómo informar de un problema.
- Buscar actualizaciones, solo a petición: consulta las releases de GitHub y abre la página en el navegador; nunca descarga nada. Se desactiva con `-Drora.updates=off`.
- Si el motor de IA no puede cargar por una biblioteca nativa, la app explica que falta Visual C++ y enlaza la página de Microsoft; sigue funcionando sin IA.
- Wallpapers: estimación del tamaño total y por destino antes de exportar.
- Icono propio, generado por `tools/icon/RoraIcon.java`.
- Workflow `release.yml` (borrador de release por etiqueta `vX.Y.Z`) y `docs/RELEASING.md`, `docs/PRIVACY.md`. Sin ejecutar todavía.

### Changed
- Compilar requiere JDK 25 y JavaFX 25 (`core-imaging`, `imaging-io` y `wallpapers` siguen en Java 21); el instalador trae su propio runtime.
- Las preguntas modales se ensanchan en vez de cortar las opciones largas.
- Restablecer ahora se puede deshacer.
- Proyecto renombrado a **Rora Studio**: groupId `io.github.erenriquezp`, paquete base `io.github.erenriquezp.rora`, módulo JPMS `io.github.erenriquezp.rora.app`.
- Estructura Maven multimódulo: `rora-parent` en la raíz, `core-imaging`, `app` (código anterior, movido con historial) y `bench`. Para lanzar la app: `./mvnw install -DskipTests && ./mvnw -pl app javafx:run`.
- Las operaciones portadas del paquete `service` están `@Deprecated(forRemoval = true)`; solo las usan los tests diferenciales.
- Ningún trabajo pesado en el hilo FX: filtros, blending, histograma, degradados, carga y guardado corren en segundo plano. El historial guarda recetas en lugar de imágenes (de unos 2,9 GB a KB con 24 MP) y su tope pasa de 30 a 200 pasos.
- Los errores del preview se muestran en la barra de estado en lugar de descartarse.
- Maven wrapper actualizado a Maven 3.9.11.
- Captura de pantalla movida a `docs/img/`.
- Surefire en modo headless; Spotless solo comprueba el código modificado (`ratchetFrom`).
- La telemetría que ONNX Runtime trae activada de fábrica se apaga al crear el motor.
- Wallpapers: la lista de destinos ya no tiene una segunda barra de desplazamiento.
- El runtime de la aplicación ya no incluye `java.sql`; JavaFX y ONNX Runtime cargan sus bibliotecas desde la carpeta de la aplicación, sin extraer nada a `%TEMP%` ni a `%USERPROFILE%\.openjfx`.
- CI: acciones fijadas por SHA, JDK 21 para los tres módulos en Java 21 y comprobación de `THIRD_PARTY_LICENSES.md`.
- Versión única `0.1.0-SNAPSHOT` en todos los módulos, visible en Acerca de como "0.1.0 (beta)".

### Fixed
- Exportar a JPG una imagen con transparencia ya no falla.
- Las imágenes en gris se abren sin aclararse.

### Removed
- Interfaz antigua: pestañas, lista de 44 filtros y comparación 50/50.
- Archivos de IDE (`.idea/`) y notas de planeación sacados del control de versiones.
- `.mvn/jvm.config`: no era necesario y provocaba avisos en Maven.

## [0.1.0-academic] - versión académica

Estado del código en el tag `v0.1.0-academic` (rama `legacy/academic-v0`): editor con 20 filtros, pipeline apilable, histograma, blending de 2 y 3 capas y generador de degradados.

[Unreleased]: https://github.com/Erenriquezp/rora-studio/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/Erenriquezp/rora-studio/compare/v0.1.0-academic...v0.1.0
[0.1.0-academic]: https://github.com/Erenriquezp/rora-studio/releases/tag/v0.1.0-academic
