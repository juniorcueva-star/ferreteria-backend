# Explicacion del proyecto

Explicacion sencilla de como funciona el backend, modulo por modulo, y de las decisiones importantes.
Sirve para defender el proyecto: cada seccion termina con las preguntas que te pueden hacer y como responderlas.

---

## 1. El problema que resuelve

Una ferreteria tiene **2 tiendas**, cada una con **su propio RUC** (dos empresas del mismo dueno), y **1 almacen
compartido**. El sistema controla:

- el **catalogo** de productos, que se venden de varias formas (unidad, ciento, kilo, medio kilo, metro, rollo...),
- el **stock** en cada ubicacion y su historial (**kardex**),
- las **compras** a proveedores (la mercaderia entra al almacen),
- los **traslados** del almacen a cada tienda,
- la **caja** de cada vendedor (apertura y cierre con cuadre),
- las **ventas** al contado y al **fiado** (credito), con pago mixto (efectivo + Yape, por ejemplo) y abonos,
- **reportes** para el dueno.

---

## 2. Arquitectura por capas

```
Cliente (Swagger, Postman, futuro frontend)
        |  HTTP + JSON + token JWT
        v
[ Filtro de seguridad ]  valida el token y el ROL (tabla de permisos)
        v
[ Controller ]  recibe el request, valida el formato (@Valid) y llama al service. Sin logica.
        v
[ Service ]     reglas del negocio y transacciones. Recibe y devuelve DTOs, nunca entidades.
        v
[ Repository ]  solo consultas a la base de datos (Spring Data JPA)
        v
[ PostgreSQL 16 ]  tablas creadas por Flyway, con CHECK, UNIQUE e indices como ultima barrera
```

- **Entidades** (`entity`): una clase por tabla, mapeada con JPA.
- **DTOs** (`dto`): records de Java. `...Request` = lo que entra; `...Response` = lo que sale. Asi nunca se expone
  algo interno (por ejemplo, el hash de la contrasena) y el JSON no cambia si cambia la tabla.
- **Manejador global de errores**: todos los errores salen con el mismo formato
  `{ "codigo", "mensaje", "detalle", "fecha" }`.

**Preguntas tipicas**
- *¿Por que el controller no tiene logica?* Para que la regla de negocio este en un solo lugar (el service) y se pueda
  probar sin HTTP (pruebas unitarias con Mockito).
- *¿Por que DTOs y no devolver la entidad?* Seguridad (no filtrar campos), evitar errores de carga perezosa (LAZY) y
  desacoplar el JSON de la base de datos.

---

## 3. Base de datos y migraciones

- El esquema lo crea **Flyway** con scripts versionados: `V1` (tablas), `V2` (metodos de pago y categorias) y `V3`
  (secuencia para el codigo de traslado). **Nunca se editan** migraciones ya aplicadas; un cambio es una migracion nueva.
- Hibernate solo **valida** que las entidades coincidan con las tablas (`ddl-auto: validate`); nunca crea tablas.
- **Dinero** `NUMERIC(12,2)`, **cantidades** `NUMERIC(14,3)` (permite 2.5 kilos), redondeo `HALF_UP` igual que
  PostgreSQL. En Java se usa `BigDecimal` (nunca `double`, que tiene errores de redondeo).
- **Fechas** con zona horaria (`TIMESTAMPTZ`). "Un dia" en los reportes es un dia de Lima, aunque el servidor este en UTC.
- La BD tiene reglas propias (CHECK): stock nunca negativo, total = subtotal + IGV, un fiado siempre tiene cliente,
  solo una caja abierta por usuario, solo una presentacion principal por producto. Son la **ultima barrera**: el
  service valida antes y da un mensaje claro, pero si algo se escapa la BD lo rechaza.

**Pregunta tipica:** *¿Por que las pruebas usan PostgreSQL real y no H2?* Porque el esquema usa CHECK con expresiones
regulares e indices parciales (`WHERE es_principal`) que H2 no soporta igual. Probar contra otra base podria dar
pruebas verdes que fallan en produccion.

---

## 4. Seguridad

### Autenticacion (quien eres)
1. `POST /api/auth/login` con usuario y contrasena.
2. La contrasena se compara con su hash **BCrypt** (nunca se guarda en texto plano).
3. Se devuelve un **token JWT** firmado (HS256) que dura 8 horas (un turno).
4. En cada peticion se envia `Authorization: Bearer <token>`. Spring Security valida la firma y el vencimiento.
5. Con el id del token se lee el usuario de la BD: si lo desactivaron, pierde el acceso al instante.

### Autorizacion por rol (que puedes hacer)
Una sola **tabla de permisos** en `SecurityConfig`:

| Rol | Puede |
|-----|-------|
| ADMIN | Todo: usuarios, empresas, catalogo, precios, anulaciones, reportes de todas las tiendas |
| VENDEDOR | Caja, ventas, fiado y clientes **de su tienda**; consultar catalogo y su stock |
| ALMACENERO | Compras, proveedores, traslados y ajustes **de su almacen** |

Si el rol no tiene permiso: **403**. Sin token: **401**. Una ruta que no esta en la tabla queda cerrada.

### Autorizacion por tienda (de quien son los datos)
Un vendedor de la Tienda Centro **no puede ver ni operar** datos de la Tienda Norte. Esto lo valida
`AccesoUbicacionService` en cada service:
- si pide datos de otra tienda (`?ubicacionId=3`) -> 403;
- si no indica tienda, se usa la suya;
- si pide una venta, caja o traslado de otra tienda por su id -> 403.

**Preguntas tipicas**
- *¿Por que JWT y no sesiones?* La API no guarda estado (stateless): sirve igual para un frontend web, movil o varias
  instancias del servidor.
- *¿Donde esta la clave del JWT?* En la variable de entorno `JWT_SECRET`, nunca en el codigo ni en Git.
- *¿Por que los roles estan en una tabla por URL y no con @PreAuthorize?* Porque la tabla se evalua **antes** de leer el
  cuerpo: con @PreAuthorize, un usuario sin permiso que mandaba datos invalidos recibia 400 con detalles de validacion
  en lugar de 403. Lo encontraron las pruebas de seguridad.

---

## 5. Catalogo: productos y presentaciones

- Cada producto tiene una **unidad base** (UNIDAD, KILO o METRO). **El stock siempre se guarda en la unidad base.**
- Cada **presentacion** es una forma de vender con su precio y un **factor** (cuantas unidades base contiene):
  Ciento = 100, Millar = 1000, Medio kilo = 0.5, Rollo 100 m = 100.
- Vender 2 cientos de clavos descuenta 2 x 100 = 200 unidades del stock.
- Una presentacion es la **principal** (la que se muestra por defecto). Solo puede haber una.
- Un producto por UNIDAD no acepta factores con decimales (no existe "media unidad" de un perno).
- Fotos: se suben a **Cloudinary** si esta configurado; si no, se guardan en una carpeta local. El tipo de archivo se
  reconoce por su contenido (no por la extension) para evitar subir archivos disfrazados.

**Pregunta tipica:** *¿Por que no guardar el stock por presentacion?* Porque seria inconsistente: 1 millar y 10 cientos
son lo mismo. Con una unidad base hay un solo numero de stock y cada venta lo convierte con el factor.

---

## 6. Inventario, kardex y traslados

### Kardex
Cada movimiento de stock deja una fila en `movimiento_inventario`: tipo (COMPRA, VENTA, TRASLADO_SALIDA...),
cantidad (+ entra, - sale), **saldo resultante**, usuario, fecha y el documento que lo origino. Asi se puede reconstruir
la historia de cualquier producto en cualquier ubicacion.

### El unico punto que mueve stock: `MovimientoStockService`
Compras, ventas, traslados, ajustes y anulaciones pasan todos por aqui. Este servicio:
1. **bloquea** la fila de stock (`SELECT ... FOR UPDATE`),
2. verifica que no quede negativa,
3. actualiza la cantidad,
4. escribe el kardex.

Ademas **exige una transaccion abierta**: el stock, el kardex y el documento (venta, compra...) se guardan juntos o no
se guarda nada.

### Traslados en dos pasos
1. **Enviar** (almacenero): sale del almacen. Estado ENVIADO ("en camino").
2. **Recibir** (vendedor de la tienda destino): entra a la tienda. Estado RECIBIDO.
Un traslado ENVIADO se puede anular y el stock vuelve al almacen.

### Ajustes e inventario inicial
- **Ajuste** (merma, rotura, conteo fisico): entrada o salida con motivo obligatorio.
- **Inventario inicial**: para cargar el stock con el que se empieza; solo si el producto no tiene movimientos.

**Pregunta tipica:** *¿Como se que el stock es correcto?* Para cada producto y ubicacion, el stock debe ser igual a la
suma de sus movimientos del kardex, y el ultimo saldo del kardex igual al stock. Se verifico con una consulta SQL al
final de la prueba de punta a punta: 0 diferencias.

---

## 7. Concurrencia: dos ventas al mismo tiempo

**Problema:** quedan 10 tubos. Dos vendedores venden 7 al mismo tiempo. Sin control, ambos leen "10", ambos venden y el
stock queda en 3 cuando en realidad se vendieron 14 (o negativo).

**Solucion: bloqueo pesimista.** La venta hace `SELECT ... FOR UPDATE` sobre la fila de stock. La segunda venta
**espera** a que la primera termine y luego lee el stock ya actualizado (3): como no alcanza, se rechaza con
`STOCK_INSUFICIENTE`.

**Para evitar deadlocks** (dos transacciones esperandose mutuamente) los bloqueos se toman siempre en el mismo orden:
caja -> serie de numeracion -> filas de stock ordenadas por id de producto.

**Pruebas:** `ConcurrenciaVentaIntegrationTest` lanza hilos que arrancan exactamente al mismo tiempo:
- 2 ventas de 7 con stock 10: solo una pasa, el stock queda en 3;
- 8 ventas de 1 con stock 5: pasan 5, el stock queda en 0, numeros de venta 1 a 5 sin repetir;
- 8 salidas por ajuste simultaneas: se comprobo que **sin el bloqueo la prueba falla** (se pierden actualizaciones) y
  con el bloqueo pasa.

---

## 8. Compras

- El almacenero registra la compra con el comprobante del proveedor (factura, boleta...).
- Se ingresa la cantidad y el precio por presentacion **tal como figuran en el comprobante** (2 cajas de 25 kg a
  S/ 100). El sistema calcula la cantidad base (50 kg) y el costo por unidad base (S/ 4.00 por kg).
- Con FACTURA se separa la base imponible y el IGV (18 %); con otros comprobantes no hay IGV deducible.
- No se puede registrar dos veces el mismo comprobante del mismo proveedor.
- **Anular** una compra (solo ADMIN) retira del stock lo que entro. Si esa mercaderia ya se vendio, no se puede.

---

## 9. Caja

- Cada vendedor **abre su caja** con un monto inicial (sencillo). Solo puede tener una abierta.
- Todo lo que cobra (ventas y abonos) queda registrado en su caja con el metodo de pago.
- Al **cerrar**, el vendedor cuenta el efectivo y el sistema calcula:
  - **efectivo esperado** = monto de apertura + todo lo cobrado en **efectivo**,
  - **diferencia** = contado - esperado (negativa = falta dinero).
- Los pagos con Yape, Plin, tarjeta, etc. se muestran por metodo pero **no** suman al efectivo del cajon.
- Si se intenta cerrar mientras se registra una venta, el cierre espera a que la venta termine (bloqueo exclusivo de la
  caja), asi ninguna venta queda fuera del cuadre.

---

## 10. Ventas

- La venta se emite en la tienda de la **caja abierta** del vendedor (no puede vender a nombre de otra tienda).
- El **precio lo pone el sistema** (de la presentacion); el vendedor solo puede aplicar un descuento por linea.
- **IGV incluido:** total = suma de lineas; base = total / 1.18; IGV = total - base.
- **Numeracion por tienda** (NV01-000001, NV01-000002...) con bloqueo de la serie para que dos ventas no reciban el
  mismo numero. Si la venta falla, el numero no se consume (no quedan huecos).
- **Contado:** los pagos deben cubrir el total. Si el cliente paga de mas en efectivo, la diferencia es el **vuelto**
  (y en caja solo queda lo que corresponde).
- **Credito (fiado):** requiere cliente; se puede dar un adelanto (incluso mixto) y el resto queda como deuda.
- **Pago mixto:** varios pagos con distintos metodos en una misma venta.
- **Anular** una venta devuelve el stock, anula los pagos (salen del cuadre) y deja la deuda en 0. Se permite mientras
  la caja donde se cobro siga abierta; con la caja cerrada la devolucion de dinero sera con nota de credito (Fase 2).

En la Fase 1 el documento es NOTA_VENTA; boleta y factura electronica (SUNAT) son de la Fase 2.

---

## 11. Fiado (credito)

- **Abono:** pago parcial posterior de una venta a credito, en la caja abierta del vendedor y en la **misma tienda**
  de la venta (cada tienda tiene su RUC y su dinero). No puede superar la deuda.
- Se bloquea la venta mientras se registra el abono, para que dos abonos simultaneos no paguen de mas.
- **Deudas:** lista de ventas con saldo (con filtro de vencidas).
- **Deudores:** clientes agrupados con su deuda total, de mayor a menor.

---

## 12. Reportes

Todos por rango de fechas (dias de Lima), paginados y calculados **en la base de datos** (consultas con `group by`),
no cargando miles de registros en memoria:

| Reporte | Que muestra |
|---------|-------------|
| Ventas por tienda | Cantidad, total vendido, contado, credito, saldo pendiente y anuladas |
| Productos mas vendidos | Por monto o por cantidad (en unidad base) |
| Traslados por tienda | Recibidos, en camino y anulados por tienda destino |
| Compras por proveedor | Cantidad y monto comprado, ultima compra |
| Stock bajo | Productos que llegaron a su stock minimo |
| Deudores | Clientes con deuda (modulo de fiado) |

---

## 13. Validaciones y errores

- **Formato** del request con Bean Validation (`@NotNull`, `@DecimalMin`, `@Size`...) -> 400 `VALIDACION` con una
  linea por campo en `detalle`.
- **Reglas de negocio** en los services -> 422 `REGLA_NEGOCIO` (Ej: "Debe abrir su caja antes de cobrar").
- **Stock insuficiente** -> 409 `STOCK_INSUFICIENTE` con lo disponible y lo requerido.
- **Duplicados** -> 409 `DUPLICADO`. **No existe** -> 404. **Sin token** -> 401. **Sin permiso** -> 403.
- Si la BD rechaza algo, se responde un mensaje generico: nunca se muestra SQL al cliente.

---

## 14. Listados y paginacion

Ningun endpoint devuelve todo: siempre `page`, `size` (maximo 100) y `sort` (solo por campos permitidos). La respuesta
trae `contenido`, `pagina`, `tamano`, `totalElementos` y `totalPaginas`. Con 50 000 ventas, el listado sigue siendo rapido.

---

## 15. Pruebas (131 en total, todas contra PostgreSQL real)

| Tipo | Que prueba | Ejemplos |
|------|------------|----------|
| Unitarias (JUnit 5 + Mockito) | Logica de negocio aislada | Calculo de IGV y vuelto, cuadre de caja, abonos, bloqueo ordenado del stock |
| Repositorio (`@DataJpaTest`) | Reglas de la BD | Stock negativo rechazado, una sola presentacion principal |
| Integracion (`@SpringBootTest` + MockMvc) | Flujos completos por HTTP con token real | Compra, traslado, venta contado, credito con pago mixto, abonos, anulacion, cierre de caja, reportes |
| Concurrencia | Ventas y salidas simultaneas | Nunca stock negativo ni venta de mas |
| Seguridad | 401, 403 por rol y por tienda | Vendedor de la tienda 1 no ve la tienda 2 |

Ademas se hizo una **prueba de punta a punta** con la aplicacion levantada, recorriendo todo por HTTP como un usuario
real, y se verificaron con SQL que stock, kardex, ventas, pagos y cajas cuadran.

---

## 16. Decisiones importantes (resumen)

El detalle de cada decision esta en `docs/DECISIONES.md`. Las mas importantes:

1. Stock en unidad base + presentaciones con factor.
2. Un solo servicio mueve el stock, con bloqueo pesimista y kardex obligatorio.
3. Todo lo que mueve stock o dinero es transaccional: o se guarda todo o nada.
4. Seguridad en dos niveles: rol (tabla de permisos) y tienda (services).
5. La venta usa la tienda de la caja abierta; el precio lo pone el sistema.
6. Cuadre de caja solo con efectivo; los pagos digitales se informan aparte.
7. Fechas de negocio en hora de Lima.
8. Errores con formato unico y sin exponer detalles internos.
9. Credenciales y cuentas externas solo por variables de entorno; Cloudinary es opcional.
10. Datos de demostracion solo en el perfil de desarrollo, nunca en las migraciones.
