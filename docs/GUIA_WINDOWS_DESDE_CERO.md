# Guía: instalar y levantar el sistema de la ferretería en una laptop con Windows (desde cero)

Esta guía es para una laptop **Windows 10 u 11** que nunca tuvo el proyecto. Empieza revisando qué herramientas
tiene la laptop, instala solo las que falten y termina con el sistema abierto en el navegador.

- Todos los comandos se escriben en **CMD** (la ventana negra que muestra algo como `C:\Users\TuNombre>`).
  Para abrirla: tecla **Windows**, escribe `cmd` y presiona **Enter**.
- Copia y pega **un comando a la vez** y espera a que termine antes de escribir el siguiente.
- Si un paso falla, **no sigas**: copia el mensaje de error completo y pide ayuda.
- Tiempo aproximado: 1 a 2 horas la primera vez (sobre todo por las descargas). Las siguientes veces son 2 minutos.

---

## Paso 0. Antes de empezar

| Qué | Por qué |
|---|---|
| Internet estable | Se descargan unos 3 a 4 GB entre herramientas y dependencias |
| 15 GB libres en el disco C: | Docker y las dependencias ocupan espacio |
| Ser administrador de la laptop | Para instalar programas (Windows pedirá "¿Permitir cambios?": responde **Sí**) |
| 8 GB de RAM o más | Docker, el backend y el navegador corren a la vez |

---

## Paso 1. Revisar qué herramientas ya tiene la laptop

Abre **CMD** y ejecuta estos comandos uno por uno. Anota qué responde cada uno:

```
winget --version
git --version
java -version
node -v
npm -v
docker --version
```

Compara con esta tabla:

| Comando | Respuesta correcta (o mayor) | Si sale error o una versión menor |
|---|---|---|
| `winget --version` | `v1.6` o mayor | Ver **1.1** |
| `git --version` | `git version 2.40` o mayor | Instalar en **2.1** |
| `java -version` | `openjdk version "21...` (debe empezar con **21**) | Instalar en **2.2** |
| `node -v` | `v22.12.0` o mayor (por ejemplo `v24.x`) | Instalar en **2.3** |
| `npm -v` | `10` o mayor (viene con Node) | Se arregla con **2.3** |
| `docker --version` | `Docker version 27` o mayor | Instalar en **2.4** |

> **Error "no se reconoce como un comando interno o externo"** significa que esa herramienta **no está instalada**.

> **Java:** si dice `1.8`, `11` o `17`, hay que instalar la **21** (paso 2.2). No sirve otra versión.

> **Maven no hace falta**: el backend trae su propio `mvnw.cmd`.

### 1.1 Si `winget` no existe
`winget` viene en Windows 11 y en Windows 10 actualizado. Si no lo tienes:
1. Abre la **Microsoft Store**, busca **"Instalador de aplicación"** (App Installer) y presiona **Actualizar** u **Obtener**.
2. Cierra CMD, ábrelo de nuevo y prueba `winget --version`.

Si aun así no funciona, en cada paso de abajo usa el **enlace de descarga** en lugar del comando `winget`.

---

## Paso 2. Instalar solo lo que falta

Después de **cada** instalación: **cierra CMD y ábrelo de nuevo** (si no, Windows no "ve" el programa nuevo) y vuelve
a revisar con el comando del paso 1.

### 2.1 Git
```
winget install --id Git.Git -e
```
Enlace (si winget no funciona): https://git-scm.com/download/win — instala con todas las opciones por defecto
(siguiente, siguiente…).

Revisar: `git --version`

### 2.2 Java 21 (Temurin)
```
winget install --id EclipseAdoptium.Temurin.21.JDK -e
```
Enlace: https://adoptium.net/temurin/releases/?version=21 — elige **Windows x64**, **JDK**, archivo **.msi**.
Durante la instalación, en la pantalla de opciones, activa **"Set JAVA_HOME variable"** y
**"Add to PATH"** (elige "Will be installed on local hard drive").

Revisar (en un CMD nuevo):
```
java -version
echo %JAVA_HOME%
```
- `java -version` debe empezar con `openjdk version "21`.
- Si sigue mostrando una versión vieja (8, 11 o 17), hay otra Java antes en el PATH: desinstala la vieja desde
  **Configuración → Aplicaciones**, o pide ayuda.
- Si `echo %JAVA_HOME%` muestra `%JAVA_HOME%` (vacío), no es grave: el backend usa la Java del PATH.

### 2.3 Node.js (trae npm)
```
winget install --id OpenJS.NodeJS.LTS -e
```
Enlace: https://nodejs.org — descarga la versión **LTS** (debe ser 22.12 o mayor) e instala con las opciones por
defecto. **No** marques la casilla de "herramientas para módulos nativos" (no hace falta).

Revisar (en un CMD nuevo): `node -v` y `npm -v`

> Si `npm -v` da un error rojo sobre **"ejecución de scripts deshabilitada"**, estás en PowerShell y no en CMD.
> Abre **CMD** (paso de arriba) y repite.

### 2.4 Docker Desktop (para la base de datos)
Docker necesita **WSL 2** (Windows Subsystem for Linux). Primero abre **CMD como administrador**: tecla
**Windows**, escribe `cmd`, clic derecho en "Símbolo del sistema" → **Ejecutar como administrador**. Luego:

```
wsl --install
```
- Si dice que ya está instalado, sigue.
- Si instaló algo, **reinicia la laptop**.

Después instala Docker (en CMD normal):
```
winget install --id Docker.DockerDesktop -e
```
Enlace: https://www.docker.com/products/docker-desktop/ — botón **Download for Windows**.

Al terminar:
1. **Reinicia la laptop** si lo pide.
2. Abre **Docker Desktop** desde el menú Inicio. Acepta los términos; puedes **saltar** ("Skip") el inicio de
   sesión y la encuesta.
3. Espera a que abajo a la izquierda diga **Engine running** (en verde).

Revisar:
```
docker --version
docker ps
```
`docker ps` debe mostrar una tabla con títulos (`CONTAINER ID  IMAGE ...`), aunque esté vacía.

| Problema con Docker | Qué hacer |
|---|---|
| "Virtualization support not detected" o "WSL 2 installation is incomplete" | Hay que activar la **virtualización** en la BIOS (se llama Intel VT-x, AMD-V o SVM). Busca en Google "activar virtualización BIOS" + la marca de la laptop, o pide ayuda |
| `docker ps` dice "cannot connect" o "pipe" | Docker Desktop no está abierto o aún no dice **Engine running**: espera un minuto |

### 2.5 Revisión final
En un **CMD nuevo**, todos estos deben responder sin error:
```
git --version
java -version
node -v
npm -v
docker ps
```
Si los cinco responden bien, ya tienes todo. ✅

---

## Paso 3. Descargar los proyectos

Se usa la carpeta `C:\ferreteria` y **no el Escritorio**, porque el Escritorio suele sincronizarse con OneDrive y
eso vuelve muy lento el frontend.

```
mkdir C:\ferreteria
cd C:\ferreteria
git clone -b catalogo-busqueda https://github.com/juniorcueva-star/ferreteria-backend.git
git clone -b catalogo-busqueda https://github.com/juniorcueva-star/ferreteria-frontend.git
dir
```

Los repositorios son públicos: no piden usuario ni contraseña. `dir` debe mostrar dos carpetas:

```
C:\ferreteria\
   ├── ferreteria-backend\    (Java / Spring Boot: el servidor)
   └── ferreteria-frontend\   (React: las pantallas)
```

> La rama **catalogo-busqueda** es la que tiene todo lo último. No uses `main`: todavía no tiene los cambios.

---

## Paso 4. Configurar el backend (archivo `.env`)

```
cd C:\ferreteria\ferreteria-backend\backend
copy .env.example .env
notepad .env
```

Se abre el Bloc de notas. **Cambia solo estas 4 líneas** (escribe después del `=`, sin espacios ni comillas) y
guarda con **Ctrl + S**:

| Línea | Qué poner | Ejemplo de formato (inventa el tuyo) |
|---|---|---|
| `DB_PASSWORD=` | una clave para la base de datos | `Ferre2026base` |
| `JWT_SECRET=` | una frase **de 32 caracteres o más**, sin espacios | `estaEsMiFraseSecretaLargaDeLaFerreteria2026` |
| `ADMIN_PASSWORD=` | la contraseña con la que entrará el usuario `admin` | `MiClaveAdmin1` |
| `DEMO_PASSWORD=` | la contraseña de `vendedor1`, `vendedor2` y `almacen1` | `MiClaveDemo1` |

- Todo lo demás se deja **igual**.
- **Anota tus contraseñas**: las usarás para entrar.
- El archivo `.env` es solo de tu laptop: **nunca** se sube a GitHub ni se comparte.

### 4.1 (Opcional) Activar el asistente de IA
El sistema trae un **asistente de IA** (botón amarillo **Asistente**, abajo a la derecha). Funciona sin esto, pero
para que responda necesita una **clave de Claude**. Si no la tienes todavía, salta este paso: el resto del sistema
funciona igual y el asistente solo avisará que no está activado.

Si ya tienes la clave (se consigue siguiendo `docs/GUIA_CLAVE_IA.md`), en el mismo `.env` busca la línea
`ANTHROPIC_API_KEY=` y pega tu clave después del `=`, sin espacios ni comillas:
```
ANTHROPIC_API_KEY=sk-ant-...tu clave completa...
```
> La clave es como la contraseña de una tarjeta: **no la mandes por WhatsApp ni la subas a GitHub**. Cada persona
> usa la suya, o el dueño la pone solo en la laptop o servidor del negocio.

Revisa que se guardó con el nombre correcto:
```
dir .env
```
Debe aparecer `.env` (y no `.env.txt`). Si aparece `.env.txt`, renómbralo: `ren .env.txt .env`

---

## Paso 5. Levantar la base de datos

Docker Desktop debe estar abierto (**Engine running**). En la misma ventana (carpeta `backend`):

```
docker compose up -d
docker compose ps
```

- La primera vez descarga PostgreSQL (1 o 2 minutos).
- Deben aparecer `ferreteria-postgres` (estado **running** o **healthy**) y `ferreteria-respaldo` (**running**).
  El segundo hace un **respaldo automático diario** de la base en `backend\respaldos`.

---

## Paso 6. Levantar el backend

En la misma ventana:

```
mvnw.cmd spring-boot:run
```

- La primera vez tarda **5 a 10 minutos** (descarga las librerías). Las siguientes, menos de 1 minuto.
- Está listo cuando aparece una línea con **`Started FerreteriaBackendApplication`**.
- Si Windows pregunta por el **Firewall**, presiona **Permitir**.
- **No cierres esta ventana**: si la cierras, se apaga el backend.

Comprueba en el navegador: http://localhost:8080/swagger-ui/index.html (debe abrir la documentación de la API).

---

## Paso 7. Levantar el frontend

Abre **otra ventana de CMD** (la del backend sigue abierta):

```
cd C:\ferreteria\ferreteria-frontend
npm install
npm run dev
```

- `npm install` tarda unos minutos **solo la primera vez**. Si muestra avisos amarillos (`warn`), es normal.
- Está listo cuando aparece `Local: http://localhost:5173/`.
- **No cierres esta ventana** tampoco.

---

## Paso 8. Entrar al sistema

Abre en Chrome o Edge: **http://localhost:5173**

| Usuario | Rol | Contraseña |
|---|---|---|
| `admin` | Administrador (ve todo) | la que pusiste en `ADMIN_PASSWORD` |
| `vendedor1` | Vendedor · Tienda Centro | la de `DEMO_PASSWORD` |
| `vendedor2` | Vendedor · Tienda Norte | la de `DEMO_PASSWORD` |
| `almacen1` | Almacenero · Almacén Central | la de `DEMO_PASSWORD` |

La base nueva ya trae productos, tiendas y clientes de ejemplo para probar.

### Qué probar de lo último
1. **Caja con fotos** (`vendedor1`): abre la caja en **Caja**, ve a **Ventas**, presiona **Fotos** y toca un
   producto para agregarlo. Prueba los botones de categoría y el botón amarillo **Ofertas**.
2. **Precio por mayor** (`admin`): en **Productos → editar → Precio por mayor**, agrega "desde 100 a S/ 0.40". En la
   caja pon 100 o más y mira cómo baja el precio solo (etiqueta **POR MAYOR**).
3. **Ofertas** (`admin`): en **Productos → editar → Oferta**, pon un precio menor y las fechas. En la caja aparece
   la etiqueta **OFERTA** y el precio de lista tachado.
4. **Lista de precios**: en **Productos**, botón **Lista de precios** (o el de descarga para mandarla por WhatsApp).
5. **Etiquetas** (`admin` o `almacen1`): en **Productos** marca varios productos y presiona **Imprimir etiquetas**.
6. **Asistente de IA** (cualquier usuario): presiona el botón amarillo **Asistente** con la cara del perno.
   - Sin clave: dice "El asistente todavía no está activado" (es normal).
   - Con clave: arriba dice "Te quedan 50 de 50 preguntas hoy". Prueba las preguntas sugeridas, por ejemplo con
     `vendedor1` "¿Cuánto cuestan 100 pernos 3/8?" o con `admin` "¿Cómo me fue hoy?".

---

## Paso 9. Apagar y volver a encender

**Apagar:** en cada ventana de CMD presiona **Ctrl + C** (responde `S` si pregunta). Luego:
```
cd C:\ferreteria\ferreteria-backend\backend
docker compose down
```
Los datos **no** se borran con `docker compose down`.

**Encender otro día:**
1. Abre **Docker Desktop** y espera **Engine running**.
2. CMD 1:
   ```
   cd C:\ferreteria\ferreteria-backend\backend
   docker compose up -d
   mvnw.cmd spring-boot:run
   ```
3. CMD 2:
   ```
   cd C:\ferreteria\ferreteria-frontend
   npm run dev
   ```
4. Abre http://localhost:5173

**Traer los cambios nuevos de GitHub** (cuando se suba algo nuevo), con el sistema apagado:
```
cd C:\ferreteria\ferreteria-backend
git pull origin catalogo-busqueda
cd C:\ferreteria\ferreteria-frontend
git pull origin catalogo-busqueda
npm install
```
Luego enciende como arriba. Los cambios en la base de datos se aplican solos al arrancar el backend.

---

## Si algo falla

| Error | Qué hacer |
|---|---|
| `"xxx" no se reconoce como un comando interno o externo` | Esa herramienta no está instalada, o falta cerrar y abrir CMD después de instalarla (paso 2) |
| `docker` da error, "cannot connect" o "pipe" | Abre Docker Desktop y espera **Engine running** |
| `JAVA_HOME is not defined correctly` | Reinstala Java 21 con la opción **Set JAVA_HOME variable** (paso 2.2) y abre un CMD nuevo |
| `release version 21 not supported` o `invalid target release: 21` | La Java que se usa no es la 21: revisa `java -version` (paso 2.2) |
| `Falta DB_NAME en .env` o el backend no encuentra la base | Estás en otra carpeta, o el archivo quedó como `.env.txt` (paso 4) |
| `Port 8080 was already in use` | Ya hay un backend abierto en otra ventana: ciérralo con **Ctrl + C** |
| `Port 5434 is already allocated` | Otro programa usa ese puerto: cambia `DB_PORT=5435` en `.env` y repite el paso 5 |
| `password authentication failed` | Cambiaste `DB_PASSWORD` después de crear la base: ejecuta `docker compose down -v` (borra la base de prueba) y repite el paso 5 |
| No puedo entrar con `admin` | Revisa `ADMIN_PASSWORD` en `.env`. El admin se crea con la clave que había **la primera vez** que arrancó el backend; si la cambiaste después, usa `docker compose down -v` y vuelve a empezar desde el paso 5 |
| La página dice "Error de conexión" | El backend (paso 6) no está corriendo o todavía no dice `Started` |
| `npm install` falla con `EBADENGINE` o pide otra versión de Node | Tu Node es menor que 22.12: instala la LTS (paso 2.3) |
| El asistente dice "no está activado" | Falta la clave en `ANTHROPIC_API_KEY` (paso 4.1) o no reiniciaste el backend |
| El asistente dice "La clave de la IA no es válida" | La clave está mal copiada o vencida: crea otra (ver `docs/GUIA_CLAVE_IA.md`) |
| El frontend está muy lento | El proyecto quedó dentro de OneDrive (Escritorio o Documentos): bórralo y descárgalo en `C:\ferreteria` (paso 3) |

Más detalles técnicos: `docs/COMO_EJECUTAR.md`. Respaldos: `docs/RESPALDOS.md`. Clave de la IA:
`docs/GUIA_CLAVE_IA.md`.
