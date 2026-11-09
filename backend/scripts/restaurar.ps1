# Restaura la base de datos desde un respaldo (.dump). REEMPLAZA todos los datos actuales por los del respaldo.
# Uso, desde la carpeta backend (con el backend DETENIDO: Ctrl + C en su ventana):
#   powershell -ExecutionPolicy Bypass -File scripts\restaurar.ps1 respaldos\ferreteria-2026-11-09.dump
# Antes de restaurar se crea un respaldo de seguridad de los datos actuales, por si hay que volver atras.
param([Parameter(Mandatory = $true)][string]$Archivo)
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

if (-not (Test-Path $Archivo)) {
    Write-Host "No existe el archivo $Archivo" -ForegroundColor Red
    exit 1
}
# Usuario y base de datos: se leen del archivo .env (las contrasenas no se muestran)
$variables = @{}
foreach ($linea in Get-Content '.env') {
    if ($linea -match '^\s*([A-Z_]+)\s*=\s*(.*)$') {
        $variables[$Matches[1]] = $Matches[2].Trim()
    }
}
$usuario = $variables['DB_USER']
$base = $variables['DB_NAME']
if (-not $usuario -or -not $base) {
    Write-Host 'Faltan DB_USER o DB_NAME en el archivo .env' -ForegroundColor Red
    exit 1
}

Write-Host "Se REEMPLAZARAN todos los datos de la base '$base' por los del respaldo $Archivo." -ForegroundColor Yellow
Write-Host 'Detenga antes el backend (Ctrl + C en su ventana).'
$respuesta = Read-Host 'Escriba SI para continuar'
if ($respuesta -ne 'SI') {
    Write-Host 'Cancelado: no se cambio nada.'
    exit 0
}

Write-Host 'Creando un respaldo de seguridad de los datos actuales...'
docker exec ferreteria-respaldo sh /respaldar.sh --ahora
if ($LASTEXITCODE -ne 0) { Write-Host 'No se pudo crear el respaldo de seguridad: no se restauro nada.' -ForegroundColor Red; exit 1 }

docker cp $Archivo ferreteria-postgres:/tmp/restaurar.dump
docker exec ferreteria-postgres pg_restore --clean --if-exists --no-owner -U $usuario -d $base /tmp/restaurar.dump
$resultado = $LASTEXITCODE
docker exec ferreteria-postgres rm -f /tmp/restaurar.dump | Out-Null
if ($resultado -ne 0) {
    Write-Host 'La restauracion termino con avisos o errores (revise los mensajes de arriba).' -ForegroundColor Yellow
    exit $resultado
}
Write-Host 'Base de datos restaurada. Ya puede volver a levantar el backend.' -ForegroundColor Green
