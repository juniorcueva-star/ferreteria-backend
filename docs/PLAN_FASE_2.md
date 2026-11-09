# Plan de la segunda fase

Mejoras pedidas por el encargado de la ferreteria (senor Romel). Se trabaja en la rama `catalogo-busqueda` de los
dos repositorios (`ferreteria-backend` y `ferreteria-frontend`). Cada etapa termina con pruebas en verde, commits
subidos a `origin/catalogo-busqueda` y su checkpoint.

## Requerimientos

1. **Stock visible por tienda:** el vendedor ve el stock de las dos tiendas y del almacen en una sola consulta (solo lectura).
2. **Cotizaciones:** crear, descargar en PDF, imprimir, enviar por correo y por WhatsApp, y convertir en nota de venta.
3. **Encargos:** producto sin stock que se consigue fuera y se vende con recargo; no toca el stock hasta entregarse.
4. **Presentaciones:** precio propio por presentacion, precio sugerido (costo + ganancia + adicional) e historial de precios.
5. **Busqueda y tamanos:** busqueda por palabras y abreviaturas (hecha en el backend en V8); falta el frontend.

## Etapas

| Etapa | Repositorio | Contenido | Criterio de terminado | Estado |
|-------|-------------|-----------|-----------------------|--------|
| E0 | ambos | Preparacion: ramas, pruebas base, este plan | `./mvnw verify` (137) y lint/build/test del frontend (53) en verde | Terminada |
| B1 | backend | Decimales por presentacion en ventas y compras; V9 categorias de ferreteria y decimales por unidad de medida; acciones en lote | Pruebas de decimales y de acciones en lote en verde | Terminada |
| F1 | frontend | Formulario de producto (tipo, acabado, medida, marca VARIADO), unidad de medida en presentaciones, buscador por palabras, acciones en lote | Vitest + Playwright ("per hex zin" y accion en lote) | Terminada |
| B2 | backend | V10 costo de referencia, ganancia, adicional, precio sugerido e historial de precios | Formula HALF_UP, historial y costo tras compra probados | Pendiente |
| F2 | frontend | Precios por presentacion: sugerido, usar sugerido, historial | Vitest + Playwright | Pendiente |
| B3 | backend | `GET /api/inventario/resumen`: stock por ubicacion y total en una sola consulta | Lectura de otras tiendas permitida; escritura sigue bloqueada | Pendiente |
| F3 | frontend | Pantalla "Disponibilidad" y resumen en el punto de venta | Vitest + Playwright | Pendiente |
| B4 | backend | V11 encargos: crear, listar, entregar (crea la venta) y anular | Stock solo cambia al entregar | Pendiente |
| F4 | frontend | "Hacer encargo" en el punto de venta y pantalla de encargos | Playwright: crear, entregar y ver el stock | Pendiente |
| B5 | backend | V12 cotizaciones: CRUD, convertir en venta, PDF, correo y WhatsApp | PDF valido, total igual al de la venta, correo 422 sin configuracion | Pendiente |
| F5 | frontend | Pantallas de cotizaciones con todas sus acciones | Playwright: crear, descargar y convertir | Pendiente |
| B6 | backend | Cierre: documentacion, coleccion de peticiones y prueba completa | `./mvnw verify` en verde | Pendiente |
| F6 | frontend | Cierre: lint, build, test y e2e contra el backend local; README | Todo en verde | Pendiente |

## Ajustes acordados al prompt (detalle en DECISIONES.md)

- Los archivos `docker-compose.test.yml`, `docker-compose.integration.yml` y `docs/PRUEBAS_AISLADAS.md` no existen:
  las pruebas usan el esquema `pruebas` de la base configurada en `.env`.
- `origin/mejoras` solo trae V4 a V6 (no hay V7 de combinaciones). De su V4 solo se reutiliza el historial de precios.
- La validacion de decimales en cotizaciones se prueba en B5, cuando existan las cotizaciones.
- `unidad_medida` recibe una columna `decimales` (V9) para que el formulario muestre cuantos decimales acepta cada unidad.
- Encargos y cotizaciones convertidas crean la venta con el precio del documento (no el de lista) y reciben
  condicion, pagos y cliente como una venta normal, en la caja abierta de quien entrega o convierte.
- La demo se hara en la nube: el mensaje de WhatsApp incluye un enlace publico de descarga del PDF, firmado con un
  token imposible de adivinar, y la URL base se configura con `APP_URL_PUBLICA`.
