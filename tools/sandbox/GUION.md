# Prueba en máquina limpia

Objetivo: instalar Rora Studio en un Windows sin JDK ni Visual C++, usarlo y desinstalarlo. Anota en cada paso **lo que pasó** (y el texto exacto de cualquier error). Si algo falla, sigue con el resto: cada paso es independiente salvo donde se indica.

## Qué necesitas

- La carpeta `dist` que te entrego: `RoraStudio-0.1.0-dev-win-x64.msi`, `RoraStudio-0.1.0-dev-win-x64.zip`, `RoraStudio-0.1.1-dev-win-x64.msi` (solo para el paso 12) y `SHA256SUMS`.
- Una foto de unos 24 MP y otra pequeña (menos de 1 MP), en una carpeta tuya. Cualquier foto sirve; no hace falta que me las envíes.

## Dónde hacerla

| Opción | Requisitos | ¿Limpia? |
|---|---|---|
| Windows Sandbox con `RoraStudio.wsb` (edita `HostFolder` con la ruta de `dist`) | Windows 11 **Pro, Enterprise o Education**; no existe en Home | Sí: Windows recién instalado, sin GPU y sin red |
| Máquina virtual (VirtualBox, GPLv3) con la ISO de evaluación de Windows 11 Enterprise (90 días, gratuita) | Instalar VirtualBox: es tu decisión | Sí |
| Otro PC recién instalado | — | Sí |
| Una cuenta local nueva en tu equipo con un nombre no ASCII (p. ej. `Ñandú 写真`) | Ninguno | **No** para Visual C++ (está en System32), pero sí prueba un perfil con caracteres raros |

Antes de empezar, en PowerShell dentro de la máquina limpia:

```powershell
Get-Command java -ErrorAction SilentlyContinue       # no debe devolver nada
Test-Path C:\Windows\System32\msvcp140_1.dll        # False si no hay Visual C++
[Environment]::OSVersion.Version                     # 10.0.19041 o posterior
```

## Los 14 pasos

| # | Paso | Resultado esperado |
|---|---|---|
| 1 | **Hash.** En la carpeta `dist`: `Get-FileHash .\RoraStudio-0.1.0-dev-win-x64.msi -Algorithm SHA256` y compáralo con la línea de `SHA256SUMS` (en minúsculas). Repite con el ZIP | Coinciden exactamente |
| 2 | **Instalar sin administrador.** Doble clic en el MSI de 0.1.0. SmartScreen avisará de que el editor es desconocido: "Más información" → "Ejecutar de todas formas". Acepta la licencia y deja marcado el acceso directo | No pide contraseña de administrador (UAC). Queda en `%LOCALAPPDATA%\Programs\Rora Studio`, con entrada en el menú Inicio y en el escritorio |
| 3 | **Arranque.** Ábrelo desde el menú Inicio y cronometra hasta ver la ventana | Menos de 15 s la primera vez. Anota el tiempo y el de un segundo arranque |
| 4 | **Foto de 24 MP.** Abrir → tu foto grande | Se abre sin congelar la ventana; abajo a la derecha aparecen el formato y el tamaño |
| 5 | **IA.** Abre la foto pequeña → Mejorar → ×2 → Mejorar | Termina y la insignia de la pila pasa a "Listo". Si en su lugar aparece **"Falta un componente de Windows (Visual C++)"**, anótalo tal cual: la app debe seguir funcionando sin IA (sigue con el paso 6) |
| 6 | **Wallpapers con tildes.** Abre la foto grande → Wallpapers → Exportar → carpeta nueva `Fondos ñandú` en el escritorio | El diálogo muestra el tamaño estimado; se escriben los archivos y el informe final no lista fallos |
| 7 | **Sin GPU.** (Windows Sandbox ya va sin GPU.) Ayuda (botón `?`) → Informe de diagnóstico → sección `[JavaFX]` | "Aceleración por hardware: no (renderizado por software)" y la app se ve y responde con normalidad |
| 8 | **Escala 150 % y 200 %.** Configuración de Windows → Pantalla → Escala; vuelve a abrir la app en cada una | Textos nítidos, nada cortado; el informe muestra la escala nueva en `[Pantallas]` |
| 9 | **Error e informe.** Crea en una carpeta con tu nombre un `prueba.txt` y renómbralo a `foto mia.jpg`; ábrelo desde la app (aviso de error). Después Ayuda → Informe de diagnóstico, y Copiar | Aviso claro de que no se pudo abrir. En el informe **no aparecen** tu nombre de usuario, el de la carpeta ni `foto mia` (las rutas salen como `%USERPROFILE%\…\<foto>.jpg`). Pega aquí lo que veas raro |
| 10 | **Arrastrar sobre el icono.** Cierra la app y arrastra una foto sobre el acceso directo del escritorio | La app se abre con esa foto ya cargada |
| 11 | **Actualizaciones sin red.** Sin conexión (Sandbox ya va sin red, o desconecta el Wi-Fi): Ayuda → Buscar actualizaciones… | En pocos segundos: "Sin conexión: no se pudo comprobar." con el botón "Ver versiones en GitHub". La app no se cuelga |
| 12 | **Versión nueva encima.** Con la app cerrada, instala `RoraStudio-0.1.1-dev-win-x64.msi` | Sin preguntas de desinstalar antes; en Configuración → Aplicaciones sale **una sola** entrada "Rora Studio" 0.1.1; Ayuda → Acerca de dice 0.1.1 |
| 13 | **Desinstalar.** Configuración → Aplicaciones → Rora Studio → Desinstalar. Luego revisa | Desaparecen `%LOCALAPPDATA%\Programs\Rora Studio`, el menú Inicio y el acceso directo. Quedan, por diseño: `%LOCALAPPDATA%\RoraStudio` (registros y temporales), `%APPDATA%\RoraStudio` (tus tamaños de Wallpapers, si creaste alguno) y `HKCU\Software\JavaSoft\Prefs\io\github\erenriquezp\rora` (disposición). Anota cualquier otra cosa |
| 14 | **ZIP portable.** Descomprime el ZIP en `C:\Mis programas de prueba\` (con espacios) y abre `Rora Studio\Rora Studio.exe` | Arranca igual que la versión instalada y abre una foto. Al cerrar, la carpeta se puede borrar |

## Qué me devuelves

- La tabla con lo que pasó en cada paso (tiempos de los pasos 3 y 5).
- El texto del informe del paso 9 (revísalo antes) y, si hubo algún error, los archivos de `%LOCALAPPDATA%\RoraStudio\logs` (también están redactados; revísalos igualmente).
- Si la IA no cargó en el paso 5: la salida de `Test-Path C:\Windows\System32\msvcp140_1.dll` en esa máquina.
