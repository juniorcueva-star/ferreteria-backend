# Respaldos de la base de datos

Los respaldos guardan **todos** los datos del sistema (productos, stock, ventas, cajas, clientes, cotizaciones,
auditoría...). Con un respaldo se puede recuperar todo si se daña la laptop o se borra algo por error.

## 1. Respaldo automático (no hay que hacer nada)

Al levantar la base con `docker compose up -d` también arranca el servicio **ferreteria-respaldo**, que:

- hace **un respaldo por día** (el primero del día en que la laptop está encendida con Docker Desktop abierto);
- lo guarda en la carpeta `C:\ferreteria\ferreteria-backend\backend\respaldos` con el nombre
  `ferreteria-AAAA-MM-DD.dump` (Ej: `ferreteria-2026-11-09.dump`);
- borra solo los respaldos diarios de **más de 30 días** (se cambia con `RESPALDO_DIAS` en el `.env`).

Para comprobar que está funcionando:

```
cd C:\ferreteria\ferreteria-backend\backend
docker compose ps
docker logs ferreteria-respaldo
```

Debe decir `Respaldo automatico activo` y `Respaldo creado: /respaldos/ferreteria-....dump`.

> Si ya tenías la base levantada antes de esta mejora, ejecuta una vez `docker compose up -d` (y `git pull` antes)
> para que se cree el servicio de respaldo.

## 2. Respaldo en este momento (antes de algo importante)

```
cd C:\ferreteria\ferreteria-backend\backend
powershell -ExecutionPolicy Bypass -File scripts\respaldar-ahora.ps1
```

Crea `ferreteria-AAAA-MM-DD_HHMM.dump` (con la hora). Estos respaldos manuales **no** se borran solos.

## 3. Guardar una copia fuera de la laptop (muy recomendado)

Si la laptop se pierde o se daña, los respaldos de su disco también se pierden. Una vez por semana:

- copia la carpeta `respaldos` a una **USB**, o
- súbela a **Google Drive / OneDrive** (arrastra la carpeta al navegador).

Los respaldos tienen datos del negocio: guárdalos en un lugar privado.

## 4. Restaurar un respaldo

Reemplaza **todos** los datos actuales por los del respaldo. Antes de restaurar, el script hace solo un respaldo de
seguridad de lo que hay ahora, por si hay que volver atrás.

1. Detén el backend (Ctrl + C en su ventana). Docker Desktop debe seguir abierto.
2. Ejecuta (cambia el nombre del archivo por el que quieras recuperar):

   ```
   cd C:\ferreteria\ferreteria-backend\backend
   powershell -ExecutionPolicy Bypass -File scripts\restaurar.ps1 respaldos\ferreteria-2026-11-09.dump
   ```

3. Escribe `SI` cuando lo pida. Al terminar, vuelve a levantar el backend (`mvnw.cmd spring-boot:run`).

## 5. En la nube

Si el sistema se publica en Railway, la base de datos está en sus servidores: revisa en el panel de Railway la
opción de respaldos de PostgreSQL de tu plan. Además, una vez por semana se puede bajar una copia a la laptop con
`pg_dump` usando la URL pública de conexión que muestra Railway (pídele ayuda a Claude cuando llegue ese momento).
