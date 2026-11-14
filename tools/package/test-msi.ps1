#Requires -Version 7.2
<#
.SYNOPSIS
  Prueba el MSI por usuario en esta máquina, en silencio y sin administrador: instala la versión
  antigua, arranca y cierra la app instalada, instala la nueva encima (debe quedar una sola
  entrada), desinstala y lista lo que queda. Herramienta de desarrollo; deja la máquina sin la app.
#>
param(
  [Parameter(Mandatory)] [string] $OldMsi,
  [Parameter(Mandatory)] [string] $NewMsi,
  [string] $LogDir = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$name = 'Rora Studio'
if (-not $LogDir) { $LogDir = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'target\msi-test' }
New-Item -ItemType Directory -Force $LogDir | Out-Null
$installDir = Join-Path $env:LOCALAPPDATA "Programs\$name"
$startMenu = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\$name"
$desktop = [Environment]::GetFolderPath('Desktop')
$failed = $false
function Note([string] $s) { Write-Host $s }
function Fail([string] $s) { $script:failed = $true; Write-Host "FALLO: $s" -ForegroundColor Red }

# Productos instalados con ese nombre, según Windows Installer (un MSI por usuario no aparece en
# la clave Uninstall de HKCU: sus datos están en UserData).
function Entries {
  $wi = New-Object -ComObject WindowsInstaller.Installer
  $out = @()
  foreach ($code in $wi.GetType().InvokeMember('Products', 'GetProperty', $null, $wi, $null)) {
    $pn = $wi.GetType().InvokeMember('ProductInfo', 'GetProperty', $null, $wi, @($code, 'ProductName'))
    if ($pn -eq $name) {
      $out += [pscustomobject]@{
        Code = $code
        DisplayVersion = $wi.GetType().InvokeMember('ProductInfo', 'GetProperty', $null, $wi, @($code, 'VersionString'))
      }
    }
  }
  return $out
}

function Msiexec([string[]] $arguments, [string] $log) {
  $p = Start-Process msiexec.exe -ArgumentList ($arguments + @('/qn', '/norestart', '/l*v', "`"$log`"")) -Wait -PassThru
  return $p.ExitCode
}

if (@(Entries).Count -gt 0) { throw "$name ya está instalado: desinstálalo antes de la prueba." }

# 1. Versión antigua
$sw = [Diagnostics.Stopwatch]::StartNew()
$code = Msiexec @('/i', "`"$OldMsi`"") (Join-Path $LogDir 'install-old.log')
Note "instalar $(Split-Path -Leaf $OldMsi): código $code en $([int]$sw.Elapsed.TotalSeconds) s"
if ($code -ne 0) { Fail 'la instalación falló' }
$e = @(Entries)
Note "entradas en Aplicaciones: $($e.Count) · versión $(@($e | ForEach-Object DisplayVersion) -join ', ')"
Note "carpeta: $(Test-Path (Join-Path $installDir "$name.exe")) · menú Inicio: $(Test-Path $startMenu) · escritorio: $(Test-Path (Join-Path $desktop "$name.lnk"))"
if (-not (Test-Path (Join-Path $installDir "$name.exe"))) { Fail "no está en $installDir" }

# 2. La app instalada arranca y se cierra
$sw.Restart()
$l = Start-Process (Join-Path $installDir "$name.exe") -PassThru
$jvm = $null
while ($sw.Elapsed.TotalSeconds -lt 60 -and -not $l.HasExited) {
  if (-not $jvm) {
    $c = Get-CimInstance Win32_Process -Filter "ParentProcessId=$($l.Id)" | Select-Object -First 1
    if ($c) { $jvm = Get-Process -Id $c.ProcessId }
  }
  if ($jvm) { $jvm.Refresh(); if ($jvm.MainWindowHandle -ne 0) { break } }
  Start-Sleep -Milliseconds 50
}
Note "app instalada: ventana en $($sw.ElapsedMilliseconds) ms"
if ($jvm) {
  Start-Sleep -Seconds 2
  [void]$jvm.CloseMainWindow()
  if (-not ($jvm.WaitForExit(20000) -and $l.WaitForExit(5000))) { Fail 'no se cerró'; Stop-Process -Id $jvm.Id -Force }
} else { Fail 'sin ventana' }

# 3. Versión nueva encima
$code = Msiexec @('/i', "`"$NewMsi`"") (Join-Path $LogDir 'install-new.log')
$e = @(Entries)
Note "actualizar a $(Split-Path -Leaf $NewMsi): código $code · entradas: $($e.Count) · versión $(@($e | ForEach-Object DisplayVersion) -join ', ')"
if ($code -ne 0 -or $e.Count -ne 1) { Fail 'la actualización no dejó una sola entrada' }

# 4. Desinstalar
$code = Msiexec @('/x', "`"$NewMsi`"") (Join-Path $LogDir 'uninstall.log')
Note "desinstalar: código $code · entradas: $(@(Entries).Count)"
if ($code -ne 0 -or @(Entries).Count -ne 0) { Fail 'la desinstalación falló' }
Note 'después de desinstalar:'
Note "  $installDir existe: $(Test-Path $installDir)"
Note "  $(Join-Path $env:LOCALAPPDATA 'Programs') existe: $(Test-Path (Join-Path $env:LOCALAPPDATA 'Programs'))"
Note "  menú Inicio: $(Test-Path $startMenu) · acceso en el escritorio: $(Test-Path (Join-Path $desktop "$name.lnk"))"
Note "  HKCU\Software\RoraStudio\Installer: $(Test-Path 'HKCU:\Software\RoraStudio\Installer')"
Note "  quedan (por diseño): %LOCALAPPDATA%\RoraStudio $(Test-Path (Join-Path $env:LOCALAPPDATA 'RoraStudio')) · %APPDATA%\RoraStudio $(Test-Path (Join-Path $env:APPDATA 'RoraStudio')) · preferencias $(Test-Path 'HKCU:\Software\JavaSoft\Prefs\io\github\erenriquezp\rora')"
if (Test-Path $installDir) { Fail 'quedó la carpeta del programa' }
if (Test-Path $startMenu) { Fail 'quedó el menú Inicio' }
if ($failed) { exit 1 }
Write-Host 'MSI: instalar, actualizar y desinstalar OK' -ForegroundColor Green
