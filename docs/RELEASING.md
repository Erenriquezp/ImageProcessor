# Publicar una versión

Las versiones se publican solo para Windows x64: un MSI por usuario, un ZIP portable y `SHA256SUMS`. El workflow `release.yml` construye los artefactos y crea un **borrador**. Publicarlo es siempre manual.

> **Estado:** `ci.yml` y `release.yml` no se han ejecutado todavía. Lo verificado es el empaquetado local con `tools/package-windows.ps1` (fase 8).

## Qué hace falta

| Herramienta | Versión | Dónde |
|---|---|---|
| JDK | Temurin 25.0.5+8 exacto (el script lo comprueba) | Instalado |
| PowerShell | 7.2 o superior | Instalado |
| JavaFX jmods win-x64 | 25.0.5, SHA-256 `41c0361e…48d2ae` | `tools/.cache/downloads/`, o `-FetchTools` |
| WiX Toolset | 3.14.1 (binarios), SHA-256 `6ac824e1…2ff43d31` | En el PATH o en `tools/.cache/downloads/`, o `-FetchTools` |

`tools/.cache` está ignorada por Git. `-FetchTools` descarga solo esas versiones y comprueba el hash. Las URL y hashes completos están al principio de `tools/package-windows.ps1`.

## Prueba local con la versión de desarrollo

```powershell
pwsh -File tools/package-windows.ps1 -Snapshot             # compila con tests y empaqueta (unos 9 min)
pwsh -File tools/package-windows.ps1 -Snapshot -SkipTests  # más rápido
pwsh -File tools/package/smoke-package.ps1                 # humo sin JDK en el PATH
pwsh -File tools/package/verify-repro.ps1 -Snapshot        # el ZIP sale igual dos veces
pwsh -File tools/package/test-msi.ps1 -OldMsi <antiguo.msi> -NewMsi <nuevo.msi>   # instalar, actualizar, desinstalar
```

Con `-Snapshot` los archivos llevan el sufijo `-dev` (`RoraStudio-0.1.0-dev-win-x64.msi`). El script se niega a empaquetar si `THIRD_PARTY_LICENSES.md` no está al día (`-UpdateLicenses` lo regenera) o si algún archivo del checkout tiene CRLF donde `.gitattributes` pide LF.

## Pasos para publicar `X.Y.Z`

1. Parte de `develop` con todo fundido y `./mvnw -B clean verify` en verde.
2. `CHANGELOG.md`: renombra `## [Unreleased]` a `## [X.Y.Z] - AAAA-MM-DD` y deja un `## [Unreleased]` vacío encima. El workflow toma las notas de esa sección y falla si no existe.
3. Versión en los 8 POM (la raíz y la referencia al padre de cada módulo):

   ```powershell
   Get-ChildItem pom.xml, */pom.xml | ForEach-Object {
     $t = [IO.File]::ReadAllText($_.FullName)
     $re = [regex] '<version>X\.Y\.Z-SNAPSHOT</version>'
     [IO.File]::WriteAllText($_.FullName, $re.Replace($t, '<version>X.Y.Z</version>', 1))
   }
   git diff --stat   # 8 archivos, una línea cada uno
   ```

4. Empaqueta en local **sin** `-Snapshot` y comprueba humo y MSI:

   ```powershell
   pwsh -File tools/package-windows.ps1
   pwsh -File tools/package/smoke-package.ps1
   ```

5. Commit `chore(release): X.Y.Z`, funde en `main` y crea la etiqueta anotada:

   ```bash
   git tag -a vX.Y.Z -m "Rora Studio X.Y.Z"
   git push origin main vX.Y.Z
   ```

   El workflow solo se dispara con etiquetas `vN.N.N` e ignora `v*-academic`. Comprueba que la etiqueta coincide con la versión del POM.

6. Espera a que `release.yml` termine: construye en `windows-latest`, verifica la reproducibilidad del ZIP y crea el borrador con MSI, ZIP, `SHA256SUMS` y `THIRD_PARTY_LICENSES.md`.
7. Descarga los artefactos del borrador y compruébalos:

   ```powershell
   Get-FileHash .\RoraStudio-X.Y.Z-win-x64.zip -Algorithm SHA256   # debe coincidir con SHA256SUMS
   ```

   El ZIP debe coincidir **byte a byte** con el que generaste en el paso 4 desde el mismo commit. El MSI no: WiX genera un código de paquete y fechas nuevos en cada compilación.
8. Revisa las notas del borrador y publícalo.
9. Vuelve a la siguiente instantánea en `develop` (`X.Y.(Z+1)-SNAPSHOT` en los 8 POM).

## Actualizaciones y compatibilidad

- El `UpgradeCode` del MSI es fijo (`b9b3690b-c081-4d82-b116-ca087d07dcce`): instalar una versión mayor sustituye a la anterior sin preguntar y deja una sola entrada en Aplicaciones. jpackage impide por defecto instalar una versión menor encima (no está probado). **No cambies el `UpgradeCode`**.
- La comprobación de actualizaciones de la app solo mira releases **publicadas** con etiqueta `vN.N.N`: los borradores no se anuncian.
- Las versiones del MSI van de 0 a 255 en los dos primeros números y hasta 65535 en el tercero.

## Firma de código

Los artefactos no están firmados, así que SmartScreen avisará de un "editor desconocido" (el README explica cómo continuar). La vía gratuita estudiada es SignPath Foundation: exige repositorio público, compilación en CI y una aprobación manual por versión. Si se adopta, el workflow subiría los artefactos sin firmar, esperaría la firma y solo después calcularía `SHA256SUMS`.
