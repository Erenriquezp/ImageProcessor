#Requires -Version 7.2
<#
.SYNOPSIS
  Humo del paquete en esta máquina, sin JDK en el PATH y desde una carpeta con espacios, tildes y
  emoji: (1) PackageSmoke dentro del runtime empaquetado (24 MP, WebP, YuNet, x4v3, wallpapers),
  con el origen de las DLL cargadas y los restos en %TEMP%; (2) el lanzador real con una foto como
  argumento, hasta que el registro confirma que se abrió, y cierre limpio.
  Herramienta de desarrollo: usa las clases de prueba de app (mvnw test-compile).
#>
param(
  [string] $Image = '',
  [int] $TimeoutSeconds = 300
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $Image) { $Image = Join-Path $root 'target\package\image\Rora Studio' }
$name = 'Rora Studio'
$results = [System.Collections.Generic.List[string]]::new()
$failed = $false
function Note([string] $s) { $results.Add($s); Write-Host $s }
function Fail([string] $s) { $script:failed = $true; Note "FALLO: $s" }

$base = Join-Path $root 'target\smoke'
if (Test-Path $base) { Remove-Item -Recurse -Force $base }
$installDir = Join-Path $base 'Programas de prueba ñ 🎉'
$work = Join-Path $base 'Trabajo con tildes ñandú 🎉'
New-Item -ItemType Directory -Force $installDir, $work | Out-Null
Copy-Item -Recurse $Image (Join-Path $installDir $name)
$app = Join-Path $installDir $name

# Lanzador extra: el .exe de jpackage busca app\<nombre>.cfg; mismo runtime, otra clase principal.
Copy-Item (Join-Path $app "$name.exe") (Join-Path $app 'RoraSmoke.exe')
$cfg = (Get-Content (Join-Path $app "app\$name.cfg")) -replace '^app\.mainmodule=.*$',
  'app.mainmodule=io.github.erenriquezp.rora.app/io.github.erenriquezp.rora.app.shell.PackageSmoke'
$cfg += 'java-options=--patch-module'
$cfg += 'java-options=io.github.erenriquezp.rora.app=$APPDIR\smoke'
$cfg += 'java-options=-Dsmoke.hold.ms=5000'
Set-Content -Path (Join-Path $app 'app\RoraSmoke.cfg') -Value $cfg -Encoding utf8
Copy-Item -Recurse (Join-Path $root 'app\target\test-classes') (Join-Path $app 'app\smoke')

$env:PATH = 'C:\WINDOWS\system32;C:\WINDOWS;C:\WINDOWS\System32\Wbem'
Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
Note "java en el PATH: $([bool](Get-Command java -ErrorAction SilentlyContinue))"
$tempDir = [IO.Path]::GetTempPath()
$tempBefore = @(Get-ChildItem $tempDir -Force -ErrorAction SilentlyContinue | ForEach-Object Name)

# (1) PackageSmoke en el runtime empaquetado
$report = Join-Path $work 'informe.txt'
$webp = Join-Path $root 'imaging-io\src\test\resources\io\gradient-lossless.webp'
$sw = [Diagnostics.Stopwatch]::StartNew()
$p = Start-Process -FilePath (Join-Path $app 'RoraSmoke.exe') -ArgumentList @("`"$report`"", "`"$work`"", "`"$webp`"") -PassThru
$mods = @()
while (-not $p.HasExited -and $sw.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
  if ((Test-Path $report) -and (Select-String -Path $report -Pattern '^HOLD' -Quiet) -and $mods.Count -eq 0) {
    $child = Get-CimInstance Win32_Process -Filter "ParentProcessId=$($p.Id)" | Select-Object -First 1
    if ($child) {
      $mods = @((Get-Process -Id $child.ProcessId).Modules | Where-Object { $_.FileName -match '(?i)msvcp1|vcruntime|onnx|glass\.dll|prism_' } |
          ForEach-Object { $_.FileName })
    }
  }
  Start-Sleep -Milliseconds 200
}
if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force; Fail 'PackageSmoke no terminó a tiempo' }
$p.WaitForExit()
Note "PackageSmoke: código $($p.ExitCode) en $([int]$sw.Elapsed.TotalSeconds) s"
if (Test-Path $report) { Get-Content $report | ForEach-Object { Note "  $_" } } else { Fail 'sin informe' }
if (-not ((Test-Path $report) -and (Select-String -Path $report -Pattern '^SMOKE OK' -Quiet))) { Fail 'PackageSmoke no acabó bien' }
Note 'DLL nativas cargadas (VC++, ORT, JavaFX):'
foreach ($m in $mods) { Note "  $($m.Replace($app, '<app>'))" }
$sys = @($mods | Where-Object { $_ -match '(?i)\\windows\\system32\\(msvcp1|vcruntime)' })
if ($mods.Count -eq 0) { Fail 'no se pudieron leer las DLL cargadas' }
elseif ($sys.Count -gt 0) { Fail "DLL de Visual C++ tomadas de System32: $($sys -join ', ')" }
elseif (-not ($mods | Where-Object { $_ -like "*\runtime\ort-native\onnxruntime.dll" })) { Fail 'ORT no cargó desde runtime\ort-native' }
$written = @(Get-ChildItem (Join-Path $work 'Fondos de prueba ñ 🎉') -Recurse -File -ErrorAction SilentlyContinue)
if ($written.Count -ne 3) { Fail "wallpapers escritos: $($written.Count) de 3" } else { Note 'wallpapers: 3 archivos en una carpeta con tildes y emoji' }

# (2) Lanzador real con una foto como argumento
$photo = Join-Path $work 'foto 24MP ñ 🎉.jpg'
$logs = Join-Path $env:LOCALAPPDATA 'RoraStudio\logs'
# NTFS no actualiza la fecha de un archivo que JUL mantiene abierto: se cuentan las líneas.
function Count-Opened {
  $n = 0
  foreach ($f in Get-ChildItem $logs -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^rora-\d+\.log(\.\d+)?$' }) {
    $fs = [IO.File]::Open($f.FullName, 'Open', 'Read', 'ReadWrite')
    $text = (New-Object IO.StreamReader($fs, [Text.Encoding]::UTF8)).ReadToEnd()
    $fs.Dispose()
    $n += ([regex]::Matches($text, 'Imagen abierta: 6000x4000 JPEG')).Count
  }
  return $n
}
$before = Count-Opened
$sw.Restart()
$l = Start-Process -FilePath (Join-Path $app "$name.exe") -ArgumentList "`"$photo`"" -PassThru
$jvm = $null
while ($sw.Elapsed.TotalSeconds -lt 90 -and -not $l.HasExited) {
  if (-not $jvm) {
    $c = Get-CimInstance Win32_Process -Filter "ParentProcessId=$($l.Id)" | Select-Object -First 1
    if ($c) { $jvm = Get-Process -Id $c.ProcessId }
  }
  if ($jvm) { $jvm.Refresh(); if ($jvm.MainWindowHandle -ne 0) { break } }
  Start-Sleep -Milliseconds 50
}
Note "lanzador real: ventana en $($sw.ElapsedMilliseconds) ms"
$opened = $false
while ($sw.Elapsed.TotalSeconds -lt 120 -and -not $opened) {
  if ((Count-Opened) -gt $before) { $opened = $true; Note "registro: 'Imagen abierta: 6000x4000 JPEG' a los $($sw.ElapsedMilliseconds) ms" }
  else { Start-Sleep -Milliseconds 250 }
}
if (-not $opened) { Fail 'el registro no confirma que se abriera la foto pasada por argumento' }
if ($jvm) {
  [void]$jvm.CloseMainWindow()
  $closed = $jvm.WaitForExit(20000) -and $l.WaitForExit(5000)
  Note "cierre con WM_CLOSE: $closed, código del lanzador $(if ($closed) { $l.ExitCode } else { 'n/a' })"
  if (-not $closed) { Stop-Process -Id $jvm.Id -Force; Fail 'no se cerró' }
}
Start-Sleep -Milliseconds 500
$alive = @(Get-CimInstance Win32_Process | Where-Object { $_.ExecutablePath -and $_.ExecutablePath.StartsWith($app) })
if ($alive.Count -gt 0) { Fail "procesos vivos desde la carpeta de la app: $($alive.Count)" } else { Note 'ningún proceso vivo' }
try {
  Rename-Item -LiteralPath $app -NewName "$name-renombrada"
  Rename-Item -LiteralPath (Join-Path $installDir "$name-renombrada") -NewName $name
  Note 'carpeta renombrable tras cerrar: sin archivos bloqueados'
} catch { Fail "archivos bloqueados: $($_.Exception.Message)" }

# Restos en %TEMP% (solo los que crearía Java, ORT o JavaFX)
$tempAfter = @(Get-ChildItem $tempDir -Force -ErrorAction SilentlyContinue | ForEach-Object Name)
$new = @($tempAfter | Where-Object { $tempBefore -notcontains $_ } | Where-Object { $_ -match '(?i)onnxruntime|hsperf|openjfx|javafx|jna|rora|imageio|\+~JF' })
if ($new.Count -gt 0) { Fail "restos nuevos en %TEMP%: $($new -join ', ')" } else { Note 'sin restos nuevos en %TEMP%' }

$results | Set-Content -Path (Join-Path $base 'smoke-result.txt') -Encoding utf8
if ($failed) { Write-Host 'Humo del paquete: FALLO' -ForegroundColor Red; exit 1 }
Write-Host 'Humo del paquete: OK' -ForegroundColor Green
