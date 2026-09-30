# Hoja de ruta del proyecto

Marca `[x]` cuando un paso este terminado, verificado y con su commit.

- [x] Paso 1. Estructura del proyecto y Git local
- [x] Paso 2. Crear repositorio en GitHub, conectarlo y subir (guiame para crearlo en la web)
- [x] Paso 3. Crear el proyecto Spring Boot 4.1.x con Java 21 y Maven Wrapper dentro de backend/
- [ ] Paso 4. Docker Compose con PostgreSQL 16, application.yml con variables de entorno, copiar docs/V1 y docs/V2 a src/main/resources/db/migration y verificar que Flyway cree las tablas
- [ ] Paso 5. Entidades JPA y repositorios (empieza por empresa, ubicacion, usuario)
- [ ] Paso 6. Manejo global de errores, DTOs y validaciones
- [ ] Paso 7. Seguridad: login, BCrypt, JWT, roles ADMIN/VENDEDOR/ALMACENERO y creacion del usuario admin al arrancar
- [ ] Paso 8. Catalogo: categorias, productos, presentaciones y foto con Cloudinary
- [ ] Paso 9. Proveedores y compras (entrada al stock y kardex, anulacion de compra)
- [ ] Paso 10. Inventario: stock por ubicacion, traslados (enviar y recibir), ajustes y kardex
- [ ] Paso 11. Clientes y caja (apertura y cierre con cuadre)
- [ ] Paso 12. Ventas: contado y credito, pago mixto, correlativos, descuento de stock con bloqueo pesimista, anulacion
- [ ] Paso 13. Fiado: abonos y reporte de deudores
- [ ] Paso 14. Reportes: ventas por tienda, traslados por tienda, compras por proveedor, stock bajo, productos mas vendidos
- [ ] Paso 15. Swagger completo y pruebas con JUnit 5 y Mockito
- [ ] Paso 16. Dockerfile, GitHub Actions y despliegue en Railway
