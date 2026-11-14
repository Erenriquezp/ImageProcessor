#Requires -Version 7.2
<#
.SYNOPSIS
  Comprueba un app-image de Rora Studio (y su MSI): avisos legales, solo nativos de win-x64,
  opciones del lanzador, versión coincidente en todos los puntos, modelos incluidos y manifiesto UTF-8.
  Sale con 1 si algo falla. Lo llama tools/package-windows.ps1; se puede usar suelto.
#>
param(
  [Parameter(Mandatory)] [string] $Image,
  [Parameter(Mandatory)] [string] $Version,
  [Parameter(Mandatory)] [string] $PomVersion,
  [Parameter(Mandatory)] [string] $JdkHome,
  [string] $Msi = '',
  [string] $UpgradeUuid = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$failures = [System.Collections.Generic.List[string]]::new()
function Check([bool] $ok, [string] $what) {
  if ($ok) { Write-Host "  OK     $what" } else { $failures.Add($what); Write-Host "  FALLO  $what" -ForegroundColor Red }
}
$name = 'Rora Studio'
$rt = Join-Path $Image 'runtime'
$exe = Join-Path $Image "$name.exe"
$cfgPath = Join-Path $Image "app\$name.cfg"

Write-Host "Comprobando $Image"

# Avisos legales
foreach ($f in 'LICENSE.txt', 'THIRD_PARTY_LICENSES.md', 'SOURCE.txt', 'onnxruntime\LICENSE.txt',
  'onnxruntime\ThirdPartyNotices.txt', 'onnxruntime\Privacy.md', 'fonts\FontAwesome-6.5.2-LICENSE.txt',
  'fonts\IBM-Plex-OFL.txt', 'models\MODEL_NOTICE.md') {
  Check (Test-Path (Join-Path $Image "legal\$f")) "legal\$f"
}
$modules = @()
if ((Get-Content (Join-Path $rt 'release') -Raw) -match 'MODULES="([^"]*)"') { $modules = $Matches[1] -split ' ' }
foreach ($m in $modules | Where-Object { $_ -match '^(java|jdk|javafx)\.' }) {
  Check (Test-Path (Join-Path $rt "legal\$m")) "runtime\legal\$m"
}
Check ($modules -contains 'javafx.graphics') 'JavaFX en el runtime'
Check ($modules -notcontains 'java.sql') 'sin java.sql (D10)'
Check ($modules -notcontains 'io.github.erenriquezp.rora.lab') 'sin el Laboratorio'

# Nativos: solo win-x64, ORT solo en runtime\ort-native y con su hash; JavaFX en bin\javafx
$foreign = @(Get-ChildItem $Image -Recurse -File -Include '*.so', '*.dylib', '*.jnilib')
Check ($foreign.Count -eq 0) "sin nativos de otras plataformas ($($foreign.Count))"
$expected = @{}
$table = Join-Path $PSScriptRoot '..\..\ai-engine\src\main\resources\io\github\erenriquezp\rora\ai\onnx\ort-native.sha256'
Get-Content $table | ForEach-Object { $p = $_.Trim() -split '\s+'; if ($p.Count -eq 2 -and $p[1] -like 'win-x64/*') { $expected[$p[1] -replace '^win-x64/', ''] = $p[0] } }
$ortFiles = @(Get-ChildItem $Image -Recurse -File -Filter '*onnxruntime*')
$inPlace = @($ortFiles | Where-Object { $_.DirectoryName -eq (Join-Path $rt 'ort-native') })
Check ($ortFiles.Count -eq 2 -and $inPlace.Count -eq 2) "ORT: 2 bibliotecas, ambas en runtime\ort-native ($($ortFiles.Count))"
foreach ($f in $inPlace) {
  Check ((Get-FileHash $f.FullName -Algorithm SHA256).Hash.ToLowerInvariant() -eq $expected[$f.Name]) "SHA-256 de $($f.Name)"
}
Check (Test-Path (Join-Path $rt 'bin\javafx\glass.dll')) 'DLL de JavaFX en runtime\bin\javafx (sin extraer a .openjfx)'
Check (-not (Test-Path (Join-Path $rt 'bin\java.exe'))) 'sin java.exe (--strip-native-commands)'

# Opciones del lanzador
$cfg = Get-Content $cfgPath -Raw
foreach ($o in '--enable-native-access=javafx.graphics,com.microsoft.onnxruntime', '-XX:MaxRAMPercentage=50',
  '-XX:ErrorFile=$LOCALAPPDATA\RoraStudio\logs\hs_err_pid%p.log', '-XX:-CreateCoredumpOnCrash', '-XX:-UsePerfData',
  '-Djava.io.tmpdir=$LOCALAPPDATA\RoraStudio\tmp', '-Drora.packaged=true') {
  Check ($cfg.Contains("java-options=$o")) "opción $o"
}
Check (-not ($cfg -match 'HeapDump')) 'sin volcados de heap'
Check ($cfg -match 'app.mainmodule=io\.github\.erenriquezp\.rora\.app/io\.github\.erenriquezp\.rora\.app\.shell\.Launcher') 'módulo principal'

# Versión en todos los puntos
Check ($cfg.Contains("java-options=-Djpackage.app-version=$Version")) "versión en el .cfg ($Version)"
$pv = (Get-Item $exe).VersionInfo.ProductVersion
Check ($pv -eq $Version -or $pv -eq "$Version.0") "versión del .exe ($pv)"
$tmp = Join-Path ([IO.Path]::GetTempPath()) ("rora-verify-" + [Guid]::NewGuid().ToString('N'))
try {
  & (Join-Path $JdkHome 'bin\jimage.exe') extract --dir $tmp --include 'regex:.*/app/diag/build\.properties' (Join-Path $rt 'lib\modules')
  $bp = Get-ChildItem $tmp -Recurse -Filter 'build.properties' | Select-Object -First 1
  $bv = if ($bp -and ((Get-Content $bp.FullName -Raw) -match '(?m)^version=(.+)$')) { $Matches[1].Trim() } else { '?' }
  Check ($bv -eq $PomVersion) "versión de 'Acerca de' ($bv)"
} finally { if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp } }
$listing = & (Join-Path $JdkHome 'bin\jimage.exe') list (Join-Path $rt 'lib\modules') | Out-String
Check ($listing -match 'realesr-general-x4v3\.onnx') 'modelo x4v3 incluido'
Check ($listing -match 'face_detection_yunet_2023mar\.onnx') 'modelo YuNet incluido'

# Lanzador con UTF-8 como página activa (rutas CJK y emoji)
$text = [Text.Encoding]::UTF8.GetString([IO.File]::ReadAllBytes($exe))
Check ($text.Contains('<activeCodePage xmlns="http://schemas.microsoft.com/SMI/2019/WindowsSettings">UTF-8</activeCodePage>')) 'manifiesto UTF-8 en el lanzador'

# MSI
if ($Msi) {
  $wi = New-Object -ComObject WindowsInstaller.Installer
  $db = $wi.GetType().InvokeMember('OpenDatabase', 'InvokeMethod', $null, $wi, @($Msi, 0))
  function Prop([string] $p) {
    $view = $db.GetType().InvokeMember('OpenView', 'InvokeMethod', $null, $db, @("SELECT Value FROM Property WHERE Property='$p'"))
    $view.GetType().InvokeMember('Execute', 'InvokeMethod', $null, $view, $null) | Out-Null
    $rec = $view.GetType().InvokeMember('Fetch', 'InvokeMethod', $null, $view, $null)
    if ($null -eq $rec) { return '' }
    return $rec.GetType().InvokeMember('StringData', 'GetProperty', $null, $rec, @(1))
  }
  $msiVersion = Prop 'ProductVersion'
  Check ($msiVersion -eq $Version) "ProductVersion del MSI ($msiVersion)"
  if ($UpgradeUuid) {
    $uc = (Prop 'UpgradeCode').Trim('{}').ToLowerInvariant()
    Check ($uc -eq $UpgradeUuid.ToLowerInvariant()) "UpgradeCode del MSI ($uc)"
  }
  Check ((Prop 'ALLUSERS') -eq '') 'MSI por usuario (sin ALLUSERS)'
  [void][Runtime.InteropServices.Marshal]::ReleaseComObject($db)
  [void][Runtime.InteropServices.Marshal]::ReleaseComObject($wi)
}

if ($failures.Count -gt 0) {
  Write-Host "$($failures.Count) comprobaciones fallidas" -ForegroundColor Red
  exit 1
}
Write-Host 'Paquete correcto' -ForegroundColor Green
