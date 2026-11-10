# Mejorar con IA

Súper-resolución local ×2 y ×4 con el modelo `realesr-general-x4v3` (Real-ESRGAN) sobre ONNX Runtime, solo en CPU. Nada sale del equipo: el módulo de IA no usa `java.net` y ArchUnit lo comprueba.

## Cuándo se ejecuta

**Solo cuando el usuario lo pide.** Hay dos caminos:

- El botón **Mejorar** del panel.
- La pregunta al exportar una receta cuyo paso de mejora no tiene resultado. Ofrece tres opciones: mejorar y exportar (con la estimación), exportar con ampliación clásica o cancelar.

Abrir el panel, mover la escala o la intensidad, editar otros pasos o refrescar la vista no ejecutan el motor (`EnhanceViewModelTest`, `EnhanceIntegrationTest`).

Si la estimación supera los 120 s, se pide confirmación antes de empezar. Por encima de 60 s, el panel avisa de que puede tardar bastante. Por encima de 20 s, el progreso muestra el tiempo restante, calculado como media móvil de los últimos tiles.

## El paso en la receta

`ai.enhance` va justo detrás de la geometría. Su estado se deriva de una clave calculada sobre la entrada al motor, así que no se guarda en ningún sitio.

| Insignia en la pila | Significado |
|---|---|
| Sin ejecutar | Aún no hay resultado. La vista usa ampliación clásica |
| Mejorando | Trabajo en curso; se puede cancelar |
| Listo | La vista y la exportación usan el resultado del modelo |
| Obsoleto | La geometría ha cambiado desde la mejora. La vista usa ampliación clásica |

- **Qué lo invalida:** cambiar la geometría o la escala. Deshacer recupera el resultado anterior sin ejecutar nada.
- **Qué no lo invalida:** ajustar la intensidad (mezcla con la ampliación clásica) o editar los pasos posteriores.
- **Al abrir otra imagen,** la escala y la intensidad vuelven a ×2 y 100 %.

## Límites

- Resultado de 64 MP como máximo. ×4 se desactiva y el panel explica por qué.
- Antes de empezar se comprueba que quepa en el montón y que quede memoria física para el motor. La memoria física se lee en segundo plano, porque en Windows esa consulta tardó más de 100 ms en el hilo de interfaz. El trabajo vuelve a comprobarlo en su propio hilo.
- Un trabajo a la vez, en el hilo `rora-ai`, separado de los carriles de render.

## Privacidad

- **Caché:** los resultados se guardan solo en la caché de la aplicación: `%LOCALAPPDATA%\RoraStudio\cache\ai` en Windows (se puede cambiar con `-Drora.ai.cache`). Se borran al cerrar la imagen y al salir, y al arrancar se limpian los restos de sesiones que no se cerraron bien. Nunca se reutilizan entre sesiones.
- **Marca de procedencia:** la casilla "Indicar en el archivo que se usó IA" está activa por defecto. Solo se escribe si la salida lleva de verdad píxeles del modelo, es decir, con el paso listo e intensidad mayor que 0. Declara el término IPTC `compositeWithTrainedAlgorithmicMedia`, el modelo y la fecha, sin hora.

| Política de metadatos | Marca |
|---|---|
| Quitar todo | Nada. La casilla aparece desactivada |
| Quitar ubicación (por defecto) | XMP nuevo solo con la marca. El XMP de origen se sigue eliminando |
| Mantener | Se añade al XMP de origen. Si no se puede ampliar sin riesgo (otra codificación, demasiado grande o ya declara su origen), se conserva intacto y sin marca |

JPEG y PNG la llevan en su XMP, y TIFF en la etiqueta 700, que solo se escribe con marca. `ProvenanceExportTest` lo comprueba leyendo el directorio del TIFF a mano.

## Bibliotecas nativas

- ONNX Runtime extrae sus DLL al directorio temporal si no se le indica otra ruta. Para evitarlo, `OrtNative` fija `onnxruntime.native.path` antes de tocar cualquier clase de ORT. Por defecto apunta a `java.home/ort-native`, el directorio de la imagen de la app.
- `OrtNative` comprueba también el SHA-256 de las dos bibliotecas contra una tabla incluida en el módulo. Si falta algo o un hash no coincide, falla sin extraer nada. El hash detecta corrupción, pero no protege de quien ya pueda escribir en ese directorio.
- En desarrollo, Maven desempaqueta los nativos en `ai-engine/target/ort-native`, y `javafx:run` y los tests ya pasan esa ruta.
- Se declara `--enable-native-access=com.microsoft.onnxruntime`.
- ORT crea siempre una carpeta vacía `onnxruntime-java*` en el directorio temporal la primera vez que se usa Mejorar. La aplicación no limpia ese directorio.
- `tools/jlink-check.sh` construye una imagen jlink solo con los nativos de win-x64 y ejecuta el modelo real.

## El modelo

`ai-engine/src/main/resources/.../ai/models/realesr-general-x4v3.onnx`, 4 866 420 bytes. Su SHA-256 se verifica al cargarlo. La procedencia, la conversión y la atribución están en `MODEL_NOTICE.md`, junto al modelo.

- **Licencia:** BSD-3-Clause.
- **Riesgo conocido, sin que esto sea consejo legal:** el proyecto original no indica la licencia de los conjuntos con los que se entrenó (DF2K y OST).

## Pruebas

- **Modelos sintéticos:** `ai-engine/src/test/resources/models/` contiene cinco modelos de menos de 50 KB, generados por `tools/models/make_test_models.py` y con su hash en `SHA256SUMS`. Prueban que el resultado por tiles es idéntico bit a bit al de la imagen completa, las costuras con margen y el mapeo de errores.
- **Modelo real:** `AiRealTest` (`@Tag("ai-real")`) queda fuera de `verify` porque sus umbrales dependen de la máquina:

```bash
./mvnw -pl ai-engine -am install -DskipTests
./mvnw -pl ai-engine test -Dgroups=ai-real -DexcludedGroups= -DargLine=-Xmx1536m -Dtest=AiRealTest
```

| Métrica | Umbral | Medido (i5-1035G1, 4 hilos, 2026-11-09) |
|---|---|---|
| 1 MP ×4 de extremo a extremo | < 60 s | 37,3 s |
| Pico de memoria del proceso | < 1400 MB | 644 MB |
| Tiles frente a imagen completa | ≥ 60 dB | 72,4 dB, diferencia máxima 2 |
| Cancelación | < 200 ms | mediana 12,9 ms, máximo 63,3 ms |

Monitor FX (`-Drora.fxStallMonitor=true`) con `SnapshotTool` y el modelo real:

- **Durante la inferencia, la cancelación y el cambio de intensidad:** ninguna pausa de más de 100 ms en tres tandas.
- **Al primer clic en Mejorar:** en una de las tandas hubo una pausa de 155 ms al cargar las clases por primera vez. No se repitió en la siguiente.

## Fuera de alcance

Gestor de descargas, otros modelos, GPU, caras, reducción de ruido y caché entre sesiones.
