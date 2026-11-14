# Privacidad

Rora Studio trabaja en tu equipo. No tiene cuentas, ni telemetría, ni anuncios, y no envía nada por su cuenta.

## Red

La única conexión que puede hacer la aplicación es **Ayuda → Buscar actualizaciones…**, y solo cuando pulsas esa opción. Nunca comprueba nada al arrancar ni en segundo plano.

| Aspecto | Qué hace |
|---|---|
| Petición | `GET https://api.github.com/repos/Erenriquezp/rora-studio/releases?per_page=10` |
| Cabeceras | `Accept`, `X-GitHub-Api-Version` y `User-Agent: RoraStudio/<versión>`. Sin cookies, sin autenticación, sin identificadores |
| Límites | 5 s para conectar, 10 s en total, 256 KiB como máximo, redirecciones solo por HTTPS |
| Resultado | Te dice si hay una versión nueva y abre su página de GitHub en tu navegador si lo pides. No descarga ni ejecuta nada |
| Desactivarla | Arranca con `-Drora.updates=off`: la opción desaparece del menú |

GitHub ve tu dirección IP y la versión de Rora Studio, como con cualquier visita a su web (ver su política de privacidad).

El motor de IA (ONNX Runtime) trae de fábrica una telemetría de Windows (ETW) que Rora Studio **apaga** al crear el motor. Un test lo comprueba. Las pruebas de arquitectura impiden que cualquier otra parte del código use la red.

## Tus fotos

- Las fotos se abren y se procesan en memoria. La mejora con IA y la detección de caras se ejecutan en tu CPU; las fotos no salen de tu equipo.
- Los resultados de la IA se guardan temporalmente en `%LOCALAPPDATA%\RoraStudio\cache\ai` y se borran al cerrar la imagen y al salir.
- Al exportar, el modo de metadatos por defecto elimina la ubicación (GPS). La miniatura incrustada no se copia nunca. Detalle en [IO.md](IO.md).

## Registros

La aplicación escribe un registro en `%LOCALAPPDATA%\RoraStudio\logs` (`rora-0.log` a `rora-4.log`, 1 MiB cada uno como máximo) para poder diagnosticar fallos. Antes de escribir cada línea se **redacta**:

| Dato | Se escribe como |
|---|---|
| Tu carpeta de usuario y las conocidas (`C:\Users\<nombre>`, `AppData`, `Temp`) | `%USERPROFILE%`, `%LOCALAPPDATA%`, `%APPDATA%`, `%TEMP%` |
| Carpetas de una ruta que no son de la aplicación | `…` (y en rutas de red, `<servidor>` y `<recurso>`) |
| Nombre de una foto | `<foto>.jpg` (se conserva la extensión) |
| Otros nombres de archivo | `<archivo>.ext` |
| Tu nombre de usuario suelto | `<usuario>` |

Nunca se registran píxeles, metadatos (EXIF, XMP, IPTC, GPS) ni los valores de tus ajustes; de una edición solo quedan los nombres de las operaciones (por ejemplo `adjust.basic`). Al abrir una foto se anota su tamaño y formato (`6000x4000 JPEG`), nunca su nombre.

Si la aplicación se cierra por un fallo interno de Java, queda un `hs_err_pid*.log` en la misma carpeta. Puede contener variables de entorno y rutas, y **no se redacta**: es un archivo de Java, no de Rora Studio. La aplicación no lo envía ni lo copia entero. Al arrancar te avisa y el informe de diagnóstico muestra solo su cabecera redactada. Se guardan los tres últimos.

La aplicación está configurada para **no** escribir volcados de memoria (ni de Java ni de Windows), porque contendrían la foto abierta.

## Informe de diagnóstico

**Ayuda → Informe de diagnóstico…** reúne versiones, sistema, memoria, pantallas, idioma, estado del motor de IA, tamaño y formato de la última foto, el último error y las últimas líneas del registro, todo redactado. No incluye tu nombre, el del equipo, la zona horaria, la GPU, el nombre de la foto, sus metadatos ni tus ajustes.

El texto se muestra **antes** de hacer nada con él, y puedes editarlo. Solo sale del diálogo si pulsas **Copiar** o **Guardar…**, y tal como lo dejaste. La aplicación no tiene ningún botón para enviarlo: si quieres informar de un problema, lo pegas tú en la incidencia de GitHub.

## Qué se guarda en tu equipo

| Ubicación | Contenido | ¿Se borra al desinstalar? |
|---|---|---|
| `%LOCALAPPDATA%\Programs\Rora Studio` | El programa | Sí, entera |
| Menú Inicio y escritorio | Accesos directos | Sí |
| `%LOCALAPPDATA%\RoraStudio\cache` y `tmp` | Caché de la IA y temporales | No, pero la aplicación los vacía al salir y al arrancar |
| `%LOCALAPPDATA%\RoraStudio\logs` | Registros redactados y `hs_err` | No |
| `%APPDATA%\RoraStudio\wallpapers` | Tus tamaños propios de Wallpapers | No: son tuyos |
| `HKCU\Software\JavaSoft\Prefs\io\github\erenriquezp\rora` | Disposición de la ventana y el tiempo medido de la IA en tu equipo | No |

El instalador no distingue entre desinstalar y actualizar, así que no borra tus datos. **Ayuda → Borrar registros y temporales…** borra los temporales, los registros antiguos y los fallos ya vistos. Para borrarlo todo después de desinstalar, en PowerShell:

```powershell
Remove-Item -Recurse -Force "$env:LOCALAPPDATA\RoraStudio", "$env:APPDATA\RoraStudio" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "HKCU:\Software\JavaSoft\Prefs\io\github\erenriquezp\rora" -ErrorAction SilentlyContinue
```

La versión portable (ZIP) guarda los mismos datos en las mismas carpetas, salvo el programa, que queda donde lo descomprimiste.
