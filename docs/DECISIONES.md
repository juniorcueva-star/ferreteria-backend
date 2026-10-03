# Decisiones tomadas

Registro de las decisiones de diseno tomadas durante el desarrollo (que se decidio y por que).

## Generales

- **D01. CLAUDE.md no existia.** Se creo desde cero con la descripcion del proyecto, los comandos y la
  seccion ESTANDARES DE CALIDAD pedida.
- **D02. El paso 5 estaba incompleto.** Solo estaba hecho el 5.1 (empresa, ubicacion, usuario). Antes del
  paso 6 se completaron el 5.2 (catalogo e inventario) y el 5.3 (operaciones), porque todos los pasos
  siguientes dependen de esas entidades.
- **D03. Pruebas contra PostgreSQL real en un esquema aparte.** Las pruebas usan el perfil `test`
  (`src/test/resources/application-test.yml`), que se conecta a la misma base de `.env` pero en el esquema
  `pruebas` (`?currentSchema=pruebas`). Flyway crea ese esquema y aplica V1, V2... la primera vez. Asi no
  hace falta crear otra base de datos en la laptop, no se tocan los datos de desarrollo y se prueban los CHECK
  e indices parciales que H2 no soporta.

## Entidades (paso 5)

- **D04. Fechas con `@CreationTimestamp`.** Columnas como `venta.fecha`, `pago.fecha` o `traslado.fecha_envio`
  tienen `DEFAULT NOW()` en la BD, pero JPA inserta todas las columnas; por eso Hibernate pone la fecha al insertar.
- **D05. Llaves foraneas compuestas.** Las FK compuestas de V1 (por ejemplo, la presentacion debe ser del mismo
  producto) se mapean como `@ManyToOne` simples; la BD sigue validando la combinacion y el service tambien lo comprueba.
- **D06. `TipoMovimiento` sabe si es entrada o salida**, replicando el CHECK `ck_mov_signo`, para que el codigo
  no tenga que repetir esa lista.

## Errores, DTOs y validaciones (paso 6)

- **D07. Formato unico de error** `ErrorResponse(codigo, mensaje, detalle, fecha)`. `detalle` es una lista de
  textos (vacia si no hay nada que agregar); en errores de validacion trae una linea por campo
  (`"cantidad: debe ser mayor que 0"`). Se eligio lista para que el frontend no tenga que adivinar el tipo.
- **D08. Codigos de error y estados HTTP** (enum `CodigoError`): 400 `VALIDACION`/`SOLICITUD_INVALIDA`,
  401 `NO_AUTENTICADO`/`CREDENCIALES_INVALIDAS`, 403 `ACCESO_DENEGADO`, 404 `RECURSO_NO_ENCONTRADO`,
  409 `DUPLICADO`/`STOCK_INSUFICIENTE`/`CONFLICTO_DATOS`, 422 `REGLA_NEGOCIO` (la peticion esta bien formada
  pero el negocio no la permite, Ej: vender sin caja abierta), 500 `ERROR_INTERNO`.
- **D09. Los errores de la BD no se exponen.** Si un CHECK/UNIQUE rechaza algo que el service no detecto, se
  responde 409 `CONFLICTO_DATOS` con un mensaje generico y el detalle tecnico solo va al log.
- **D10. Paginacion.** Todos los listados reciben `page`, `size` y `sort` (Spring Data) y responden
  `PaginaResponse` (`contenido`, `pagina`, `tamano`, `totalElementos`, `totalPaginas`). Tamano por defecto 20 y
  maximo 100 (`spring.data.web.pageable.max-page-size`): aunque se pida `size=5000` se devuelven 100.
- **D11. DTOs como `record`** de Java: inmutables, con validaciones Bean Validation en los `...Request` y un
  metodo estatico `desde(entidad)` en los `...Response` para convertir dentro del service.

## Seguridad (paso 7)

- **D12. JWT con el "resource server" de Spring Security** (HS256, `NimbusJwtEncoder`/`NimbusJwtDecoder`) en vez
  de un filtro escrito a mano: Spring valida firma y vencimiento, y se escribe menos codigo propio que pueda fallar.
  La clave viene de `JWT_SECRET` (minimo 32 caracteres; si falta, la app no arranca). El token dura 8 horas
  (`JWT_EXPIRACION_MINUTOS`), un turno de trabajo.
- **D13. El usuario se lee de la BD en cada peticion** (`UsuarioJwtConverter`). El token solo sirve para
  identificarlo; rol y tienda se toman de la BD. Asi un usuario desactivado pierde el acceso al instante y un
  cambio de tienda aplica sin esperar a que venza el token. Costo: una consulta por id (clave primaria) por peticion.
- **D14. Permisos por rol con `@PreAuthorize`** en cada controller y **permisos por tienda en los services**
  (`AccesoUbicacionService`): el ADMIN ve y opera todo; VENDEDOR y ALMACENERO solo su propia ubicacion. Si piden
  datos de otra ubicacion se responde 403; si no indican ubicacion se usa la suya.
- **D15. Roles y ubicaciones:** VENDEDOR solo puede asignarse a una TIENDA y ALMACENERO a un ALMACEN; el ADMIN no
  tiene ubicacion. No se puede quitar el ultimo ADMIN activo ni un ADMIN puede desactivarse a si mismo.
- **D16. Admin inicial:** al arrancar, si no hay ningun ADMIN activo se crea uno con `ADMIN_USERNAME` y
  `ADMIN_PASSWORD`. Si `ADMIN_PASSWORD` no esta configurada solo se registra una advertencia (no hay contrasenas
  en el codigo).
- **D17. Login sin pistas:** usuario inexistente, contrasena incorrecta o usuario inactivo dan el mismo 401
  `CREDENCIALES_INVALIDAS`, y cuando el usuario no existe igual se calcula un BCrypt para que el tiempo de respuesta
  no revele que usuarios existen.
- **D18. Empresas, ubicaciones y usuarios** se administran por API (solo ADMIN). Las ubicaciones las puede listar
  cualquier usuario autenticado: su nombre no es un dato sensible y se necesita, por ejemplo, para ver el destino
  de un traslado. No se borran registros: se desactivan (`activo=false`) para no romper el historial.
- **D19. CORS** configurable con `CORS_ORIGENES` (por defecto los puertos tipicos de Vite y React en localhost).

## Catalogo (paso 8)

- **D20. Producto y presentaciones se crean juntos.** Un producto nace con al menos una presentacion; si ninguna
  se marca como principal, la primera lo es. Siempre hay exactamente una principal y activa: para cambiarla se
  marca otra como principal (la anterior se desmarca sola). Antes de marcar la nueva se guarda el cambio de la
  anterior, porque el indice unico parcial `uq_presentacion_principal` no permite dos principales ni por un instante.
- **D21. La unidad base no se puede cambiar** despues de crear el producto: el stock y el kardex ya estan
  guardados en esa unidad.
- **D22. Productos por UNIDAD solo aceptan factores enteros** (no existe "media unidad" de un perno); los productos
  por KILO o METRO si aceptan fracciones (medio kilo = 0.5).
- **D23. Fotos con almacenamiento intercambiable** (`AlmacenImagenes`): si existe `CLOUDINARY_URL` se usa
  Cloudinary; si no, se guardan en la carpeta `./imagenes` (`IMAGENES_DIRECTORIO`) y se publican en `/imagenes/**`.
  Asi el sistema funciona sin cuenta de Cloudinary. El tipo de archivo se reconoce por sus primeros bytes (no por el
  Content-Type que manda el cliente), se aceptan JPG, PNG y WEBP de hasta 2 MB y el nombre del archivo lo genera
  el sistema (UUID), para que nadie pueda escribir en otra ruta.
- **D24. Nada se borra.** Productos, presentaciones y categorias se desactivan (`activo=false`) porque las ventas,
  compras y el kardex los referencian.
- **D25. Listado de productos con presentaciones** usando `@BatchSize` en la relacion: las presentaciones de toda
  la pagina se cargan en una sola consulta extra (evita el problema N+1 sin paginar en memoria).

## Compras y movimiento de stock (paso 9)

- **D26. Un solo punto modifica el stock: `MovimientoStockService`.** Bloquea la fila con `SELECT ... FOR UPDATE`
  (bloqueo pesimista), valida que no quede negativa, actualiza y escribe el kardex con el saldo resultante. Exige una
  transaccion abierta (`Propagation.MANDATORY`): si algo falla despues, se deshace todo junto (stock, kardex y documento).
- **D27. Orden fijo de bloqueo.** Si una operacion mueve varios productos, las filas se bloquean ordenadas por id de
  producto. Dos ventas con los mismos productos en distinto orden no pueden bloquearse mutuamente (deadlock).
  Si un producto aparece en varias lineas se suma en un solo movimiento.
- **D28. Fila de stock creada con `INSERT ... ON CONFLICT DO NOTHING`.** La primera entrada de un producto en una
  ubicacion crea su fila en 0 y luego la bloquea; si dos transacciones la crean a la vez no hay error de duplicado.
- **D29. Precio de compra por presentacion.** En la compra se ingresa la cantidad y el precio tal como vienen en el
  comprobante (Ej: 2 cajas de 25 kg a S/ 100). El sistema calcula la cantidad base (50 kg), el subtotal (S/ 200) y el
  costo por unidad base (S/ 4.0000), que se guarda en el detalle y en el kardex.
- **D30. IGV de compras.** Los precios se ingresan con IGV. Con FACTURA se separa base e IGV (credito fiscal); con
  boleta, nota de venta o sin comprobante el IGV no es deducible y todo queda como subtotal (IGV 0).
- **D31. Comprobante duplicado.** No se puede registrar dos veces el mismo `serieNumero` del mismo proveedor mientras
  la compra este REGISTRADA (comparacion sin importar mayusculas).
- **D32. Anulacion de compra (solo ADMIN, con motivo).** Retira del stock lo que entro (kardex `ANULACION_COMPRA` con
  el motivo) y agrega "ANULADA: motivo" a la observacion (no hay columna de motivo en V1 y no hacia falta una
  migracion). Si esa mercaderia ya se vendio o traslado y no alcanza el stock, la anulacion se rechaza con
  `STOCK_INSUFICIENTE`: primero hay que corregir con un ajuste.
- **D33. Quien compra:** ADMIN (indicando la ubicacion) y ALMACENERO (solo en su almacen). El ADMIN puede comprar
  directo a una tienda.

## Inventario y traslados (paso 10)

- **D34. Migracion V3: secuencia `traslado_codigo_seq`.** El codigo `TR-000001` debe existir al insertar el
  traslado (columna NOT NULL UNIQUE). Una secuencia de PostgreSQL da numeros unicos aun con envios simultaneos, sin
  tablas extra ni doble escritura. Es la unica migracion nueva; V1 y V2 no se tocaron.
- **D35. Traslado en dos pasos.** Enviar resta del origen; recibir suma en el destino. Mientras esta ENVIADO la
  mercaderia esta "en camino" y no se puede vender en ninguna de las dos ubicaciones. Solo un traslado ENVIADO se
  puede anular (devuelve al origen); uno RECIBIDO ya no, se corrige con otro traslado o un ajuste.
- **D36. Quien hace que en traslados:** enviar y anular, ADMIN o ALMACENERO desde su almacen; recibir, quien trabaja
  en la ubicacion destino (el vendedor de esa tienda) o el ADMIN. No hay recepcion parcial: si llega menos, se recibe
  y se registra un ajuste de salida con el motivo.
- **D37. Ajustes de inventario** (ENTRADA/SALIDA) con motivo obligatorio, solo ADMIN o ALMACENERO en su almacen.
  Los vendedores no ajustan stock: una rotura en tienda la registra el ADMIN.
- **D38. Inventario inicial** (solo ADMIN): para cargar el stock con el que se empieza. Solo se permite si el producto
  no tiene ningun movimiento en esa ubicacion; despues se usan compras o ajustes, para que el kardex no se reescriba.
- **D39. Visibilidad del stock y kardex:** quien no es ADMIN solo ve su ubicacion (el vendedor no ve el stock del
  almacen ni de la otra tienda). Es la interpretacion mas estricta de "no ver datos de otra tienda"; si se quiere que
  el vendedor consulte el almacen se cambia en `InventarioService`.
- **D40. Fechas de los filtros** (`desde`/`hasta`) son dias de Lima: el dia va de 00:00 a 24:00 hora de Lima
  aunque el servidor este en UTC (componente `Calendario`).

## Clientes y caja (paso 11)

- **D41. Clientes compartidos.** La tabla cliente no tiene tienda: un cliente puede comprar en ambas tiendas.
  Lo que si es por tienda son sus deudas (ventas a credito), que se filtran por la tienda de la venta.
  Pueden registrarlos ADMIN y VENDEDOR. El documento se valida con las mismas reglas del CHECK `ck_cliente_doc`
  (DNI 8 digitos, RUC 11, CE obligatorio, NINGUNO sin numero) para dar un mensaje claro antes de llegar a la BD.
- **D42. Una caja abierta por usuario y solo en una tienda.** El vendedor abre en su tienda; el ADMIN puede abrir
  indicando la tienda (por ejemplo, para cubrir un turno). El indice unico parcial `uq_caja_abierta_usuario` es la
  ultima barrera si llegan dos aperturas a la vez.
- **D43. Cuadre de caja:** efectivo esperado = monto de apertura + pagos VALIDOS en efectivo de esa caja (ventas al
  contado, adelantos de fiado y abonos). Los pagos con Yape, tarjeta, etc. se informan por metodo pero no cuentan
  para el efectivo. Diferencia = contado - esperado (negativo = falta dinero).
- **D44. Cierre sin carreras:** las ventas y abonos toman un bloqueo compartido (`FOR SHARE`) de la caja y el cierre
  un bloqueo exclusivo. Si se cierra mientras se registra una venta, el cierre espera a que termine y la incluye; una
  venta que llega despues del cierre ve la caja CERRADA y se rechaza.
- **D45. Solo el duenio de la caja (o el ADMIN) la cierra.** Los vendedores pueden consultar las cajas de su tienda,
  no las de la otra.

## Ventas (paso 12)

- **D46. La venta se emite en la tienda de la caja abierta del usuario.** No se envia la tienda en el request:
  sale de la caja (y el RUC, de la tienda). Asi un vendedor no puede vender a nombre de otra tienda. Si lo
  cambiaron de tienda con la caja abierta, ya no puede cobrar en la anterior.
- **D47. Solo NOTA_VENTA en la Fase 1.** Boleta y factura electronica (SUNAT) quedan para la Fase 2.
- **D48. Correlativo por tienda** en `serie_correlativo`, con bloqueo exclusivo de la fila para que dos ventas no
  reciban el mismo numero. Si la tienda aun no tiene serie, se crea sola: `NV` + id de la tienda con 2 digitos
  (NV02...). Como el numero se toma dentro de la misma transaccion, si la venta falla (por ejemplo, sin stock) el
  numero no se consume y no quedan huecos.
- **D49. Orden de bloqueos en una venta:** caja (compartido) -> serie de la tienda (exclusivo) -> filas de stock
  (por id de producto). Siempre el mismo orden, por eso no hay deadlocks. Como las ventas de una misma tienda se
  ordenan en la serie, el bloqueo del stock protege sobre todo frente a traslados y ajustes simultaneos; las pruebas
  de concurrencia cubren ambos casos (y se comprobo que sin el bloqueo de stock la prueba de ajustes falla).
- **D50. El precio lo pone el sistema,** no el cliente: se toma de la presentacion. El vendedor solo puede aplicar
  un descuento por linea que no supere el importe de la linea. `venta.descuento` guarda la suma (informativo).
- **D51. IGV incluido:** total = suma de lineas; subtotal = total / 1.18 (redondeo HALF_UP); IGV = total - subtotal.
  Asi el CHECK `total = subtotal + igv` siempre cuadra.
- **D52. Pagos por codigo de metodo** (`"metodoPago": "EFECTIVO"`, `"YAPE"`...) en vez de id, porque son estables
  (vienen de V2) y mas legibles en Swagger. Los metodos con `requiere_referencia` exigen `numeroOperacion`.
- **D53. Contado:** los pagos deben cubrir el total. Si se paga de mas, la diferencia es vuelto y solo puede salir
  del efectivo; el pago en efectivo se guarda por el monto neto (lo que realmente queda en la caja). Pagar de mas
  con Yape o tarjeta se rechaza.
- **D54. Credito (fiado):** requiere cliente; los pagos son un adelanto opcional (pago mixto permitido) que no puede
  superar el total; el resto es `saldo_pendiente`. La fecha de vencimiento es opcional y no puede ser pasada.
- **D55. Anulacion de venta:** devuelve el stock (kardex `ANULACION_VENTA`), pone los pagos en ANULADO (salen del
  cuadre) y deja el saldo en 0. Se permite solo si todas las cajas donde se cobro siguen ABIERTAS, porque el dinero
  se devuelve desde esa caja; con la caja cerrada la devolucion de dinero sera una nota de credito (Fase 2). El
  vendedor solo anula ventas de su tienda emitidas en una caja aun abierta; el ADMIN puede anular, por ejemplo, un
  fiado sin pagos de una caja ya cerrada.

## Fiado (paso 13)

- **D56. El abono se cobra en la tienda que vendio,** en la caja abierta del usuario (puede ser otro dia y otra caja
  que la de la venta). Cada tienda tiene su RUC: el dinero de una deuda de la tienda 1 no entra a la caja de la
  tienda 2. Admite varios metodos a la vez y no puede superar el saldo pendiente.
- **D57. Bloqueo de la venta al abonar** (`FOR UPDATE`): dos abonos simultaneos a la misma deuda no pueden pagar de
  mas. Orden de bloqueo en abonos y anulaciones: venta -> caja (igual en ambos, sin deadlocks).
- **D58. Reporte de deudores** agrupado por cliente (deuda total, cantidad de ventas, deuda mas antigua y vencimiento
  mas proximo), de mayor a menor deuda, paginado y por tienda (el vendedor solo ve los deudores de su tienda).
  El orden lo fija la consulta; el parametro `sort` se ignora en este reporte.
- **D59. Abonos en el cuadre:** los abonos en efectivo suman al efectivo esperado de la caja donde se cobraron; el
  resumen de caja los muestra aparte (`totalAbonos`).
