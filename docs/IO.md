# Entrada y salida

Cómo abre y exporta imágenes Rora Studio: formatos, color, metadatos y escritura de archivos. El módulo es `imaging-io`; la lógica pura está en `core.io` (solo `java.base`).

## Formatos

| Formato | Abrir | Exportar | Limitaciones |
|---|---|---|---|
| JPEG | RGB, gris, CMYK, YCCK | Calidad 1–100, progresivo, 4:2:0 o 4:4:4, Huffman optimizado | La transparencia se aplana sobre un color |
| PNG | 1–16 bits, gris, paleta, alfa | Compresión 0–9, alfa exacto | — |
| TIFF | Hasta 16 bits, alfa, CMYK | Sin comprimir, LZW, Deflate, PackBits | Se abre la primera página. Al exportar solo se conservan `Make`, `Model`, `DateTime`, `Artist` y `Copyright` |
| WebP | Con y sin pérdida | No | Sin escritura: los candidatos Java son binarios nativos sin mantener o sin Windows |
| BMP | Sí | No | — |
| GIF | Sí | No | Se abre el primer fotograma |
| HEIC, HEIF, AVIF, BigTIFF | No | No | Mensaje propio |
| RAW (NEF, CR2, CR3, ARW, DNG…) | No | No | Se detecta también por extensión: el lector TIFF abriría solo su miniatura |

El formato se decide por el contenido, nunca por la extensión. El lector y el escritor de cada formato se eligen por su clase (`Codecs`): el orden de `ImageIO` cambia en cuanto hay otro plugin disponible. TwelveMonkeys se usa solo para leer WebP.

## Color

El pipeline trabaja en sRGB de 8 bits. Los archivos con 16 bits o gamut ancho se convierten al abrirlos, y la interfaz lo avisa.

| Origen | Tratamiento |
|---|---|
| RGB con perfil | ICC a sRGB, con recorte por canal (colorimétrico relativo) |
| Gris | La muestra se toma tal cual. `BufferedImage.getRGB` la aclara (128 → 188) y no se usa |
| 16 bits | Se redondea: `(v*255 + 32767) / 65535` |
| CMYK sin perfil | `R = ((255-C)*(255-K) + 127) / 255`, con aviso de colores aproximados. No se usan perfiles del sistema: el resultado cambiaría entre equipos |
| CMYK con perfil | ICC a sRGB con el perfil del archivo |
| Perfil inválido | Se asume sRGB, con aviso |

La salida siempre es sRGB. Con «sRGB etiquetado», JPEG y TIFF llevan un perfil generado por el propio programa y PNG el chunk `sRGB`.

## Orientación

La orientación EXIF se aplica a los píxeles al abrir. Lo que se edita ya está derecho, y al exportar se escribe Orientation=1 con las dimensiones reales.

## Metadatos y privacidad

La salida se construye desde una lista blanca: lo que no se reconoce no se copia. Ante cualquier anomalía en el EXIF, se descarta el bloque entero y se avisa.

| Dato | Mantener | Quitar ubicación (por defecto) | Quitar todo |
|---|---|---|---|
| Cámara, exposición y fechas | Sí | Sí | No |
| GPS | Sí | No | No |
| Zona horaria, ubicación del sujeto | Sí | No | No |
| Número de serie, propietario, comentarios | Sí | No | No |
| `MakerNote` y etiquetas desconocidas | No | No | No |
| Miniatura | **Nunca** | **Nunca** | **Nunca** |
| XMP | Sí, si es XML válido | No, entero | No |
| IPTC | Sí, si es válido | No, entero | No |
| Texto PNG | `Title`, `Author`, `Copyright` | No | No |

No se escribe el campo `Software`. «Web» usa «Quitar todo».

**Cómo comprobar que el GPS desapareció**, con [ExifTool](https://exiftool.org/):

```
exiftool -a -G1 -s -gps:all -xmp:all salida.jpg
```

No debe mostrar nada. Sin ExifTool, en Windows: clic derecho en el archivo, Propiedades, Detalles; no debe aparecer la sección GPS. Buscar la palabra `GPS` en el archivo no sirve: en EXIF son etiquetas binarias.

## Escritura de archivos

- Se escribe en un temporal `.rora-export-<id>.tmp` dentro de la misma carpeta y luego se publica. Un fallo o una cancelación no dejan restos ni tocan un archivo previo.
- Un archivo existente nunca se sustituye sin confirmación, y el original necesita una segunda.
- La publicación usa un enlace duro porque `ATOMIC_MOVE` reemplaza en Windows. En sistemas sin enlaces (exFAT), un `move` sin reemplazo.
- `ImageIO` no usa caché en disco.

Nombres: `{nombre}`, `{fecha}`, `{hora}`, `{n}`, `{ancho}`, `{alto}` y `{formato}`. Se sanean con las mismas reglas en todos los sistemas (caracteres no válidos, nombres reservados de Windows, puntos y espacios finales, 240 bytes como máximo).

## Límites

- Antes de decodificar se comprueba que la imagen quepa en el 70 % del heap, descontando la imagen abierta. `-Drora.io.maxPixels=N` fija un tope.
- Un JPEG truncado se rechaza: el lector del JDK lo abriría relleno de gris.
- El límite de peso solo se aplica a JPEG. Busca la mayor calidad que cabe, entre la elegida y 20, y si no se alcanza falla sin reducir la imagen.

## Empaquetado

TwelveMonkeys publica módulos automáticos, que `jlink` rechaza. `tools/jlink-check.sh` les inyecta un `module-info` generado con `jdeps` y comprueba el SPI en una imagen real. En un JDK sin `jmods`, `--bind-services` falla: los módulos se listan a mano.

`THIRD_PARTY_LICENSES.md` se regenera con:

```
./mvnw -B -DskipTests install && ./mvnw -B license:aggregate-add-third-party@third-party
```
