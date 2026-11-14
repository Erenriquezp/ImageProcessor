#Requires -Version 7.2
<#
.SYNOPSIS
  Comprueba que el empaquetado es reproducible: genera el app-image y el ZIP dos veces desde los
  mismos JAR (sin recompilar ni MSI) y compara el ZIP byte a byte y, si difiere, archivo a archivo.
  El MSI no es reproducible (WiX genera un PackageCode nuevo y fechas) y queda fuera.
#>
param([switch] $Snapshot, [switch] $AllowOtherJdk)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$runs = @()
foreach ($n in 'a', 'b') {
  $out = Join-Path $root "target\repro-$n"
  if (Test-Path $out) { Remove-Item -Recurse -Force $out }
  $a = @('-NoProfile', '-File', (Join-Path $root 'tools\package-windows.ps1'), '-SkipBuild', '-NoMsi', '-Out', $out)
  if ($Snapshot) { $a += '-Snapshot' }
  if ($AllowOtherJdk) { $a += '-AllowOtherJdk' }
  & pwsh @a
  if ($LASTEXITCODE -ne 0) { throw "Falló el empaquetado $n" }
  $zip = Get-ChildItem $out -Filter '*.zip' | Select-Object -First 1
  $runs += [pscustomobject]@{ Zip = $zip.FullName; Sha = (Get-FileHash $zip.FullName -Algorithm SHA256).Hash }
}
if ($runs[0].Sha -eq $runs[1].Sha) {
  Write-Host "Reproducible: el ZIP es idéntico en las dos ejecuciones ($($runs[0].Sha.ToLowerInvariant()))" -ForegroundColor Green
  exit 0
}
Write-Host 'El ZIP difiere; archivos distintos:' -ForegroundColor Red
Add-Type -AssemblyName System.IO.Compression.FileSystem
$maps = foreach ($r in $runs) {
  $z = [IO.Compression.ZipFile]::OpenRead($r.Zip)
  $m = @{}
  foreach ($e in $z.Entries) {
    $s = $e.Open(); $sha = [Security.Cryptography.SHA256]::Create()
    $m[$e.FullName] = [Convert]::ToHexString($sha.ComputeHash($s)); $s.Dispose()
  }
  $z.Dispose(); , $m
}
foreach ($k in ($maps[0].Keys + $maps[1].Keys | Sort-Object -Unique)) {
  if ($maps[0][$k] -ne $maps[1][$k]) { Write-Host "  $k" }
}
exit 1
