# Pendientes

Lo que falta en el sistema y lo que el dueno del proyecto debe conseguir.

## Bloqueos durante el desarrollo

Ninguno. Todos los pasos del 6 al 15 se completaron y probaron. El paso 16 (despliegue) no se hizo por indicacion:
por ahora el sistema solo se usa en la laptop.

## Lo que debes conseguir tu (cuentas y claves)

| Que | Para que | Cuando | Donde se configura |
|-----|----------|--------|--------------------|
| Una clave propia para `JWT_SECRET` (minimo 32 caracteres) | Firmar los tokens | Ya (en tu `.env`) | `backend/.env` |
| Contrasenas propias para `ADMIN_PASSWORD` y `DEMO_PASSWORD` | No usar las de ejemplo | Ya | `backend/.env` |
| Cuenta de **Cloudinary** (gratis): `CLOUDINARY_URL` = `cloudinary://API_KEY:API_SECRET@CLOUD_NAME` | Guardar las fotos en la nube | Opcional; sin ella las fotos se guardan en `backend/imagenes` | `backend/.env` |
| Cuenta de **Railway** y repositorio en GitHub con Actions | Despliegue en la nube (paso 16) | Cuando se quiera publicar | Variables de Railway |
| **Clave SOL** de SUNAT y **certificado digital** de cada RUC, y un **OSE/PSE** (proveedor de facturacion electronica) | Boletas y facturas electronicas (Fase 2) | Fase 2 | Nuevas variables de entorno |

## Pendientes de la segunda fase

- Para enviar cotizaciones por correo hay que configurar `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` y
  `MAIL_FROM` (con Gmail, una "contrasena de aplicacion"). Sin ellas el envio responde 422.
- El enlace de WhatsApp usa `APP_URL_PUBLICA` (URL publica del backend) para el PDF.

## Despliegue en la nube (guia corta)

| Parte | Herramienta | Configuracion |
|-------|-------------|---------------|
| Base de datos | PostgreSQL de Railway (o Neon) | Flyway crea las tablas al arrancar |
| Backend | Railway | Repositorio `ferreteria-backend`, *Root Directory* `backend`, Java 21 (Maven) |
| Frontend | Vercel (o Netlify) | `npm run build`, carpeta `dist`, variable `VITE_API_URL` con la URL del backend |

Variables del backend: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` y `DB_PASSWORD` (los de la base de Railway), `JWT_SECRET` (32+ caracteres),
`ADMIN_PASSWORD`, `DEMO_PASSWORD`, `CORS_ORIGENES` (URL del frontend), `APP_URL_PUBLICA` (URL del backend),
`SPRING_PROFILES_ACTIVE` (`dev` carga datos de demostracion; `prod` para uso real), y opcionales `MAIL_*` y
`CLOUDINARY_URL` (sin Cloudinary las fotos se pierden al reiniciar el servidor).

## Funcionalidades pendientes

### Paso 16 (no realizado por ahora)
- `Dockerfile` del backend, workflow de GitHub Actions (compilar y correr pruebas en cada push) y despliegue en Railway.
- En produccion: usar `SPRING_PROFILES_ACTIVE=prod` (sin datos de demostracion), desactivar Swagger
  (`springdoc.api-docs.enabled=false`), servir solo por HTTPS y definir `CORS_ORIGENES` con el dominio del frontend.

### Fase 2 (fuera del alcance de la Fase 1)
- Boletas y facturas electronicas (envio a SUNAT, columna `estado_sunat` ya existe en `venta`).
- **Notas de credito / devoluciones** de ventas cuya caja ya se cerro (hoy esas ventas no se pueden anular si se cobro
  dinero en una caja cerrada; ver D55).
- Envios a provincia.

### Mejoras sugeridas
- **Limite de intentos de login** (bloqueo temporal tras varios fallos) para frenar ataques de fuerza bruta.
- **Cierre de sesion / revocacion de tokens** antes de su vencimiento (hoy un token dura 8 horas; desactivar al
  usuario si corta el acceso al instante).
- **Recepcion parcial de traslados** (hoy se recibe completo y la diferencia se registra con un ajuste).
- Administracion de **varias series** por tienda (hoy cada tienda usa una serie NV que se crea sola).
- Frontend (web): se desarrolla en el repositorio `ferreteria-frontend`.
- Si se pide una respuesta en un formato distinto de JSON (cabecera `Accept: text/plain`), un error se responde con
  500 sin cuerpo; los clientes normales (navegador, Postman, frontend) no se ven afectados.
