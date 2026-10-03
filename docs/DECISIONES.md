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
