#!/bin/sh
# Respaldo de la base de datos de la ferreteria con pg_dump (formato comprimido "custom" de PostgreSQL).
#
# Uso (lo ejecuta el servicio "respaldo" de docker-compose.yml):
#   sh respaldar.sh            -> queda corriendo: un respaldo por dia (el primero del dia en que el sistema
#                                 esta encendido) y borra los de mas de RESPALDO_DIAS dias
#   sh respaldar.sh --ahora    -> un respaldo inmediato con fecha y hora en el nombre, y termina
#
# Conexion: variables PGHOST, PGPORT, PGUSER, PGPASSWORD y PGDATABASE (las lee pg_dump).
# Carpeta: RESPALDO_DIR (por defecto /respaldos). Dias que se guardan: RESPALDO_DIAS (por defecto 30).
set -eu

DIR="${RESPALDO_DIR:-/respaldos}"
DIAS="${RESPALDO_DIAS:-30}"
mkdir -p "$DIR"

registrar() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') $1"
}

# Escribe primero en un archivo .parcial y lo renombra al terminar: nunca queda un respaldo a medias
volcar() {
    archivo="$1"
    if pg_dump --format=custom --no-owner --file="$archivo.parcial"; then
        mv "$archivo.parcial" "$archivo"
        registrar "Respaldo creado: $archivo ($(du -h "$archivo" | cut -f1))"
    else
        rm -f "$archivo.parcial"
        registrar "ERROR: no se pudo crear el respaldo (se vuelve a intentar en una hora)"
        return 1
    fi
}

respaldo_del_dia() {
    archivo="$DIR/ferreteria-$(date +%Y-%m-%d).dump"
    if [ ! -s "$archivo" ]; then
        volcar "$archivo" || true
    fi
    # Solo borra respaldos diarios viejos; los manuales (con hora en el nombre) se conservan
    find "$DIR" -name 'ferreteria-????-??-??.dump' -mtime +"$DIAS" -exec rm -f {} \;
}

if [ "${1:-}" = "--ahora" ]; then
    volcar "$DIR/ferreteria-$(date +%Y-%m-%d_%H%M).dump"
    exit $?
fi

registrar "Respaldo automatico activo: carpeta $DIR, se guardan $DIAS dias"
while true; do
    respaldo_del_dia
    sleep 3600
done
