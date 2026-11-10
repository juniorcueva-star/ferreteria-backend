# Guía: levantar el sistema de la ferretería en tu laptop (Windows)

Todos los comandos son para **CMD** (la ventana que muestra `C:\Users\LENOVO>`).
Haz los pasos en orden. Si un paso falla, no sigas: copia el error y pídele ayuda a Claude.

---

## Paso 1. Herramientas (ya revisado ✅)

| Herramienta | Versión necesaria | Tu versión |
|---|---|---|
| Git | cualquiera reciente | 2.55 ✅ |
| Java | 21 | 21 (Temurin) ✅ |
| Docker Desktop | cualquiera reciente | 29.4 ✅ |
| Node.js (trae npm) | 20 o superior | 24 ✅ |

Maven no hace falta: el backend trae su propio `mvnw`.

---

## Paso 2. Crear la carpeta y descargar los proyectos

### 2.1 Abrir Docker Desktop
Abre **Docker Desktop** desde el menú Inicio y espera a que diga **Engine running**. Luego revisa:

```
npm -v
docker ps
```

`docker ps` debe mostrar una tabla (aunque esté vacía). Si da error, Docker Desktop no está abierto.

### 2.2 Descargar los dos proyectos
Se usa `C:\ferreteria` y no el Escritorio, porque el Escritorio suele sincronizarse con OneDrive y eso vuelve
muy lento el frontend.

```
mkdir C:\ferreteria
cd C:\ferreteria
git clone -b catalogo-busqueda https://github.com/juniorcueva-star/ferreteria-backend.git
git clone -b catalogo-busqueda https://github.com/juniorcueva-star/ferreteria-frontend.git
dir
```

- Si aparece una ventana de **inicio de sesión de GitHub**, entra con tu cuenta `juniorcueva-star`.
- `dir` debe mostrar dos carpetas:

```
C:\ferreteria\
   ├── ferreteria-backend\    (Java / Spring Boot)
   └── ferreteria-frontend\   (React)
```

---

## Paso 3. Configurar el backend (archivo `.env`)

```
cd C:\ferreteria\ferreteria-backend\backend
copy .env.example .env
notepad .env
```

En el Bloc de notas **cambia solo estas 4 líneas** y guarda (Ctrl + S):

| Línea | Qué poner |
|---|---|
| `DB_PASSWORD=` | una clave para la base de datos (Ej: una palabra tuya con números) |
| `JWT_SECRET=` | una frase larga de **32 caracteres o más**, sin espacios |
| `ADMIN_PASSWORD=` | la contraseña con la que entrará `admin` |
| `DEMO_PASSWORD=` | la contraseña de `vendedor1`, `vendedor2` y `almacen1` |

Todo lo demás se deja igual. **Anota tus contraseñas**: las usarás para entrar.
El archivo `.env` es solo tuyo: nunca se sube a GitHub.

---

## Paso 4. Levantar la base de datos

En la misma ventana (carpeta `backend`):

```
docker compose up -d
docker compose ps
```

Deben aparecer `ferreteria-postgres` (estado **running** o **healthy**) y `ferreteria-respaldo` (**running**).
El segundo hace el **respaldo automático diario** de la base en la carpeta `backend\respaldos`
(ver `docs/RESPALDOS.md`).

---

## Paso 5. Levantar el backend

En la misma ventana:

```
mvnw.cmd spring-boot:run
```

- La primera vez tarda varios minutos (descarga dependencias).
- Está listo cuando aparece una línea con **`Started FerreteriaBackendApplication`**.
- **No cierres esta ventana**: si la cierras, se apaga el backend.

Comprueba en el navegador: http://localhost:8080/swagger-ui/index.html (debe abrir la documentación de la API).

---

## Paso 6. Levantar el frontend

Abre **otra ventana de CMD** (la del backend sigue abierta):

```
cd C:\ferreteria\ferreteria-frontend
npm install
npm run dev
```

- `npm install` tarda unos minutos solo la primera vez.
- Está listo cuando aparece `Local: http://localhost:5173/`.
- **No cierres esta ventana** tampoco.

---

## Paso 7. Entrar al sistema

| Qué | Enlace |
|---|---|
| Sistema (frontend) | http://localhost:5173 |
| API (Swagger) | http://localhost:8080/swagger-ui/index.html |

| Usuario | Rol | Contraseña |
|---|---|---|
| `admin` | Administrador (ve todo) | la que pusiste en `ADMIN_PASSWORD` |
| `vendedor1` | Vendedor · Tienda Centro | la de `DEMO_PASSWORD` |
| `vendedor2` | Vendedor · Tienda Norte | la de `DEMO_PASSWORD` |
| `almacen1` | Almacenero · Almacén Central | la de `DEMO_PASSWORD` |

### Qué revisar de lo nuevo (segunda fase)
1. **Productos:** busca `per hex zin`; en un producto mira el precio sugerido, "Usar sugerido" y el historial de precios;
   selecciona varios productos y prueba las acciones en lote.
2. **Disponibilidad:** stock de las dos tiendas y del almacén en una sola pantalla.
3. **Encargos:** en **Ventas** (con la caja abierta) usa "Hacer encargo"; luego entrégalo desde **Encargos** y mira
   que recién ahí baja el stock.
4. **Cotizaciones:** crea una con 2 productos, guárdala, **Descargar PDF**, **Imprimir**, **Enviar por WhatsApp** y
   **Convertir en nota de venta** (necesita la caja abierta). Revisa que la venta tenga el mismo total.

---

## Paso 8. Apagar y volver a encender

**Apagar:** en cada ventana de CMD presiona `Ctrl + C` (y responde `S` si pregunta). Luego:

```
cd C:\ferreteria\ferreteria-backend\backend
docker compose down
```

Los datos **no** se borran con `docker compose down`.

**Encender otro día:** abre Docker Desktop y repite los pasos **4, 5 y 6** (sin `npm install`).

**Traer cambios nuevos de GitHub** (cuando Claude suba algo):

```
cd C:\ferreteria\ferreteria-backend
git pull origin catalogo-busqueda
cd C:\ferreteria\ferreteria-frontend
git pull origin catalogo-busqueda
npm install
```

---

## Respaldos

Cada día se crea solo un respaldo en `C:\ferreteria\ferreteria-backend\backend\respaldos`. Para hacer uno en el
momento, restaurar uno o guardarlos en una USB, sigue `docs/RESPALDOS.md`.

---

## Si algo falla

| Error | Qué hacer |
|---|---|
| `docker` da error o "cannot connect" | Abre Docker Desktop y espera a "Engine running" |
| `Falta DB_NAME en .env` | Estás en otra carpeta: ve a `C:\ferreteria\ferreteria-backend\backend` |
| `Port 8080 was already in use` | Otra ventana ya tiene el backend: ciérrala o usa `Ctrl + C` en ella |
| `Port 5434 is already allocated` | Otro PostgreSQL usa ese puerto: cambia `DB_PORT=5435` en `.env` y repite el paso 4 |
| El backend dice `password authentication failed` | Cambiaste `DB_PASSWORD` después de crear la base: ejecuta `docker compose down -v` (borra la base de prueba) y repite el paso 4 |
| No puedo entrar con `admin` | Revisa `ADMIN_PASSWORD` en `.env`; el usuario admin se crea con la clave que había la primera vez que arrancó |
| La página dice "Error de conexión" | El backend (paso 5) no está corriendo o todavía no dice `Started` |

## Completos (perno + tuerca + arandela)

En **Productos → (editar un producto) → Completos** el ADMIN arma los completos: el producto con otras piezas a un
precio propio. En una base nueva ya vienen de ejemplo: "Perno hexagonal 3/8 x 2" → Completo" (perno + tuerca +
arandela), "Con tuerca", y "Bisagra capuchina 3" → Con tornillos" (bisagra + 6 tornillos). En **Ventas** aparecen
como botones amarillos y al venderlos baja el stock de cada pieza.
