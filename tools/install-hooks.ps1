# Activa los hooks versionados en tools/hooks (solo cambia la config local del repo).
$ErrorActionPreference = 'Stop'
$root = git rev-parse --show-toplevel
Set-Location $root
git config core.hooksPath tools/hooks
Write-Host 'Hooks activados (core.hooksPath = tools/hooks).'
Write-Host 'Para desactivarlos: git config --unset core.hooksPath'
