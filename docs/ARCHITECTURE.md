# Arquitectura

## Estado actual

Un único módulo Maven (`com.example:imageprocessor`) con tres capas:

```
app/      Presentación y coordinación (ImageProcessorApp + ui/)
service/  Lógica de procesamiento; ImageProcessor es la fachada
domain/   Enumeraciones y tipos de dominio
```

- La UI solo habla con `service/ImageProcessor`.
- La edición es no destructiva: la imagen original no se modifica; cada filtro se aplica sobre el resultado consolidado anterior.
- El proyecto es modular (JPMS): ver `src/main/java/module-info.java`.

## Arquitectura objetivo (en evolución)

La dirección prevista, que puede cambiar:

- **Separar el núcleo de imagen de la interfaz**: una librería de procesamiento sin dependencia de JavaFX, comprobable con tests unitarios.
- **Operaciones descubiertas por `ServiceLoader`**, con descriptores de parámetros para generar la UI automáticamente.
- **Edición basada en recetas** (lista inmutable de operaciones) en lugar de guardar imágenes en el historial.
- **Procesamiento en segundo plano** con cancelación y por teselas para imágenes grandes.
- **Motor de IA opcional**, aislado en su propio módulo; los modelos se descargan bajo demanda y no se versionan.
- **Distribución** con `jlink` + `jpackage` desde GitHub Actions.

Los cambios se harán por fases en ramas `fase/N-descripcion` (ver [CONTRIBUTING.md](../CONTRIBUTING.md)). Las decisiones concretas se documentarán aquí cuando se tomen.
