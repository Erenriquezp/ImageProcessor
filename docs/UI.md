# Interfaz

Editor con un único visor, inspector fijo y pila de ajustes visible. El criterio de diseño: abrir, ajustar, comparar y exportar sin pasar por una lista de filtros.

## Disposición

| Zona | Contenido |
|---|---|
| Barra superior | Abrir, Exportar, deshacer, rehacer, Restablecer, modo de comparación, zoom |
| Riel izquierdo | Ajustes, Filtros, Recortar (y Lab con `--lab`) |
| Cajón izquierdo | Catálogo con buscador, o la herramienta de recorte. Flota sobre el visor por debajo de 1280 px |
| Visor | Zoom y pan únicos, cortina, lado a lado, superposición de recorte |
| Inspector | Histograma, zona contextual (Ajustes básicos o parámetros del paso) y pila |
| Barra de estado | Mensaje, píxel bajo el cursor, formato y tamaño |

Ventana mínima: 1100×700. En Windows la ventana usa barra de título propia; en macOS y Linux, la decoración nativa. Los tamaños de paneles, la última carpeta y el modo de comparación se guardan con `java.util.prefs`.

## Atajos

| Tecla | Acción |
|---|---|
| Ctrl+O / Ctrl+S | Abrir / Diálogo de exportación |
| Ctrl+Z / Ctrl+Y o Ctrl+Mayús+Z | Deshacer / Rehacer |
| `\` u `O` (mantener) | Ver el original |
| F / 1 | Ajustar / 100 % real |
| + / − | Zoom |
| Enter / Esc | Aplicar o cancelar el recorte |
| Flechas sobre un slider | Paso normal; con Mayús, paso fino. Inicio lo restablece |
| Flechas sobre la cortina | Mover la cortina |
| Doble clic en un slider | Restablecer |

## Tokens de diseño

`app/.../theme/tokens.css`, sobre AtlantaFX Primer Dark. El gris alrededor de la foto es neutro (R = G = B) para no sesgar el color, y el azul solo señala estado. `TokenContrastTest` lee el archivo y exige 4,5:1 en texto y 3:1 en bordes de control.

| Token | Valor | Uso |
|---|---|---|
| `canvas` | #161616 | Entorno de la foto |
| `bg`, `surface-1/2/3` | #1C1C1C, #242424, #2E2E2E, #383838 | Fondos |
| `border`, `border-strong` | #454545, #6E6E6E | Divisores y controles |
| `text`, `text-2`, `text-3` | #EDEDED, #B8B8B8, #A3A3A3 | Texto |
| `accent`, `accent-fill`, `select` | #6AAEF2, #2B6CB0, #1F3A57 | Estado |
| `danger`, `warn`, `ok` | #F28B82, #E6B450, #74C69D | Avisos |

Tipografía: IBM Plex Sans (SIL OFL 1.1), incluida en la aplicación. Espaciado en base 4.

## Arquitectura

Las vistas son cableado delgado. La lógica vive en ViewModels que no conocen nodos.

| Paquete | Contenido |
|---|---|
| `app.model` | Sin JavaFX: `ViewTransform`, `Curtain`, `ControlSpec`, `CropModel`, `RecipeEdits`, `ClippingOverlay` |
| `app.viewmodel` | Solo `javafx.base`: `StudioViewModel` y uno por panel; se prueban sin toolkit |
| `app.view`, `app.shell` | Nodos y ventana |
| `app.catalog` | Filtros que ve el usuario: es presentación, el núcleo no la conoce |
| `app.i18n` | `labels_es.properties`; `CatalogAndLabelsTest` exige que no falten claves ni sobren |
| `app.theme` | Tema, tokens y fuente |
| `app.lab` | SPI del Laboratorio |

Los paneles de parámetros se generan desde `ParamSchema`. Añadir una operación visible = la operación, sus textos y una entrada en `Catalog`.

Un arrastre de slider es una sola entrada de deshacer (`GestureTracker`). Los pasos de geometría van siempre primero y no se reordenan, porque de ellos dependen los parámetros en píxeles de los pasos posteriores.

## Abrir y exportar

Abrir muestra una barra de progreso con cancelar solo si tarda más de 300 ms, y los avisos de conversión como notificación. El diálogo de exportación vive en `ExportDialog` y su estado en `ExportDialogViewModel`; se recuerda con `LayoutStore`. Antes de abrir otra imagen o cerrar, si hay cambios sin exportar, se pregunta con un `Prompt`. Las preguntas modales son datos del ViewModel: se prueban sin toolkit.

## Laboratorio

Módulo opcional `lab-plugin`, fuera del instalador: operaciones didácticas, Generador, Blending y Triple Blending, y el código heredado que los tests diferenciales necesitan. Se activa con `./mvnw -pl lab-plugin javafx:run`, que pasa `--lab`.

## Herramientas de desarrollo

En `app/src/test/.../app/shell`; no se distribuyen. `SnapshotTool` genera capturas de todos los estados, `StressTool` ejercita la interfaz con el monitor de bloqueos y `ColdStartProbe` mide el arranque. Se lanzan con `--patch-module` sobre `target/test-classes`.

Humo con toolkit real (necesita pantalla o `xvfb-run`): `./mvnw -Pui-smoke -pl app -am test`.
