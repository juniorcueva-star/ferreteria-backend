# CLAUDE.md

Guia para trabajar en este repositorio (personas y asistentes de IA).

## Proyecto

Backend de un sistema interno para una ferreteria con 2 tiendas (cada una con su RUC) y 1 almacen
compartido. Stack: Java 21, Spring Boot 4.1 (Maven Wrapper), Spring Data JPA, Spring Security + JWT,
PostgreSQL 16, Flyway, springdoc OpenAPI. El codigo esta en `backend/`, la documentacion en `docs/`.

- Hoja de ruta: `docs/PLAN.md` (marcar cada paso terminado).
- Decisiones tomadas: `docs/DECISIONES.md`.
- Pendientes y bloqueos: `docs/PENDIENTES.md`.

## Comandos

Todos se ejecutan dentro de `backend/`:

```bash
./mvnw verify                          # compila y corre TODAS las pruebas (obligatorio antes de cada commit)
./mvnw spring-boot:run                 # levanta la API en http://localhost:8080 (perfil dev por defecto)
docker compose up -d                   # PostgreSQL 16 local en el puerto 5434
```

Las pruebas usan PostgreSQL real (no H2), en el esquema `pruebas` de la misma base configurada en `.env`.

## Organizacion del codigo (`backend/src/main/java/com/ferreteria`)

| Paquete       | Contenido                                                                 |
|---------------|---------------------------------------------------------------------------|
| `controller`  | Endpoints REST. Solo reciben, validan (`@Valid`) y delegan al service      |
| `service`     | Logica de negocio y transacciones. Reciben y devuelven DTOs               |
| `repository`  | Interfaces Spring Data JPA. Solo acceso a datos                           |
| `entity`      | Entidades JPA (una por tabla) y `entity.enums`                            |
| `dto`         | Records de entrada (`...Request`) y salida (`...Response`) por modulo     |
| `security`    | JWT, usuario autenticado, acceso por tienda                               |
| `exception`   | Excepciones de negocio y manejador global de errores                      |
| `config`      | Configuracion (seguridad, OpenAPI, imagenes, datos de demostracion)       |
| `util`        | Utilidades sin estado (`Montos`: redondeos e IGV)                         |

## Reglas del dominio que no se deben romper

- El stock se guarda siempre en la unidad base del producto (`UNIDAD`, `KILO`, `METRO`).
- El stock nunca es negativo (CHECK en BD + bloqueo pesimista en Java).
- Todo movimiento de stock deja una fila en `movimiento_inventario` (kardex).
- Dinero `NUMERIC(12,2)`, cantidades `NUMERIC(14,3)`, redondeo `HALF_UP`.
- Los precios de venta incluyen IGV (18 %).
- Fechas `TIMESTAMPTZ` -> `OffsetDateTime`; los reportes por dia usan la zona `America/Lima`.

## ESTANDARES DE CALIDAD

Reglas obligatorias para todo el codigo del proyecto:

1. **Arquitectura por capas estricta.**
   - Los controllers no tienen logica de negocio: reciben el request, lo validan con `@Valid`
     y llaman a un service.
   - Los services no devuelven entidades: siempre devuelven DTOs (`...Response`, `PaginaResponse`).
   - Los repositories solo acceden a datos (consultas), sin reglas de negocio.
2. **Operaciones de stock y dinero transaccionales.** Toda operacion que mueve stock o dinero usa
   `@Transactional` y deja registro en el kardex (`movimiento_inventario`). El unico punto que
   modifica stock es `MovimientoStockService`, que exige una transaccion activa.
3. **Validacion y errores uniformes.** La entrada se valida con Bean Validation. Todos los errores
   salen del manejador global con el mismo formato: `codigo`, `mensaje`, `detalle`, `fecha`
   (incluidos 401 y 403 del filtro de seguridad).
4. **Seguridad por rol y por tienda.** Los roles permitidos de cada endpoint estan en una sola tabla
   (`SecurityConfig.permisosPorRol`), que se evalua antes de leer el cuerpo; una ruta no listada queda
   cerrada. Un usuario que no es ADMIN solo puede ver y operar datos de su propia ubicacion; esto se
   valida en los services (`AccesoUbicacionService`) y se prueba en `SeguridadIntegrationTest`.
5. **Listados con paginacion y filtros.** Ningun endpoint devuelve una lista completa sin limite:
   se usa `Pageable` (tamano maximo 100) y se responde con `PaginaResponse`.
6. **Nombres consistentes.** Conceptos del dominio en espanol (`Venta`, `registrar`, `anular`) y
   sufijos tecnicos en ingles (`Controller`, `Service`, `Repository`, `Request`, `Response`).
   Metodos de service en espanol e infinitivo: `crear`, `actualizar`, `obtener`, `listar`,
   `registrar`, `anular`, `desactivar`.
7. **Codigo limpio.** Sin codigo muerto, sin TODOs sueltos (lo pendiente va a `docs/PENDIENTES.md`)
   y sin credenciales en el codigo: claves, contrasenas y cuentas externas se leen de variables
   de entorno (`.env`, que nunca se sube a Git).
8. **Migraciones.** Nunca editar `V1` ni `V2`. Todo cambio de esquema va en una migracion nueva
   (`V3`, `V4`, ...) explicada en `docs/DECISIONES.md`.
9. **Pruebas.** Antes de cada commit se ejecuta `./mvnw verify` y todo debe pasar. No se desactivan
   pruebas para que el build pase.
