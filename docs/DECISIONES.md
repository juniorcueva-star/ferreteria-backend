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
