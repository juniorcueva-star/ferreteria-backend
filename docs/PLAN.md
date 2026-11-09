# Hoja de ruta del proyecto

Marca `[x]` cuando un paso este terminado, verificado y con su commit.

- [x] Paso 1. Estructura del proyecto y Git local
- [x] Paso 2. Crear repositorio en GitHub, conectarlo y subir (guiame para crearlo en la web)
- [x] Paso 3. Crear el proyecto Spring Boot 4.1.x con Java 21 y Maven Wrapper dentro de backend/
- [x] Paso 4. Docker Compose con PostgreSQL 16, application.yml con variables de entorno, copiar docs/V1 y docs/V2 a src/main/resources/db/migration y verificar que Flyway cree las tablas
- [x] Paso 5. Entidades JPA y repositorios (empieza por empresa, ubicacion, usuario)
  - [x] 5.1 Organizacion: empresa, ubicacion, usuario
  - [x] 5.2 Catalogo e inventario: categoria, producto, presentacion, stock, traslado, traslado_detalle, movimiento_inventario
  - [x] 5.3 Operaciones: proveedor, compra, compra_detalle, cliente, caja_sesion, serie_correlativo, venta, venta_detalle, metodo_pago, pago
- [x] Paso 6. Manejo global de errores, DTOs y validaciones
- [x] Paso 7. Seguridad: login, BCrypt, JWT, roles ADMIN/VENDEDOR/ALMACENERO y creacion del usuario admin al arrancar
- [x] Paso 8. Catalogo: categorias, productos, presentaciones y foto con Cloudinary
- [x] Paso 9. Proveedores y compras (entrada al stock y kardex, anulacion de compra)
- [x] Paso 10. Inventario: stock por ubicacion, traslados (enviar y recibir), ajustes y kardex
- [x] Paso 11. Clientes y caja (apertura y cierre con cuadre)
- [x] Paso 12. Ventas: contado y credito, pago mixto, correlativos, descuento de stock con bloqueo pesimista, anulacion
- [x] Paso 13. Fiado: abonos y reporte de deudores
- [x] Paso 14. Reportes: ventas por tienda, traslados por tienda, compras por proveedor, stock bajo, productos mas vendidos
- [x] Paso 15. Swagger completo y pruebas con JUnit 5 y Mockito
- [ ] Paso 16. Dockerfile, GitHub Actions y despliegue en Railway (pospuesto: por ahora el sistema se usa solo en local, ver docs/PENDIENTES.md)

## Segunda fase (rama `catalogo-busqueda`, detalle en `docs/PLAN_FASE_2.md`)

- [x] E0. Preparacion: ramas, pruebas base y plan
- [x] B1 / F1. Decimales por presentacion, categorias de ferreteria (V9), tipo/acabado/medida, busqueda por palabras y acciones en lote
- [x] B2 / F2. Costo de referencia, precio sugerido (costo + ganancia + adicional) e historial de precios (V10)
- [x] B3 / F3. Disponibilidad: stock de las dos tiendas y del almacen en una sola consulta (solo lectura)
- [x] B4 / F4. Encargos (V11): productos sin stock con precio propio; el stock cambia solo al entregarlos
- [x] B5 / F5. Cotizaciones (V12): PDF, imprimir, correo, WhatsApp y conversion en nota de venta
- [x] B6 / F6. Cierre: documentacion, coleccion de peticiones (48), `./mvnw verify` y e2e del frontend (25) en verde
- [x] Pedido del dueno: tema amarillo, negro y blanco, y lupa para agrandar la letra (frontend)
