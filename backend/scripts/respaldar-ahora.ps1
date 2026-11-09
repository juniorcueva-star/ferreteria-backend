# Respaldo inmediato de la base de datos (ademas del automatico diario).
# Uso, desde la carpeta backend:   powershell -ExecutionPolicy Bypass -File scripts\respaldar-ahora.ps1
# Requisito: la base levantada con "docker compose up -d" (servicio ferreteria-respaldo).
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

docker exec ferreteria-respaldo sh /respaldar.sh --ahora
if ($LASTEXITCODE -ne 0) {
    Write-Host 'No se pudo crear el respaldo. Revise que Docker Desktop este abierto y que haya ejecutado "docker compose up -d".' -ForegroundColor Red
    exit 1
}
Write-Host ''
Write-Host 'Respaldos guardados en:' (Resolve-Path 'respaldos') -ForegroundColor Green
Get-ChildItem 'respaldos' -Filter 'ferreteria-*.dump' | Sort-Object LastWriteTime -Descending |
    Select-Object -First 5 Name, @{ Name = 'Tamano (KB)'; Expression = { [math]::Round($_.Length / 1KB) } }, LastWriteTime |
    Format-Table -AutoSize
