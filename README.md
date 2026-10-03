# Sistema de Gestion para Ferreteria

Backend de un sistema interno para gestionar 2 tiendas de ferreteria (cada una con su propio RUC) y 1 almacen compartido.
Controla el catalogo con presentaciones (unidad, ciento, kilo, metro, rollo...), el stock y su kardex, las compras a
proveedores, los traslados del almacen a cada tienda, la caja por vendedor, las ventas al contado y al fiado con pago
mixto y abonos, y los reportes del negocio.

## Stack

| Capa            | Tecnologia                                                         |
|-----------------|--------------------------------------------------------------------|
| Lenguaje        | Java 21 LTS                                                        |
| Framework       | Spring Boot 4.1 (Maven Wrapper)                                    |
| Modulos         | Spring Web, Spring Data JPA, Spring Security + JWT (resource server), Bean Validation, Lombok |
| Base de datos   | PostgreSQL 16 (Docker Compose en local)                            |
| Migraciones     | Flyway (V1 esquema, V2 catalogos, V3 secuencia de traslados)       |
| Documentacion   | springdoc OpenAPI (Swagger UI)                                     |
| Imagenes        | Cloudinary (opcional) o carpeta local                              |
| Pruebas         | JUnit 5 + Mockito + MockMvc, contra PostgreSQL real (131 pruebas)  |

## Estructura

```
backend/            Proyecto Spring Boot
  src/main/java/com/ferreteria/
    controller/     Endpoints REST (sin logica)
    service/        Reglas de negocio y transacciones (devuelven DTOs)
    repository/     Acceso a datos (Spring Data JPA)
    entity/         Entidades JPA y enums
    dto/            Records de entrada y salida por modulo
    security/       JWT, usuario autenticado y acceso por tienda
    exception/      Errores de negocio y manejador global
    config/         Seguridad, OpenAPI, imagenes, admin inicial y datos demo
docs/               Documentacion, esquema SQL, diagrama ER y coleccion de peticiones
```

## Como ejecutarlo

Guia completa para Windows + PowerShell + Docker: **[docs/COMO_EJECUTAR.md](docs/COMO_EJECUTAR.md)**. Resumen:

```powershell
cd backend
Copy-Item .env.example .env      # y completar JWT_SECRET, ADMIN_PASSWORD, DEMO_PASSWORD
docker compose up -d             # PostgreSQL 16 en el puerto 5434
.\mvnw.cmd verify                # pruebas (131)
.\mvnw.cmd spring-boot:run       # API en http://localhost:8080
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Usuarios de demostracion: `admin` / `Admin123!`, `vendedor1`, `vendedor2`, `almacen1` / `Demo123!`
  (las contrasenas salen de tu `.env`).
- Coleccion de peticiones: `docs/api/ferreteria.http` (VS Code REST Client) y
  `docs/api/ferreteria.postman_collection.json` (Postman).

## Modulos de la API

| Modulo | Ruta base | Roles |
|--------|-----------|-------|
| Autenticacion | `/api/auth` | Publico (login) / todos |
| Empresas, usuarios | `/api/empresas`, `/api/usuarios` | ADMIN |
| Ubicaciones | `/api/ubicaciones` | Consultar: todos; modificar: ADMIN |
| Catalogo | `/api/categorias`, `/api/productos` | Consultar: todos; modificar: ADMIN |
| Compras | `/api/proveedores`, `/api/compras` | ADMIN, ALMACENERO (anular: ADMIN) |
| Inventario | `/api/inventario` (stock, kardex, ajustes, inventario inicial) | Segun operacion |
| Traslados | `/api/traslados` | Enviar: ADMIN/ALMACENERO; recibir: la ubicacion destino |
| Clientes, caja, ventas, fiado | `/api/clientes`, `/api/cajas`, `/api/ventas`, `/api/fiado` | ADMIN, VENDEDOR |
| Reportes | `/api/reportes` | Segun reporte |

Quien no es ADMIN solo ve y opera los datos de su propia tienda o almacen.

## Documentacion

- [docs/COMO_EJECUTAR.md](docs/COMO_EJECUTAR.md): instalacion, usuarios y como probar cada modulo.
- [docs/EXPLICACION.md](docs/EXPLICACION.md): explicacion de cada modulo y de las decisiones importantes.
- [docs/DECISIONES.md](docs/DECISIONES.md): registro de decisiones de diseno.
- [docs/PENDIENTES.md](docs/PENDIENTES.md): lo que falta y las cuentas/claves por conseguir.
- [docs/PLAN.md](docs/PLAN.md): hoja de ruta.
- [CLAUDE.md](CLAUDE.md): estandares de calidad del codigo.

## Estado

Fase 1 completa (pasos 1 a 15): ventas como NOTA_VENTA, uso local.
Pendiente: paso 16 (despliegue) y Fase 2 (comprobantes electronicos SUNAT, notas de credito, envios a provincia).
