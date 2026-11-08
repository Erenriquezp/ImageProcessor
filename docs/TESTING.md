# Pruebas

Red de seguridad de la fase 0: pruebas de caracterización que permiten refactorizar sin cambiar el comportamiento sin querer.
Las operaciones de imagen (`service/`, `domain/`) no dependen de JavaFX, así que los tests corren en modo headless.

## Ejecutar

```bash
./mvnw test                       # todos los tests (Windows PowerShell: .\mvnw.cmd test)
./mvnw verify                     # tests + Checkstyle (informe) + JaCoCo (informe por módulo en <módulo>/target/site/jacoco/ y agregado en app/target/site/jacoco-aggregate/)
./mvnw test -Dgroups=property     # solo una etiqueta
./mvnw test -DexcludedGroups=known-issue
./mvnw test -Dtest=LegacyPinTest
```

Surefire usa el module path (JPMS): los tests viven en los mismos paquetes que el código (`...service`, `...domain`) y no requieren cambios en `module-info.java`.
JaCoCo no tiene umbral de bloqueo todavía.

## Etiquetas JUnit

| Etiqueta | Significado |
|---|---|
| `legacy-pin` | Nivel A. Fija con golden exacto el comportamiento heredado. Una fase posterior puede actualizarlo **de forma deliberada**. |
| `property` | Nivel B (y propiedades comunes). Solo propiedades: dimensiones, alpha, rango, determinismo, identidad con parámetros neutros. |
| `known-issue` | Defecto documentado de la auditoría. El test describe el comportamiento **actual** (o está `@Disabled` si describe el correcto). |

## Goldens

Los PNG de referencia están en `app/src/test/resources/golden/<operacion>/<caso>__<imagen>.png` (peso total < 2 MB).

- Sin flags, un golden ausente **hace fallar** el test; nunca se crea en silencio.
- Para regenerarlos, de forma deliberada: `./mvnw test -Dtest=LegacyPinTest -Dgolden.update=true`.
- Si un golden cambia, explica el motivo en el mensaje del commit.

## Clasificación de operaciones

| Nivel | Qué se prueba | Operaciones |
|---|---|---|
| **A** (pin exacto) | Golden exacto con parámetros neutro, típico y extremo | Negativo, A luminancia, Retro 1, Re-coloración, Pixelar, Ajuste de color, Escala y bias, Umbral suave, Saturación, Diagnóstico de exposición |
| **B** (propiedades) | Comportamiento que cambiará a propósito | Grises, Brillo, HSV, B/N por umbral, Grises cuantizados, Sepia/Frío/Cálido/Polaroid/Kodachrome, Desvanecimiento circular, Retro 2, Convolución, Buffer ADD/ACUM/MULT, Mapa de profundidad, Muestreo de textura, Blending de fragmentos, Ecualización, Rotación de tono, Degradados, Histograma, Blending 2 imágenes |
| **C** (humo) | Solo que no lanza y conserva dimensiones | Transparencia global, Esmerilado, Reducción 4 bits, Z/W-Buffer, Rasterizar, Interpolación en profundidad, MSAA, Alpha Test, Stencil, Logic Op, Interpolación, Extrapolación, Espacio de color, Triple blending |

El detalle por operación (justificación y parámetros) está en el plan local de la fase 0.

## Utilidades (`app/src/test/java/.../testsupport`)

- `TestImages`: imágenes deterministas (degradado, tablero, ruido con semilla fija, bordes duros), RGB y ARGB con alpha parcial.
- `ImageAssert`: igualdad exacta o con tolerancia por canal, dimensiones, alpha, rango.
- `GoldenSupport`: comparación con goldens.
- `OpCatalog` / `OpCase`: catálogo de operaciones con sus casos de parámetros (rangos tomados de `EditorFilterPane`).

## Defectos conocidos verificados

JPG con ARGB (reproducido; el test correcto está `@Disabled`, se corrige en la fase 4), Interpolación = Extrapolación, modos de Reducción 4 bits idénticos (incluido DECIMAL), marco de 1 px sin procesar en la convolución, Blending que deforma el fondo, Escala de grises por promedio y matrices de color sin offsets.
