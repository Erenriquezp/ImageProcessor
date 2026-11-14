#Requires -Version 7.2
<#
.SYNOPSIS
  Empaqueta Rora Studio para Windows x64: runtime con jlink, app-image con jpackage, ZIP
  reproducible, MSI por usuario, SHA256SUMS y THIRD_PARTY_LICENSES.md.

.DESCRIPTION
  Mismo script en local y en CI (windows-latest). Nunca descarga nada salvo con -FetchTools, y
  entonces solo las versiones fijadas aquí, comprobando su SHA-256.

  Herramientas (en -Tools, por defecto tools/.cache, ignorada por Git):
    downloads/openjfx-25.0.5_windows-x64_bin-jmods.zip   (siempre)
    downloads/wix314-binaries.zip                       (si candle.exe 3.14.1 no está en el PATH)

.EXAMPLE
  pwsh -File tools/package-windows.ps1                       # versión del POM (falla si es SNAPSHOT)
  pwsh -File tools/package-windows.ps1 -Snapshot -SkipTests  # prueba local con una instantánea
#>
[CmdletBinding()]
param(
  [string] $Tools = '',
  [string] $Out = '',
  [switch] $FetchTools,
  [switch] $Snapshot,
  [switch] $SkipTests,
  [switch] $SkipBuild,
  [switch] $NoMsi,
  [switch] $AllowOtherJdk,
  [switch] $UpdateLicenses
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$started = [Diagnostics.Stopwatch]::StartNew()

# ── Versiones fijadas ────────────────────────────────────────────────────────
$JdkVersion = 'Temurin-25.0.5+8'
$Fx = @{ Name = 'javafx-jmods-25.0.5'; Zip = 'openjfx-25.0.5_windows-x64_bin-jmods.zip'
  Url = 'https://download2.gluonhq.com/openjfx/25.0.5/openjfx-25.0.5_windows-x64_bin-jmods.zip'
  Sha = '41c0361e1183852a3693c5d505ed4aa914bb4190e0adc51ae4db28812a48d2ae' }
$Wix = @{ Name = 'wix-3.14.1'; Zip = 'wix314-binaries.zip'
  Url = 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip'
  Sha = '6ac824e1642d6f7277d0ed7ea09411a508f6116ba6fae0aa5f2c7daa2ff43d31'; Version = '3.14.1.8722' }
$OrtJarSha = 'd3f903af31fe8f01300adb6b2b67297aa5bee7895909afc32e76ef29a3921149'
# Nunca cambiar: Windows Installer reconoce por él las versiones anteriores.
$UpgradeUuid = 'b9b3690b-c081-4d82-b116-ca087d07dcce'
$Name = 'Rora Studio'
$FileBase = 'RoraStudio'
$MainModule = 'io.github.erenriquezp.rora.app/io.github.erenriquezp.rora.app.shell.Launcher'
$ProjectUrl = 'https://github.com/Erenriquezp/rora-studio'
# Comillas simples: $LOCALAPPDATA lo expande el lanzador al arrancar, no PowerShell.
$JavaOptions = @(
  '--enable-native-access=javafx.graphics,com.microsoft.onnxruntime',
  '-XX:MaxRAMPercentage=50',
  '-XX:ErrorFile=$LOCALAPPDATA\RoraStudio\logs\hs_err_pid%p.log',
  '-XX:-CreateCoredumpOnCrash',
  '-XX:-UsePerfData',
  '-Djava.io.tmpdir=$LOCALAPPDATA\RoraStudio\tmp',
  '-Drora.packaged=true'
)

$root = Split-Path -Parent $PSScriptRoot
if (-not $Tools) { $Tools = Join-Path $root 'tools\.cache' }
if (-not $Out) { $Out = Join-Path $root 'dist' }
$work = Join-Path $root 'target\package'

function Step([string] $text) {
  Write-Host ("[{0,5:N0} s] {1}" -f $started.Elapsed.TotalSeconds, $text) -ForegroundColor Cyan
}

function Invoke-Checked([string] $exe, [string[]] $arguments) {
  & $exe @arguments
  if ($LASTEXITCODE -ne 0) { throw "Falló ($LASTEXITCODE): $exe $($arguments -join ' ')" }
}

function Get-Sha256([string] $path) {
  (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
}

# Herramienta fijada: carpeta ya extraída y verificada, zip local verificado o descarga (-FetchTools).
function Get-PinnedTool([hashtable] $tool) {
  $dir = Join-Path $Tools $tool.Name
  $marker = Join-Path $dir '.sha256'
  if ((Test-Path $marker) -and ((Get-Content $marker -Raw).Trim() -eq $tool.Sha)) { return $dir }
  $zip = Join-Path $Tools "downloads\$($tool.Zip)"
  if (-not (Test-Path $zip)) {
    if (-not $FetchTools) {
      throw "Falta $($tool.Zip). Descárgalo de $($tool.Url) (SHA-256 $($tool.Sha)) en $(Split-Path $zip), o usa -FetchTools."
    }
    New-Item -ItemType Directory -Force (Split-Path $zip) | Out-Null
    Step "Descargando $($tool.Url)"
    Invoke-WebRequest -Uri $tool.Url -OutFile $zip -UseBasicParsing
  }
  $sha = Get-Sha256 $zip
  if ($sha -ne $tool.Sha) { throw "SHA-256 inesperado en $zip`: $sha (esperado $($tool.Sha))" }
  if (Test-Path $dir) { Remove-Item -Recurse -Force $dir }
  Expand-Archive -LiteralPath $zip -DestinationPath $dir
  Set-Content -Path $marker -Value $tool.Sha -NoNewline
  return $dir
}

function Get-ModuleName([string] $jar) {
  $d = & $jdkBin\jar.exe --describe-module --file $jar 2>&1 | Out-String
  if ($d -match 'No module descriptor') { return $null }
  return ($d.Trim() -split '\s+')[0] -replace '@.*$', ''
}

# Nombre que Java deriva para un JAR sin module-info (p. ej. com.twelvemonkeys.common.lang).
function Get-AutomaticName([string] $jar) {
  $lines = & $jdkBin\jar.exe --describe-module --file $jar 2>&1
  foreach ($l in $lines) {
    if ("$l" -match '^(\S+?)(@\S+)?\s+automatic$') { return $Matches[1] }
  }
  throw "No se pudo derivar el módulo de $jar"
}

# ── 1. Comprobaciones ────────────────────────────────────────────────────────
Step 'Comprobaciones'
$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { Split-Path -Parent (Split-Path -Parent (Get-Command java).Source) }
$jdkBin = Join-Path $javaHome 'bin'
$release = Get-Content (Join-Path $javaHome 'release') -Raw
$implementor = if ($release -match 'IMPLEMENTOR_VERSION="([^"]+)"') { $Matches[1] } else { '?' }
if ($implementor -ne $JdkVersion) {
  if (-not $AllowOtherJdk) { throw "JDK $implementor; se espera $JdkVersion (o usa -AllowOtherJdk)." }
  Write-Warning "JDK $implementor en lugar de ${JdkVersion}: el resultado no será reproducible."
}
[xml] $pom = Get-Content (Join-Path $root 'pom.xml')
$pomVersion = $pom.project.version
if ($pomVersion -notmatch '^(\d+\.\d+\.\d+)(-SNAPSHOT)?$') { throw "Versión del POM no válida: $pomVersion" }
$appVersion = $Matches[1]
$isSnapshot = [bool] $Matches[2]
if ($isSnapshot -and -not $Snapshot) { throw "La versión $pomVersion es una instantánea: usa -Snapshot para una prueba local." }
$fileVersion = if ($isSnapshot) { "$appVersion-dev" } else { $appVersion }
Push-Location $root
try {
  $dirty = (& git status --porcelain --untracked-files=no) -join "`n"
  $commit = (& git rev-parse --short HEAD).Trim()
  $epoch = [long] (& git log -1 --format=%ct).Trim()
  # Un checkout anterior a .gitattributes puede conservar CRLF en archivos que van dentro del
  # paquete (LICENSE): el ZIP ya no coincidiría con el de un clon nuevo o el del CI.
  $staleEol = @(& git ls-files --eol | Where-Object { $_ -match '^\S+\s+w/crlf\s+attr/.*eol=lf' } |
      ForEach-Object { ($_ -split "`t", 2)[1] })
} finally { Pop-Location }
if ($staleEol.Count -gt 0) {
  throw ("Archivos con CRLF en el checkout aunque .gitattributes pide LF: $($staleEol -join ', '). " +
    'Bórralos y recupéralos con git checkout -- <archivo> (el contenido en Git no cambia).')
}
if ($dirty) { Write-Warning 'Hay cambios sin confirmar: el paquete no corresponde exactamente a un commit.' }
$fxDir = Get-PinnedTool $Fx
$fxJmods = (Get-ChildItem $fxDir -Recurse -Filter 'javafx.base.jmod' | Select-Object -First 1).DirectoryName
if (-not $NoMsi) {
  $candle = Get-Command candle.exe -ErrorAction SilentlyContinue
  if ($candle -and $candle.Version.ToString() -eq $Wix.Version) {
    $wixBin = Split-Path $candle.Source
  } else {
    $wixBin = Get-PinnedTool $Wix
  }
  $env:PATH = "$wixBin;$env:PATH"
  $wv = (Get-Item (Join-Path $wixBin 'candle.exe')).VersionInfo.FileVersion
  if ($wv -ne $Wix.Version) { throw "WiX $wv; se espera $($Wix.Version)." }
}
Write-Host "  JDK $implementor · versión $pomVersion → $fileVersion · commit $commit · WiX $(if ($NoMsi) { 'no' } else { $Wix.Version })"

# ── 2. Compilación y licencias ───────────────────────────────────────────────
Push-Location $root
try {
  if (-not $SkipBuild) {
    Step ($SkipTests ? 'Maven: install sin tests' : 'Maven: install con todos los tests')
    $mvnArgs = @('-B', '-ntp', 'clean', 'install')
    if ($SkipTests) { $mvnArgs += '-DskipTests' }
    Invoke-Checked "$root\mvnw.cmd" $mvnArgs
    Step 'THIRD_PARTY_LICENSES.md'
    Invoke-Checked "$root\mvnw.cmd" @('-B', '-ntp', '-q', 'license:aggregate-add-third-party@third-party')
    # El plugin escribe CRLF en Windows; el repositorio usa LF.
    $tpl = Join-Path $root 'THIRD_PARTY_LICENSES.md'
    [IO.File]::WriteAllText($tpl, ([IO.File]::ReadAllText($tpl) -replace "`r`n", "`n"))
    & git diff --quiet -- THIRD_PARTY_LICENSES.md
    if ($LASTEXITCODE -ne 0 -and -not $UpdateLicenses) {
      throw 'THIRD_PARTY_LICENSES.md no estaba al día: revisa el cambio, confírmalo y vuelve a empaquetar (o usa -UpdateLicenses).'
    }
  }
} finally { Pop-Location }

# ── 3. Ensayo de módulos ─────────────────────────────────────────────────────
Step 'Módulos: dependencias, ORT sin nativos y module-info para los automáticos'
if (Test-Path $work) { Remove-Item -Recurse -Force $work }
$libs = New-Item -ItemType Directory -Force (Join-Path $work 'libs')
Push-Location $root
try {
  Invoke-Checked "$root\mvnw.cmd" @('-B', '-ntp', '-q', '-pl', 'app', 'dependency:copy-dependencies',
    '-DincludeScope=runtime', "-DoutputDirectory=$libs")
} finally { Pop-Location }
Copy-Item (Join-Path $root "app\target\rora-app-$pomVersion.jar") $libs
Get-ChildItem $libs -Filter 'javafx-*.jar' | Remove-Item   # JavaFX sale de los jmods
$ortJar = Get-ChildItem $libs -Filter 'onnxruntime-*.jar' | Select-Object -First 1
if ((Get-Sha256 $ortJar.FullName) -ne $OrtJarSha) { throw "SHA-256 inesperado en $($ortJar.Name)" }
$ortVersion = $ortJar.BaseName -replace '^onnxruntime-', ''
$x = New-Item -ItemType Directory -Force (Join-Path $work 'ort-x')
Push-Location $x
try { Invoke-Checked "$jdkBin\jar.exe" @('xf', $ortJar.FullName) } finally { Pop-Location }
$ortNative = Join-Path $work 'ort-native'
New-Item -ItemType Directory -Force $ortNative | Out-Null
Copy-Item (Join-Path $x 'ai\onnxruntime\native\win-x64\onnxruntime.dll'), (Join-Path $x 'ai\onnxruntime\native\win-x64\onnxruntime4j_jni.dll') $ortNative
$ortNotices = Join-Path $work 'ort-notices'
New-Item -ItemType Directory -Force $ortNotices | Out-Null
Copy-Item (Join-Path $x 'ThirdPartyNotices.txt'), (Join-Path $x 'Privacy.md') $ortNotices
Remove-Item -Recurse -Force (Join-Path $x 'ai\onnxruntime\native')
Remove-Item $ortJar.FullName
Invoke-Checked "$jdkBin\jar.exe" @('--create', '--file', (Join-Path $libs "onnxruntime-$ortVersion.jar"),
  '--manifest', (Join-Path $x 'META-INF\MANIFEST.MF'), '-C', $x, '.')

# Los automáticos (TwelveMonkeys y ORT) solo dependen de java.* y entre sí: se analizan aparte.
$autoDir = New-Item -ItemType Directory -Force (Join-Path $work 'auto')
Get-ChildItem $libs -Filter '*.jar' | Where-Object { -not (Get-ModuleName $_.FullName) } | Move-Item -Destination $autoDir
$auto = @(Get-ChildItem $autoDir -Filter '*.jar')
$gen = Join-Path $work 'gen'
Invoke-Checked "$jdkBin\jdeps.exe" (@('--multi-release', '21', '--ignore-missing-deps', '--module-path', $autoDir,
  '--generate-module-info', $gen) + ($auto | ForEach-Object { $_.FullName }))
$tmModules = @()
foreach ($jar in $auto) {
  $mod = Get-AutomaticName $jar.FullName
  $mi = Get-ChildItem (Join-Path $gen $mod) -Recurse -Filter 'module-info.java' | Select-Object -First 1
  if ($mod -eq 'com.twelvemonkeys.common.lang') {
    # D10: solo un par de utilidades usan java.sql; el WebP no las necesita (lo comprueba el humo).
    (Get-Content $mi.FullName) | Where-Object { $_ -notmatch 'requires transitive java\.sql;' } | Set-Content $mi.FullName
  }
  $ver = if ($jar.BaseName -match '-(\d+\.\d+\.\d+)$') { $Matches[1] } else { '0' }
  $miOut = Join-Path $work "mi\$mod"
  Invoke-Checked "$jdkBin\javac.exe" @('-nowarn', '--release', '21', '--module-version', $ver, '-d', $miOut,
    '--module-path', $autoDir, '--patch-module', "$mod=$($jar.FullName)", $mi.FullName)
  Invoke-Checked "$jdkBin\jar.exe" @('uf', $jar.FullName, '-C', $miOut, 'module-info.class')
  if ($mod -like 'com.twelvemonkeys.*') { $tmModules += $mod }
}
$auto | Move-Item -Destination $libs

# ── 4. Runtime con jlink ─────────────────────────────────────────────────────
Step 'jlink'
$runtime = Join-Path $work 'runtime'
Invoke-Checked "$jdkBin\jlink.exe" @('--module-path', "$fxJmods;$libs",
  '--add-modules', ((@('io.github.erenriquezp.rora.app') + $tmModules + @('jdk.localedata')) -join ','),
  '--include-locales=en,es', '--strip-debug', '--strip-native-commands', '--no-man-pages',
  '--no-header-files', '--compress=zip-6', '--output', $runtime)
# Enlazando desde la imagen del JDK (sin jmods), jlink no copia los avisos de algunos módulos
# (p. ej. jdk.localedata, con thaidict.md): se toman del mismo JDK.
if ((Get-Content (Join-Path $runtime 'release') -Raw) -match 'MODULES="([^"]*)"') {
  foreach ($m in ($Matches[1] -split ' ') | Where-Object { $_ -match '^(java|jdk)\.' }) {
    $target = Join-Path $runtime "legal\$m"
    $source = Join-Path $javaHome "legal\$m"
    if (-not (Test-Path $target) -and (Test-Path $source)) { Copy-Item -Recurse $source $target }
  }
}
$expected = @{}
Get-Content (Join-Path $root 'ai-engine\src\main\resources\io\github\erenriquezp\rora\ai\onnx\ort-native.sha256') |
  ForEach-Object { $p = $_.Trim() -split '\s+'; if ($p.Count -eq 2 -and $p[1] -like 'win-x64/*') { $expected[$p[1] -replace '^win-x64/', ''] = $p[0] } }
$dest = New-Item -ItemType Directory -Force (Join-Path $runtime 'ort-native')
foreach ($dll in Get-ChildItem $ortNative) {
  if ((Get-Sha256 $dll.FullName) -ne $expected[$dll.Name]) { throw "SHA-256 inesperado en $($dll.Name)" }
  Copy-Item $dll.FullName $dest
}

# ── 5. Icono y app-image ─────────────────────────────────────────────────────
Step 'Icono y app-image'
$iconDir = Join-Path $work 'icon'
Invoke-Checked "$jdkBin\java.exe" @('-Djava.awt.headless=true', (Join-Path $root 'tools\icon\RoraIcon.java'), $iconDir)
$committedIcon = Join-Path $root 'app\src\main\resources\io\github\erenriquezp\rora\app-icon.png'
if ((Get-Sha256 (Join-Path $iconDir 'app-icon.png')) -ne (Get-Sha256 $committedIcon)) {
  throw 'app-icon.png no coincide con tools/icon/RoraIcon.java: regenera el icono.'
}
$imageRoot = Join-Path $work 'image'
$jpArgs = @('--type', 'app-image', '--name', $Name, '--app-version', $appVersion, '--vendor', $Name,
  '--description', 'Editor de fotos no destructivo con mejora por IA local y wallpapers',
  '--copyright', '© 2026 Erenriquezp', '--icon', (Join-Path $iconDir 'RoraStudio.ico'),
  '--runtime-image', $runtime, '--module', $MainModule, '--dest', $imageRoot)
foreach ($o in $JavaOptions) { $jpArgs += @('--java-options', $o) }
Invoke-Checked "$jdkBin\jpackage.exe" $jpArgs
$image = Join-Path $imageRoot $Name

# ── 6. Avisos legales ────────────────────────────────────────────────────────
Step 'Avisos legales'
$legal = Join-Path $image 'legal'
$legalRes = Join-Path $root 'app\src\main\resources\io\github\erenriquezp\rora\app\diag\legal'
New-Item -ItemType Directory -Force (Join-Path $legal 'onnxruntime'), (Join-Path $legal 'fonts'), (Join-Path $legal 'models') | Out-Null
Copy-Item (Join-Path $root 'LICENSE') (Join-Path $legal 'LICENSE.txt')
Copy-Item (Join-Path $root 'THIRD_PARTY_LICENSES.md') $legal
Copy-Item (Join-Path $legalRes 'SOURCE.txt') $legal
Copy-Item (Join-Path $legalRes "onnxruntime-$ortVersion-LICENSE.txt") (Join-Path $legal 'onnxruntime\LICENSE.txt')
Copy-Item (Join-Path $ortNotices '*') (Join-Path $legal 'onnxruntime')
Copy-Item (Join-Path $legalRes 'FontAwesome-6.5.2-LICENSE.txt') (Join-Path $legal 'fonts')
Copy-Item (Join-Path $root 'app\src\main\resources\io\github\erenriquezp\rora\app\theme\fonts\LICENSE-IBMPlexSans.txt') (Join-Path $legal 'fonts\IBM-Plex-OFL.txt')
Copy-Item (Join-Path $root 'ai-engine\src\main\resources\io\github\erenriquezp\rora\ai\models\MODEL_NOTICE.md') (Join-Path $legal 'models')

# ── 7. Lanzador con UTF-8 y comprobación del árbol ───────────────────────────
Step 'Manifiesto UTF-8 del lanzador'
Invoke-Checked 'pwsh' @('-NoProfile', '-File', (Join-Path $PSScriptRoot 'package\Set-Utf8Manifest.ps1'), '-Exe', (Join-Path $image "$Name.exe"))
Step 'Comprobación del árbol'
Invoke-Checked 'pwsh' @('-NoProfile', '-File', (Join-Path $PSScriptRoot 'package\verify-package.ps1'),
  '-Image', $image, '-Version', $appVersion, '-PomVersion', $pomVersion, '-JdkHome', $javaHome)

# ── 8. ZIP reproducible ──────────────────────────────────────────────────────
New-Item -ItemType Directory -Force $Out | Out-Null
$zipPath = Join-Path $Out "$FileBase-$fileVersion-win-x64.zip"
Step 'ZIP'
Invoke-Checked "$jdkBin\java.exe" @((Join-Path $PSScriptRoot 'package\ReproZip.java'), $image, $zipPath, "$epoch")
$artifacts = @($zipPath)

# ── 9. MSI por usuario ───────────────────────────────────────────────────────
if (-not $NoMsi) {
  Step 'MSI (WiX)'
  $msiTemp = Join-Path $work 'msi-temp'
  $msiOut = Join-Path $work 'msi'
  Invoke-Checked "$jdkBin\jpackage.exe" @('--type', 'msi', '--app-image', $image, '--name', $Name,
    '--app-version', $appVersion, '--vendor', $Name,
    '--description', 'Editor de fotos no destructivo con mejora por IA local y wallpapers',
    '--copyright', '© 2026 Erenriquezp', '--win-per-user-install', '--install-dir', "Programs\$Name",
    '--win-menu', '--win-menu-group', $Name, '--win-shortcut', '--win-shortcut-prompt',
    '--win-upgrade-uuid', $UpgradeUuid, '--license-file', (Join-Path $root 'LICENSE'),
    '--about-url', $ProjectUrl, '--win-help-url', "$ProjectUrl/issues",
    '--resource-dir', (Join-Path $PSScriptRoot 'package\wix'), '--temp', $msiTemp, '--dest', $msiOut)
  # El main.wxs propio retira la carpeta intermedia %LOCALAPPDATA%\Programs por su id generado.
  if (-not (Select-String -Path (Join-Path $msiTemp 'config\bundle.wxf') -Pattern 'dir3b50371ef2d03bb0b1971278ecbab3a3' -Quiet)) {
    throw 'jpackage cambió el id de la carpeta Programs: actualiza tools/package/wix/main.wxs.'
  }
  $msiPath = Join-Path $Out "$FileBase-$fileVersion-win-x64.msi"
  Move-Item -Force (Get-ChildItem $msiOut -Filter '*.msi' | Select-Object -First 1).FullName $msiPath
  Invoke-Checked 'pwsh' @('-NoProfile', '-File', (Join-Path $PSScriptRoot 'package\verify-package.ps1'),
    '-Image', $image, '-Version', $appVersion, '-PomVersion', $pomVersion, '-JdkHome', $javaHome,
    '-Msi', $msiPath, '-UpgradeUuid', $UpgradeUuid)
  $artifacts = @($msiPath) + $artifacts
}

# ── 10. Hashes y resumen ─────────────────────────────────────────────────────
Step 'SHA256SUMS'
$sums = foreach ($a in $artifacts) { "$(Get-Sha256 $a)  $(Split-Path -Leaf $a)" }
[IO.File]::WriteAllText((Join-Path $Out 'SHA256SUMS'), (($sums -join "`n") + "`n"))
Copy-Item (Join-Path $root 'THIRD_PARTY_LICENSES.md') $Out
$imageMb = (Get-ChildItem $image -Recurse -File | Measure-Object Length -Sum).Sum / 1MB
Write-Host ''
Write-Host ("Rora Studio {0} (commit {1}) en {2:N0} s" -f $fileVersion, $commit, $started.Elapsed.TotalSeconds) -ForegroundColor Green
Write-Host ("  app-image: {0:N1} MB en {1}" -f $imageMb, $image)
foreach ($a in $artifacts) {
  Write-Host ("  {0}: {1:N1} MB  {2}" -f (Split-Path -Leaf $a), ((Get-Item $a).Length / 1MB), (Get-Sha256 $a))
}
