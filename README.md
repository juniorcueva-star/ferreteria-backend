# Sistema de Gestion para Ferreteria

Backend de un sistema interno para gestionar 2 tiendas de ferreteria (cada una con su propio RUC) y 1 almacen compartido.
Permite controlar el stock y los traslados del almacen a cada tienda, las ventas por tienda, las compras a proveedores,
la caja por vendedor y las ventas al fiado con pagos por partes.

## Stack

| Capa            | Tecnologia                                              |
|-----------------|---------------------------------------------------------|
| Lenguaje        | Java 21 LTS                                             |
| Framework       | Spring Boot 4.0.x (Maven Wrapper)                       |
| Modulos         | Spring Web, Spring Data JPA, Spring Security + JWT, Bean Validation, Lombok |
| Base de datos   | PostgreSQL 16 (Docker Compose en local, Railway en la nube) |
| Migraciones     | Flyway                                                  |
| Documentacion   | springdoc OpenAPI (Swagger UI)                          |
| Imagenes        | Cloudinary (solo se guarda la URL)                      |
| Pruebas         | JUnit 5 + Mockito                                       |

## Estructura

```
backend/   Proyecto Spring Boot
docs/      Esquema SQL (Flyway), datos iniciales y diagrama ER
```

## Estado

En desarrollo - Fase 1 (ventas como NOTA_VENTA). Fase 2: comprobantes electronicos SUNAT y envios a provincia.
