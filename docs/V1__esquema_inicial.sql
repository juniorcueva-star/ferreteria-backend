-- =====================================================================
-- Sistema de Gestion Ferretera - Esquema inicial (Flyway V1)
-- Base de datos: PostgreSQL 16
-- Alcance: Fase 1 (almacen, tiendas, compras, ventas, fiado y caja)
-- Convenciones:
--   * Dinero: NUMERIC(12,2)
--   * Cantidades: NUMERIC(14,3)  (permite kilos y metros con decimales)
--   * El stock SIEMPRE se guarda en la unidad base del producto
--   * Fechas con zona horaria (TIMESTAMPTZ): el servidor en la nube esta
--     en UTC y Lima en UTC-5; asi los reportes por dia no se desfasan
--   * Textos fijos (estados, tipos) como VARCHAR + CHECK, mapeados en Java
--     con @Enumerated(EnumType.STRING)
--   * En las entidades JPA: TIMESTAMPTZ -> OffsetDateTime (no LocalDateTime),
--     DATE -> LocalDate, NUMERIC -> BigDecimal, BIGSERIAL -> Long con IDENTITY
--   * Redondeo en Java: BigDecimal.setScale(3 o 2, RoundingMode.HALF_UP),
--     igual que ROUND() de PostgreSQL, para que los CHECK de totales cuadren
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. ORGANIZACION: empresas (RUC), tiendas y almacen
-- ---------------------------------------------------------------------
CREATE TABLE empresa (
    id                BIGSERIAL PRIMARY KEY,
    ruc               VARCHAR(11)  NOT NULL UNIQUE,
    razon_social      VARCHAR(150) NOT NULL,
    nombre_comercial  VARCHAR(150),
    direccion         VARCHAR(200),
    activo            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_empresa_ruc CHECK (ruc ~ '^[0-9]{11}$')
);

-- Una ubicacion es el almacen o una tienda.
-- Cada tienda pertenece a una empresa (RUC). El almacen es compartido.
CREATE TABLE ubicacion (
    id          BIGSERIAL PRIMARY KEY,
    empresa_id  BIGINT       REFERENCES empresa(id),
    nombre      VARCHAR(100) NOT NULL UNIQUE,
    tipo        VARCHAR(10)  NOT NULL,
    direccion   VARCHAR(200),
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_ubicacion_tipo CHECK (tipo IN ('ALMACEN', 'TIENDA')),
    CONSTRAINT ck_tienda_con_empresa CHECK (tipo = 'ALMACEN' OR empresa_id IS NOT NULL),
    -- Permite que la venta valide que su RUC corresponde a su tienda
    CONSTRAINT uq_ubicacion_empresa UNIQUE (id, empresa_id)
);

-- ---------------------------------------------------------------------
-- 2. USUARIOS Y ROLES
--   ADMIN      = dueno, ve todo
--   VENDEDOR   = vende solo en su tienda
--   ALMACENERO = recibe compras y envia traslados desde el almacen
-- ---------------------------------------------------------------------
CREATE TABLE usuario (
    id             BIGSERIAL PRIMARY KEY,
    nombres        VARCHAR(120) NOT NULL,
    username       VARCHAR(50)  NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,           -- BCrypt, nunca texto plano
    rol            VARCHAR(15)  NOT NULL,
    ubicacion_id   BIGINT       REFERENCES ubicacion(id),
    activo         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_usuario_rol CHECK (rol IN ('ADMIN', 'VENDEDOR', 'ALMACENERO')),
    CONSTRAINT ck_usuario_ubicacion CHECK (rol = 'ADMIN' OR ubicacion_id IS NOT NULL)
);

-- ---------------------------------------------------------------------
-- 3. CATALOGO: categorias, productos y presentaciones
-- ---------------------------------------------------------------------
CREATE TABLE categoria (
    id      BIGSERIAL PRIMARY KEY,
    nombre  VARCHAR(80) NOT NULL UNIQUE,
    activo  BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE producto (
    id                BIGSERIAL PRIMARY KEY,
    codigo            VARCHAR(30)   NOT NULL UNIQUE,   -- codigo interno
    nombre            VARCHAR(150)  NOT NULL,
    descripcion       VARCHAR(300),
    marca             VARCHAR(80),
    categoria_id      BIGINT        NOT NULL REFERENCES categoria(id),
    unidad_base       VARCHAR(10)   NOT NULL,          -- en que se guarda el stock
    imagen_url        VARCHAR(500),                    -- link de Cloudinary
    imagen_public_id  VARCHAR(200),                    -- id en Cloudinary (para borrar/reemplazar)
    activo            BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),   -- lo actualiza JPA (@UpdateTimestamp)
    CONSTRAINT ck_producto_unidad CHECK (unidad_base IN ('UNIDAD', 'KILO', 'METRO'))
);

-- Formas de vender un producto. Ej: Unidad (1), Ciento (100), Millar (1000),
-- Medio kilo (0.5), Rollo 30 m (30). Cada una con su propio precio.
CREATE TABLE presentacion (
    id             BIGSERIAL PRIMARY KEY,
    producto_id    BIGINT        NOT NULL REFERENCES producto(id),
    nombre         VARCHAR(50)   NOT NULL,
    factor         NUMERIC(14,3) NOT NULL,             -- cuantas unidades base contiene
    precio_venta   NUMERIC(12,2) NOT NULL,             -- precio con IGV incluido
    codigo_barras  VARCHAR(50)   UNIQUE,
    es_principal   BOOLEAN       NOT NULL DEFAULT FALSE,
    activo         BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_presentacion_nombre UNIQUE (producto_id, nombre),
    -- Permite que los detalles validen que la presentacion es de ese producto
    CONSTRAINT uq_presentacion_producto UNIQUE (id, producto_id),
    CONSTRAINT ck_presentacion_factor CHECK (factor > 0),
    CONSTRAINT ck_presentacion_precio CHECK (precio_venta >= 0)
);

-- Solo una presentacion principal por producto
CREATE UNIQUE INDEX uq_presentacion_principal
    ON presentacion(producto_id) WHERE es_principal;

-- ---------------------------------------------------------------------
-- 4. INVENTARIO: stock por ubicacion y traslados
-- ---------------------------------------------------------------------
CREATE TABLE stock (
    id            BIGSERIAL PRIMARY KEY,
    producto_id   BIGINT        NOT NULL REFERENCES producto(id),
    ubicacion_id  BIGINT        NOT NULL REFERENCES ubicacion(id),
    cantidad      NUMERIC(14,3) NOT NULL DEFAULT 0,
    stock_minimo  NUMERIC(14,3) NOT NULL DEFAULT 0,   -- alerta de stock bajo, por ubicacion
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_stock UNIQUE (producto_id, ubicacion_id),
    -- Ultima barrera: la base de datos nunca permite stock negativo
    CONSTRAINT ck_stock_no_negativo CHECK (cantidad >= 0),
    CONSTRAINT ck_stock_minimo CHECK (stock_minimo >= 0)
);

-- Traslado del almacen a una tienda (o entre tiendas).
-- ENVIADO: sale stock del origen. RECIBIDO: entra stock al destino.
CREATE TABLE traslado (
    id                 BIGSERIAL PRIMARY KEY,
    codigo             VARCHAR(20) NOT NULL UNIQUE,     -- Ej: TR-000001
    origen_id          BIGINT      NOT NULL REFERENCES ubicacion(id),
    destino_id         BIGINT      NOT NULL REFERENCES ubicacion(id),
    estado             VARCHAR(10) NOT NULL DEFAULT 'ENVIADO',
    usuario_envia_id   BIGINT      NOT NULL REFERENCES usuario(id),
    usuario_recibe_id  BIGINT      REFERENCES usuario(id),
    fecha_envio        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_recepcion    TIMESTAMPTZ,
    observacion        VARCHAR(300),
    CONSTRAINT ck_traslado_estado CHECK (estado IN ('ENVIADO', 'RECIBIDO', 'ANULADO')),
    CONSTRAINT ck_traslado_distinto CHECK (origen_id <> destino_id),
    -- Si esta recibido, se sabe quien y cuando lo recibio
    CONSTRAINT ck_traslado_recepcion CHECK (
        estado <> 'RECIBIDO' OR (usuario_recibe_id IS NOT NULL AND fecha_recepcion IS NOT NULL))
);

CREATE TABLE traslado_detalle (
    id               BIGSERIAL PRIMARY KEY,
    traslado_id      BIGINT        NOT NULL REFERENCES traslado(id) ON DELETE CASCADE,
    producto_id      BIGINT        NOT NULL REFERENCES producto(id),
    presentacion_id  BIGINT,
    cantidad         NUMERIC(14,3) NOT NULL,            -- en la presentacion elegida
    cantidad_base    NUMERIC(14,3) NOT NULL,            -- cantidad * factor
    CONSTRAINT fk_traslado_det_presentacion FOREIGN KEY (presentacion_id, producto_id)
        REFERENCES presentacion(id, producto_id),
    CONSTRAINT ck_traslado_det_cant CHECK (cantidad > 0 AND cantidad_base > 0)
);

-- ---------------------------------------------------------------------
-- 5. COMPRAS: proveedores y compras (la mercaderia entra al almacen)
-- ---------------------------------------------------------------------
CREATE TABLE proveedor (
    id            BIGSERIAL PRIMARY KEY,
    ruc           VARCHAR(11)  UNIQUE,
    razon_social  VARCHAR(150) NOT NULL,
    contacto      VARCHAR(100),
    telefono      VARCHAR(20),
    email         VARCHAR(100),
    direccion     VARCHAR(200),
    activo        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_proveedor_ruc CHECK (ruc IS NULL OR ruc ~ '^[0-9]{11}$')
);

CREATE TABLE compra (
    id                  BIGSERIAL PRIMARY KEY,
    empresa_id          BIGINT        NOT NULL REFERENCES empresa(id),   -- que RUC compra
    proveedor_id        BIGINT        NOT NULL REFERENCES proveedor(id),
    ubicacion_id        BIGINT        NOT NULL REFERENCES ubicacion(id), -- donde entra (normalmente almacen)
    usuario_id          BIGINT        NOT NULL REFERENCES usuario(id),
    tipo_comprobante    VARCHAR(15)   NOT NULL,
    serie_numero        VARCHAR(30),                                     -- Ej: F001-000123
    fecha_emision       DATE          NOT NULL,
    subtotal            NUMERIC(12,2) NOT NULL DEFAULT 0,                -- sin IGV
    igv                 NUMERIC(12,2) NOT NULL DEFAULT 0,
    total               NUMERIC(12,2) NOT NULL DEFAULT 0,
    estado              VARCHAR(10)   NOT NULL DEFAULT 'REGISTRADA',
    observacion         VARCHAR(300),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_compra_comprobante CHECK (tipo_comprobante IN ('FACTURA', 'BOLETA', 'NOTA_VENTA', 'SIN_COMPROBANTE')),
    CONSTRAINT ck_compra_estado CHECK (estado IN ('REGISTRADA', 'ANULADA')),
    CONSTRAINT ck_compra_montos CHECK (subtotal >= 0 AND igv >= 0 AND total >= 0),
    CONSTRAINT ck_compra_total CHECK (total = subtotal + igv)
);

CREATE TABLE compra_detalle (
    id               BIGSERIAL PRIMARY KEY,
    compra_id        BIGINT        NOT NULL REFERENCES compra(id) ON DELETE CASCADE,
    producto_id      BIGINT        NOT NULL REFERENCES producto(id),
    presentacion_id  BIGINT,                     -- opcional: un saco no siempre trae lo mismo
    cantidad         NUMERIC(14,3) NOT NULL,
    cantidad_base    NUMERIC(14,3) NOT NULL,     -- lo que entra al stock (Ej: saco = 5000 unidades)
    costo_unitario   NUMERIC(12,4) NOT NULL,     -- costo por unidad base
    subtotal         NUMERIC(12,2) NOT NULL,
    CONSTRAINT fk_compra_det_presentacion FOREIGN KEY (presentacion_id, producto_id)
        REFERENCES presentacion(id, producto_id),
    CONSTRAINT ck_compra_det CHECK (cantidad > 0 AND cantidad_base > 0
                                    AND costo_unitario >= 0 AND subtotal >= 0)
);

-- ---------------------------------------------------------------------
-- 6. CLIENTES (solo como dato para el fiado; no acceden al sistema)
-- ---------------------------------------------------------------------
CREATE TABLE cliente (
    id                BIGSERIAL PRIMARY KEY,
    nombre            VARCHAR(150) NOT NULL,
    tipo_documento    VARCHAR(10)  NOT NULL DEFAULT 'NINGUNO',
    numero_documento  VARCHAR(15),
    telefono          VARCHAR(20),
    direccion         VARCHAR(200),
    activo            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_cliente_tipo_doc CHECK (tipo_documento IN ('NINGUNO', 'DNI', 'RUC', 'CE')),
    CONSTRAINT ck_cliente_doc CHECK (
        (tipo_documento = 'NINGUNO' AND numero_documento IS NULL) OR
        (tipo_documento = 'DNI' AND numero_documento ~ '^[0-9]{8}$') OR
        (tipo_documento = 'RUC' AND numero_documento ~ '^[0-9]{11}$') OR
        (tipo_documento = 'CE'  AND numero_documento IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_cliente_documento
    ON cliente(tipo_documento, numero_documento) WHERE numero_documento IS NOT NULL;

-- ---------------------------------------------------------------------
-- 7. CAJA: cada vendedor abre y cierra su caja
-- ---------------------------------------------------------------------
CREATE TABLE caja_sesion (
    id                    BIGSERIAL PRIMARY KEY,
    ubicacion_id          BIGINT        NOT NULL REFERENCES ubicacion(id),
    usuario_id            BIGINT        NOT NULL REFERENCES usuario(id),
    fecha_apertura        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    monto_apertura        NUMERIC(12,2) NOT NULL DEFAULT 0,   -- sencillo inicial
    fecha_cierre          TIMESTAMPTZ,
    efectivo_esperado     NUMERIC(12,2),                      -- lo que calcula el sistema
    efectivo_contado      NUMERIC(12,2),                      -- lo que cuenta el vendedor
    diferencia            NUMERIC(12,2),                      -- contado - esperado
    estado                VARCHAR(10)   NOT NULL DEFAULT 'ABIERTA',
    CONSTRAINT ck_caja_estado CHECK (estado IN ('ABIERTA', 'CERRADA')),
    CONSTRAINT ck_caja_apertura CHECK (monto_apertura >= 0),
    -- Una caja cerrada debe tener sus datos de cierre completos
    CONSTRAINT ck_caja_cierre CHECK (
        estado = 'ABIERTA' OR (fecha_cierre IS NOT NULL AND efectivo_esperado IS NOT NULL
                               AND efectivo_contado IS NOT NULL AND diferencia IS NOT NULL)),
    -- Permite que la venta valide que la caja es de su misma tienda
    CONSTRAINT uq_caja_ubicacion UNIQUE (id, ubicacion_id)
);

-- Un vendedor no puede tener dos cajas abiertas a la vez
CREATE UNIQUE INDEX uq_caja_abierta_usuario
    ON caja_sesion(usuario_id) WHERE estado = 'ABIERTA';

-- ---------------------------------------------------------------------
-- 8. VENTAS
-- ---------------------------------------------------------------------
-- Correlativos por tienda y tipo de documento (Ej: NV01-000245)
CREATE TABLE serie_correlativo (
    id              BIGSERIAL PRIMARY KEY,
    ubicacion_id    BIGINT      NOT NULL REFERENCES ubicacion(id),
    tipo_documento  VARCHAR(12) NOT NULL,
    serie           VARCHAR(4)  NOT NULL,
    ultimo_numero   INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT uq_serie UNIQUE (ubicacion_id, tipo_documento, serie),
    CONSTRAINT ck_serie_tipo CHECK (tipo_documento IN ('NOTA_VENTA', 'BOLETA', 'FACTURA')),
    CONSTRAINT ck_serie_numero CHECK (ultimo_numero >= 0)
);

CREATE TABLE venta (
    id                 BIGSERIAL PRIMARY KEY,
    ubicacion_id       BIGINT        NOT NULL,                            -- tienda
    empresa_id         BIGINT        NOT NULL,                            -- RUC de la tienda
    usuario_id         BIGINT        NOT NULL REFERENCES usuario(id),     -- vendedor
    caja_sesion_id     BIGINT        NOT NULL,
    cliente_id         BIGINT        REFERENCES cliente(id),              -- NULL = cliente varios
    tipo_documento     VARCHAR(12)   NOT NULL DEFAULT 'NOTA_VENTA',
    serie              VARCHAR(4)    NOT NULL,
    numero             INTEGER       NOT NULL,
    fecha              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    condicion          VARCHAR(10)   NOT NULL DEFAULT 'CONTADO',
    fecha_vencimiento  DATE,                                              -- opcional, para fiado
    subtotal           NUMERIC(12,2) NOT NULL,                            -- sin IGV
    igv                NUMERIC(12,2) NOT NULL,
    descuento          NUMERIC(12,2) NOT NULL DEFAULT 0,                  -- informativo, ya aplicado
    total              NUMERIC(12,2) NOT NULL,                            -- subtotal + igv
    saldo_pendiente    NUMERIC(12,2) NOT NULL DEFAULT 0,                  -- deuda del fiado
    estado             VARCHAR(10)   NOT NULL DEFAULT 'EMITIDA',
    estado_sunat       VARCHAR(15),                                       -- Fase 2
    motivo_anulacion   VARCHAR(300),
    -- La tienda y su RUC deben coincidir
    CONSTRAINT fk_venta_tienda_empresa FOREIGN KEY (ubicacion_id, empresa_id)
        REFERENCES ubicacion(id, empresa_id),
    -- La caja debe ser de la misma tienda
    CONSTRAINT fk_venta_caja_tienda FOREIGN KEY (caja_sesion_id, ubicacion_id)
        REFERENCES caja_sesion(id, ubicacion_id),
    CONSTRAINT uq_venta_numero UNIQUE (empresa_id, tipo_documento, serie, numero),
    CONSTRAINT ck_venta_tipo CHECK (tipo_documento IN ('NOTA_VENTA', 'BOLETA', 'FACTURA')),
    CONSTRAINT ck_venta_condicion CHECK (condicion IN ('CONTADO', 'CREDITO')),
    CONSTRAINT ck_venta_estado CHECK (estado IN ('EMITIDA', 'ANULADA')),
    CONSTRAINT ck_venta_montos CHECK (subtotal >= 0 AND igv >= 0 AND descuento >= 0 AND total >= 0),
    CONSTRAINT ck_venta_total CHECK (total = subtotal + igv),
    CONSTRAINT ck_venta_saldo CHECK (saldo_pendiente >= 0 AND saldo_pendiente <= total),
    -- El fiado siempre necesita saber a quien se le fio
    CONSTRAINT ck_credito_con_cliente CHECK (condicion = 'CONTADO' OR cliente_id IS NOT NULL),
    CONSTRAINT ck_contado_sin_saldo CHECK (condicion = 'CREDITO' OR saldo_pendiente = 0),
    CONSTRAINT ck_vencimiento_solo_credito CHECK (condicion = 'CREDITO' OR fecha_vencimiento IS NULL),
    -- Una venta anulada no deja deuda y debe explicar por que se anulo
    CONSTRAINT ck_venta_anulada CHECK (
        estado = 'EMITIDA' OR (saldo_pendiente = 0 AND motivo_anulacion IS NOT NULL))
);

CREATE TABLE venta_detalle (
    id               BIGSERIAL PRIMARY KEY,
    venta_id         BIGINT        NOT NULL REFERENCES venta(id) ON DELETE CASCADE,
    producto_id      BIGINT        NOT NULL REFERENCES producto(id),
    presentacion_id  BIGINT        NOT NULL,
    descripcion      VARCHAR(200)  NOT NULL,     -- copia del nombre al momento de vender
    cantidad         NUMERIC(14,3) NOT NULL,     -- Ej: 2 millares, 2.5 kilos
    factor           NUMERIC(14,3) NOT NULL,     -- copia del factor al momento de vender
    cantidad_base    NUMERIC(14,3) NOT NULL,     -- lo que se descuenta del stock
    precio_unitario  NUMERIC(12,2) NOT NULL,     -- precio de la presentacion, con IGV
    descuento        NUMERIC(12,2) NOT NULL DEFAULT 0,
    subtotal         NUMERIC(12,2) NOT NULL,
    CONSTRAINT fk_venta_det_presentacion FOREIGN KEY (presentacion_id, producto_id)
        REFERENCES presentacion(id, producto_id),
    CONSTRAINT ck_venta_det CHECK (cantidad > 0 AND factor > 0 AND cantidad_base > 0
                                   AND precio_unitario >= 0 AND descuento >= 0 AND subtotal >= 0),
    -- Lo que se descuenta del stock cuadra con lo vendido
    CONSTRAINT ck_venta_det_base CHECK (cantidad_base = ROUND(cantidad * factor, 3))
);

-- ---------------------------------------------------------------------
-- 9. PAGOS (pago al contado, adelanto o abono de un fiado)
-- ---------------------------------------------------------------------
CREATE TABLE metodo_pago (
    id                   BIGSERIAL PRIMARY KEY,
    codigo               VARCHAR(20) NOT NULL UNIQUE,
    nombre               VARCHAR(50) NOT NULL,
    requiere_referencia  BOOLEAN     NOT NULL DEFAULT FALSE,   -- pedir N de operacion
    es_efectivo          BOOLEAN     NOT NULL DEFAULT FALSE,   -- cuenta para el cuadre de caja
    activo               BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE pago (
    id                BIGSERIAL PRIMARY KEY,
    venta_id          BIGINT        NOT NULL REFERENCES venta(id),
    caja_sesion_id    BIGINT        NOT NULL REFERENCES caja_sesion(id),  -- caja donde entro el dinero
    metodo_pago_id    BIGINT        NOT NULL REFERENCES metodo_pago(id),
    usuario_id        BIGINT        NOT NULL REFERENCES usuario(id),
    tipo              VARCHAR(10)   NOT NULL,             -- VENTA = al vender, ABONO = pago de fiado
    monto             NUMERIC(12,2) NOT NULL,
    numero_operacion  VARCHAR(50),                        -- Yape, Plin, deposito
    fecha             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    estado            VARCHAR(10)   NOT NULL DEFAULT 'VALIDO',
    CONSTRAINT ck_pago_tipo CHECK (tipo IN ('VENTA', 'ABONO')),
    CONSTRAINT ck_pago_monto CHECK (monto > 0),
    CONSTRAINT ck_pago_estado CHECK (estado IN ('VALIDO', 'ANULADO'))
);

-- ---------------------------------------------------------------------
-- 10. KARDEX: historial de TODO movimiento de stock
-- ---------------------------------------------------------------------
CREATE TABLE movimiento_inventario (
    id                BIGSERIAL PRIMARY KEY,
    producto_id       BIGINT        NOT NULL REFERENCES producto(id),
    ubicacion_id      BIGINT        NOT NULL REFERENCES ubicacion(id),
    tipo              VARCHAR(20)   NOT NULL,
    cantidad          NUMERIC(14,3) NOT NULL,          -- positivo = entra, negativo = sale (unidad base)
    saldo_resultante  NUMERIC(14,3) NOT NULL,          -- stock despues del movimiento
    costo_unitario    NUMERIC(12,4),
    compra_id         BIGINT        REFERENCES compra(id),
    venta_id          BIGINT        REFERENCES venta(id),
    traslado_id       BIGINT        REFERENCES traslado(id),
    usuario_id        BIGINT        NOT NULL REFERENCES usuario(id),
    motivo            VARCHAR(300),                    -- obligatorio en ajustes
    fecha             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    -- Nota: un traslado solo se puede anular mientras esta ENVIADO (aun no recibido);
    -- por eso ANULACION_TRASLADO siempre devuelve stock al origen (positivo).
    CONSTRAINT ck_mov_tipo CHECK (tipo IN (
        'INVENTARIO_INICIAL', 'COMPRA', 'ANULACION_COMPRA', 'VENTA', 'ANULACION_VENTA',
        'TRASLADO_SALIDA', 'TRASLADO_ENTRADA', 'ANULACION_TRASLADO',
        'AJUSTE_ENTRADA', 'AJUSTE_SALIDA')),
    CONSTRAINT ck_mov_saldo CHECK (saldo_resultante >= 0),
    -- Las entradas suman y las salidas restan
    CONSTRAINT ck_mov_signo CHECK (
        (tipo IN ('INVENTARIO_INICIAL', 'COMPRA', 'ANULACION_VENTA', 'TRASLADO_ENTRADA',
                  'ANULACION_TRASLADO', 'AJUSTE_ENTRADA') AND cantidad > 0) OR
        (tipo IN ('VENTA', 'ANULACION_COMPRA', 'TRASLADO_SALIDA', 'AJUSTE_SALIDA') AND cantidad < 0)),
    -- Cada movimiento apunta al documento que lo origino
    CONSTRAINT ck_mov_referencia CHECK (
        (tipo IN ('COMPRA', 'ANULACION_COMPRA') AND compra_id IS NOT NULL) OR
        (tipo IN ('VENTA', 'ANULACION_VENTA') AND venta_id IS NOT NULL) OR
        (tipo IN ('TRASLADO_SALIDA', 'TRASLADO_ENTRADA', 'ANULACION_TRASLADO') AND traslado_id IS NOT NULL) OR
        (tipo IN ('INVENTARIO_INICIAL', 'AJUSTE_ENTRADA', 'AJUSTE_SALIDA'))),
    CONSTRAINT ck_mov_ajuste_motivo CHECK (tipo NOT LIKE 'AJUSTE%' OR motivo IS NOT NULL)
);

-- ---------------------------------------------------------------------
-- 11. INDICES para reportes y busquedas frecuentes
-- ---------------------------------------------------------------------
CREATE INDEX ix_producto_nombre      ON producto(nombre);
CREATE INDEX ix_producto_categoria   ON producto(categoria_id);
CREATE INDEX ix_stock_ubicacion      ON stock(ubicacion_id);
CREATE INDEX ix_traslado_destino     ON traslado(destino_id, fecha_envio);
CREATE INDEX ix_traslado_det         ON traslado_detalle(traslado_id);
CREATE INDEX ix_compra_fecha         ON compra(fecha_emision);
CREATE INDEX ix_compra_proveedor     ON compra(proveedor_id);
CREATE INDEX ix_compra_det           ON compra_detalle(compra_id);
CREATE INDEX ix_venta_tienda_fecha   ON venta(ubicacion_id, fecha);
CREATE INDEX ix_venta_empresa_fecha  ON venta(empresa_id, fecha);
CREATE INDEX ix_venta_caja           ON venta(caja_sesion_id);
CREATE INDEX ix_venta_det            ON venta_detalle(venta_id);
CREATE INDEX ix_venta_det_producto   ON venta_detalle(producto_id);
CREATE INDEX ix_pago_venta           ON pago(venta_id);
CREATE INDEX ix_pago_caja            ON pago(caja_sesion_id);
CREATE INDEX ix_mov_producto_fecha   ON movimiento_inventario(producto_id, ubicacion_id, fecha);
-- Reporte de deudores: solo ventas vigentes con saldo
CREATE INDEX ix_venta_deudas         ON venta(cliente_id)
    WHERE saldo_pendiente > 0 AND estado = 'EMITIDA';
