# Como ejecutar el sistema en tu laptop (Windows + PowerShell + Docker)

Guia paso a paso para levantar el backend en Windows, probarlo con los datos de demostracion y correr las pruebas.

---

## 1. Requisitos (una sola vez)

| Programa | Para que | Como comprobarlo |
|----------|----------|------------------|
| **Java 21 (JDK)** | Compilar y ejecutar el backend | `java -version` debe decir 21 |
| **Docker Desktop** | Levantar PostgreSQL 16 | `docker --version` y Docker Desktop abierto |
| **Git** | Traer el codigo | `git --version` |
| VS Code + extension **REST Client** (opcional) | Ejecutar `docs/api/ferreteria.http` | |
| **Postman** (opcional) | Ejecutar `docs/api/ferreteria.postman_collection.json` | |

`JAVA_HOME` debe apuntar al JDK 21. Para verlo:

```powershell
java -version
echo $env:JAVA_HOME
```

> No hace falta instalar Maven: el proyecto trae el Maven Wrapper (`mvnw.cmd`).

---

## 2. Traer el trabajo a tu laptop

Todo el trabajo quedo en la rama **`claude/tender-volta-j1fjrk`**.

**Si ya tienes el repositorio clonado** (abre PowerShell en la carpeta del proyecto):

```powershell
git fetch origin
git checkout claude/tender-volta-j1fjrk
git pull origin claude/tender-volta-j1fjrk
```

**Si no lo tienes todavia:**

```powershell
git clone https://github.com/juniorcueva-star/ferreteria-backend.git
cd ferreteria-backend
git checkout claude/tender-volta-j1fjrk
```

---

## 3. Configurar las variables de entorno (`.env`)

Todos los comandos siguientes se ejecutan **dentro de la carpeta `backend`**:

```powershell
cd backend
```

### Si NO tienes archivo `.env`

```powershell
Copy-Item .env.example .env
notepad .env
```

### Si YA tenias un `.env` (de los pasos 1 a 5)

Tu `.env` antiguo solo tiene las variables de la base de datos. **Agrega al final** las nuevas (sin ellas la
aplicacion no arranca porque falta `JWT_SECRET`):

```powershell
Add-Content .env "`nJWT_SECRET=pon_aqui_una_clave_larga_de_al_menos_32_caracteres"
Add-Content .env "ADMIN_USERNAME=admin"
Add-Content .env "ADMIN_PASSWORD=Admin123!"
Add-Content .env "DEMO_PASSWORD=Demo123!"
Add-Content .env "CLOUDINARY_URL="
```

### Que significa cada variable

| Variable | Obligatoria | Descripcion |
|----------|-------------|-------------|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Si | Conexion a PostgreSQL (Docker usa las mismas). Puerto por defecto 5434 |
| `JWT_SECRET` | Si | Clave para firmar los tokens. Minimo 32 caracteres. Inventala |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | Si (la primera vez) | Usuario ADMIN que se crea al arrancar si no existe ninguno |
| `DEMO_PASSWORD` | Para los datos demo | Contrasena de `vendedor1`, `vendedor2` y `almacen1` |
| `CLOUDINARY_URL` | No | Si la dejas vacia, las fotos se guardan en la carpeta `backend/imagenes` |

---

## 4. Levantar la base de datos (PostgreSQL 16 en Docker)

Con Docker Desktop abierto:

```powershell
docker compose up -d
docker compose ps
```

Espera a que la columna STATUS diga `healthy`. La base queda en `localhost:5434`.

---

## 5. Correr las pruebas (opcional pero recomendado)

Las pruebas usan la **misma base de Docker** pero en un esquema separado llamado `pruebas`, asi que no tocan tus datos.
La base debe estar levantada (paso 4).

```powershell
.\mvnw.cmd verify
```

Al final debe decir `BUILD SUCCESS` y `Tests run: 131, Failures: 0, Errors: 0`.

---

## 6. Levantar la aplicacion

```powershell
.\mvnw.cmd spring-boot:run
```

La primera vez veras en la consola:

- `Migrating schema "public" to version "3 - secuencia codigo traslado"` (Flyway crea o actualiza las tablas),
- `Usuario ADMIN inicial 'admin' creado`,
- `Datos de demostracion cargados (usuarios: vendedor1, vendedor2, almacen1)`.

La API queda en **http://localhost:8080**. Para detenerla: `Ctrl + C`.

> Los datos de demostracion solo se cargan con el perfil `dev`, que es el perfil por defecto. Se cargan una sola vez.

---

## 7. Usuarios de demostracion

| Usuario | Contrasena | Rol | Ubicacion | Que puede hacer |
|---------|-----------|-----|-----------|-----------------|
| `admin` | `Admin123!` (tu `ADMIN_PASSWORD`) | ADMIN | Todas | Todo |
| `vendedor1` | `Demo123!` (tu `DEMO_PASSWORD`) | VENDEDOR | Tienda Centro (RUC 20601234561) | Caja, ventas, fiado, clientes de su tienda |
| `vendedor2` | `Demo123!` | VENDEDOR | Tienda Norte (RUC 20609876542) | Caja, ventas, fiado, clientes de su tienda |
| `almacen1` | `Demo123!` | ALMACENERO | Almacen Central | Compras, traslados, ajustes del almacen |

### Datos que ya vienen cargados

- **Ubicaciones:** 1 = Almacen Central, 2 = Tienda Centro, 3 = Tienda Norte.
- **Empresas:** 1 = Ferreteria El Constructor S.A.C., 2 = Ferreteria La Llave E.I.R.L.
- **Proveedores:** 1 = Aceros Arequipa, 2 = Importadora Ferretera del Peru.
- **Clientes:** 1 = Juan Perez Quispe (DNI), 2 = Constructora Los Andes (RUC), 3 = Maria Lopez.
- **15 productos** con stock en las 3 ubicaciones. Algunos con sus presentaciones (id de presentacion entre parentesis):

| Producto | Unidad base | Presentaciones (id) |
|----------|-------------|---------------------|
| 1 Clavo de acero 2" | UNIDAD | Unidad (1) S/0.10, Ciento (2) S/8.00, Millar (3) S/70.00 |
| 6 Martillo de una 16 oz | UNIDAD | Unidad (11) S/35.00 |
| 7 Foco LED 9 W | UNIDAD | Unidad (12) S/6.50, Caja x 10 (13) S/60.00 |
| 11 Clavo para madera 3" | KILO | Kilo (17) S/7.00, Medio kilo (18) S/3.80, Cuarto de kilo (19) S/2.00 |
| 12 Alambre negro N 16 | KILO | Kilo (20) S/6.50, Medio kilo (21) S/3.50 |
| 13 Cable THW 14 AWG | METRO | Metro (22) S/1.30, Rollo 100 m (23) S/115.00 |
| 15 Manguera de jardin 1/2" | METRO | Metro (25) S/2.50, Rollo 25 m (26) S/55.00 |

Los demas se ven con `GET /api/productos`.

---

## 8. Probar en Swagger

1. Abre **http://localhost:8080/swagger-ui.html**
2. En **01. Autenticacion** ejecuta `POST /api/auth/login` con:
   ```json
   { "username": "vendedor1", "password": "Demo123!" }
   ```
3. Copia el valor de `token` de la respuesta.
4. Pulsa el boton **Authorize** (arriba a la derecha), pega el token y pulsa *Authorize*.
5. Ya puedes probar los endpoints. Para cambiar de usuario repite el login y vuelve a pulsar *Authorize*.

Todos los listados aceptan `page` (desde 0), `size` (maximo 100) y `sort` (Ej: `nombre,asc`).
Todos los errores responden con `{ "codigo", "mensaje", "detalle", "fecha" }`.

### Recorrido sugerido por modulo

**a) Catalogo** (usuario `admin`) - `06. Productos` -> `POST /api/productos`:

```json
{
  "codigo": "SIL-280", "nombre": "Silicona transparente 280 ml", "marca": "Sika",
  "categoriaId": 9, "unidadBase": "UNIDAD",
  "presentaciones": [
    { "nombre": "Unidad", "factor": 1, "precioVenta": 12.00, "codigoBarras": "7751234000011" },
    { "nombre": "Caja x 12", "factor": 12, "precioVenta": 130.00 }
  ]
}
```

Anota el `id` del producto y los `id` de sus presentaciones (en una base recien creada: producto 16, "Unidad" 27 y "Caja x 12" 28). Para subir una foto: `POST /api/productos/{id}/imagen`
(campo `archivo`, JPG/PNG/WEBP de hasta 2 MB).

**b) Compras** (usuario `almacen1`) - `08. Compras` -> `POST /api/compras` (cambia los ids por los tuyos):

```json
{
  "empresaId": 1, "proveedorId": 1, "tipoComprobante": "FACTURA", "serieNumero": "F001-4589",
  "fechaEmision": "2026-10-02",
  "detalles": [
    { "productoId": 16, "presentacionId": 28, "cantidad": 5, "precioUnitario": 100.00 },
    { "productoId": 13, "presentacionId": 23, "cantidad": 2, "precioUnitario": 90.00 }
  ]
}
```

Verifica el stock con `10. Inventario` -> `GET /api/inventario/stock?ubicacionId=1`.

**c) Traslado** (usuario `almacen1`) - `09. Traslados` -> `POST /api/traslados`:

```json
{ "destinoId": 2, "detalles": [ { "productoId": 13, "cantidad": 50 } ] }
```

Luego, con `vendedor1`: `POST /api/traslados/{id}/recibir`. El stock pasa del almacen a la Tienda Centro.

**d) Caja y ventas** (usuario `vendedor1`):

1. `12. Caja` -> `POST /api/cajas/abrir` con `{ "montoApertura": 100.00 }`
2. `14. Ventas` -> `POST /api/ventas` (contado, paga 50 y recibe vuelto):
   ```json
   {
     "condicion": "CONTADO",
     "detalles": [ { "presentacionId": 2, "cantidad": 3 }, { "presentacionId": 22, "cantidad": 10 } ],
     "pagos": [ { "metodoPago": "EFECTIVO", "monto": 50.00 } ]
   }
   ```
3. Venta a credito (fiado) con adelanto por Yape:
   ```json
   {
     "condicion": "CREDITO", "clienteId": 1,
     "detalles": [ { "presentacionId": 13, "cantidad": 1 } ],
     "pagos": [ { "metodoPago": "YAPE", "monto": 20.00, "numeroOperacion": "00123456" } ]
   }
   ```
4. Anular una venta: `POST /api/ventas/{id}/anular` con `{ "motivo": "El cliente cambio de opinion" }`.

Metodos de pago validos (`13. Metodos de pago`): EFECTIVO, YAPE, PLIN, DEPOSITO, TRANSFERENCIA, TARJETA, OTRO_QR.
Todos menos EFECTIVO piden `numeroOperacion`.

**e) Fiado** (usuario `vendedor1`) - `15. Fiado`:

- Abono: `POST /api/fiado/ventas/{ventaId}/abonos` con `{ "pagos": [ { "metodoPago": "EFECTIVO", "monto": 20.00 } ] }`
- Deudas: `GET /api/fiado/deudas` - Deudores: `GET /api/fiado/deudores`

**f) Cierre de caja** (usuario `vendedor1`):

1. `GET /api/cajas/actual` muestra el resumen y el `efectivoEsperado`.
2. `POST /api/cajas/{id}/cerrar` con `{ "efectivoContado": 150.00 }` -> responde la `diferencia`.

**g) Reportes** (usuario `admin`) - `16. Reportes` (fechas en formato `AAAA-MM-DD`):

- `GET /api/reportes/ventas-por-tienda?desde=2026-10-01&hasta=2026-10-31`
- `GET /api/reportes/productos-mas-vendidos?desde=...&hasta=...&orden=MONTO`
- `GET /api/reportes/traslados-por-tienda?desde=...&hasta=...`
- `GET /api/reportes/compras-por-proveedor?desde=...&hasta=...`
- `GET /api/reportes/stock-bajo`
- Kardex: `GET /api/inventario/kardex?productoId=13`

**h) Seguridad** - prueba que:

- sin token cualquier endpoint responde **401**;
- `vendedor1` en `GET /api/usuarios` recibe **403** (rol incorrecto);
- `vendedor1` en `GET /api/ventas?ubicacionId=3` recibe **403** (datos de otra tienda).

---

## 9. Probar todo de una vez (coleccion de peticiones)

Con la aplicacion recien levantada y la base **recien creada** (ver punto 10):

- **VS Code + REST Client:** abre `docs/api/ferreteria.http` y pulsa *Send Request* en cada peticion, en orden.
- **Postman:** *Import* -> `docs/api/ferreteria.postman_collection.json` -> clic derecho en la coleccion -> *Run*.
  Son 35 peticiones y cada una comprueba el codigo HTTP esperado.

---

## 10. Empezar de cero (borrar todos los datos)

```powershell
docker compose down -v
docker compose up -d
.\mvnw.cmd spring-boot:run
```

`down -v` borra el volumen de la base de datos. Al arrancar, Flyway vuelve a crear las tablas y se recargan los datos
de demostracion. Para detener la base sin borrar datos: `docker compose down`.

---

## 11. Problemas frecuentes

| Problema | Solucion |
|----------|----------|
| `Configure la variable de entorno JWT_SECRET` o `JWT_SECRET debe tener al menos 32 caracteres` | Revisa el paso 3 |
| `Connection refused` al puerto 5434 | Docker Desktop no esta abierto o falta `docker compose up -d` |
| `port is already allocated` al levantar Docker | Otro programa usa el puerto 5434: cambia `DB_PORT` en `.env` |
| `JAVA_HOME is not set` o version incorrecta | Instala el JDK 21 y configura `JAVA_HOME` |
| No aparecen los usuarios demo | Falta `DEMO_PASSWORD` en `.env`, o la base ya tenia un "Almacen Central" |
| La venta responde "Debe abrir su caja antes de cobrar" | Abre la caja con `POST /api/cajas/abrir` |
| Un reporte sale vacio | Las fechas son dias de Lima; usa un rango que incluya el dia de la venta |
