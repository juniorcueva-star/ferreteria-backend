# Sistema de Gestion para Ferreteria

Backend de un sistema interno para gestionar 2 tiendas de ferreteria (cada una con su propio RUC) y 1 almacen compartido.
Permite controlar el stock y los traslados del almacen a cada tienda, las ventas por tienda, las compras a proveedores,
la caja por vendedor y las ventas al fiado con pagos por partes.

## Stack

| Capa            | Tecnologia                                              |
|-----------------|---------------------------------------------------------|
| Lenguaje        | Java 21 LTS                                             |
| Framework       | Spring Boot 4.1.x (Maven Wrapper)                       |
| Modulos         | Spring Web, Spring Data JPA, Spring Security + JWT, Bean Validation, Lombok |
| Base de datos   | PostgreSQL 16 (Docker Compose en local, Railway en la nube) |
| Migraciones     | Flyway                                                  |
| Documentacion   | springdoc OpenAPI (Swagger UI)                          |
| Imagenes        | Cloudinary (solo se guarda la URL)                      |
| Pruebas         | JUnit (Jupiter) + Mockito                               |

## Estructura

```
backend/   Proyecto Spring Boot
docs/      Esquema SQL (Flyway), datos iniciales y diagrama ER
```

## Como ejecutarlo en local

Requisitos: Java 21 (con `JAVA_HOME` apuntando a el) y Docker Desktop.

1. Crear el archivo de variables de entorno a partir de la plantilla y poner una clave propia:

   ```powershell
   cd backend
   Copy-Item .env.example .env
   ```

2. Levantar PostgreSQL 16 (puerto 5434):

   ```powershell
   docker compose up -d
   ```

3. Arrancar el backend (Flyway crea las tablas automaticamente la primera vez):

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

La API queda en `http://localhost:8080`. Para detener la base de datos: `docker compose down`
(los datos se conservan; `docker compose down -v` los borra).

## Estado

En desarrollo - Fase 1 (ventas como NOTA_VENTA). Fase 2: comprobantes electronicos SUNAT y envios a provincia.
