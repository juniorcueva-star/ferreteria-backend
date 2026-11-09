-- =====================================================================
-- V12: cotizaciones (punto 2 del encargado)
-- Adaptadas de V5 y V6 de la rama mejoras con numeros nuevos y estos cambios:
-- * codigo correlativo COT-000001 (secuencia), cliente opcional y fecha de validez;
-- * token_publico: identificador imposible de adivinar para descargar el PDF
--   sin iniciar sesion desde el enlace que se envia por WhatsApp;
-- * la venta creada al convertir se guarda en cotizacion.venta_id (UNIQUE):
--   una cotizacion se convierte una sola vez y la tabla venta no se modifica.
-- Los precios incluyen IGV: total = subtotal + igv.
-- =====================================================================

CREATE TABLE cotizacion (
    id              BIGSERIAL PRIMARY KEY,
    codigo          VARCHAR(20)   NOT NULL UNIQUE,                 -- Ej: COT-000001
    ubicacion_id    BIGINT        NOT NULL REFERENCES ubicacion(id),
    cliente_id      BIGINT        REFERENCES cliente(id),          -- NULL = cliente sin registrar
    usuario_id      BIGINT        NOT NULL REFERENCES usuario(id),
    estado          VARCHAR(10)   NOT NULL DEFAULT 'BORRADOR',
    fecha_validez   DATE          NOT NULL,
    observacion     VARCHAR(300),
    subtotal        NUMERIC(12,2) NOT NULL,                        -- sin IGV
    igv             NUMERIC(12,2) NOT NULL,
    descuento       NUMERIC(12,2) NOT NULL DEFAULT 0,              -- informativo, ya aplicado
    total           NUMERIC(12,2) NOT NULL,
    venta_id        BIGINT        UNIQUE REFERENCES venta(id),     -- nota de venta creada al convertir
    token_publico   VARCHAR(64)   NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_cotizacion_estado CHECK (estado IN ('BORRADOR', 'EMITIDA', 'ANULADA')),
    CONSTRAINT ck_cotizacion_montos CHECK (subtotal >= 0 AND igv >= 0 AND descuento >= 0 AND total >= 0),
    CONSTRAINT ck_cotizacion_total CHECK (total = subtotal + igv),
    -- Una cotizacion convertida en venta no se puede anular
    CONSTRAINT ck_cotizacion_convertida CHECK (estado <> 'ANULADA' OR venta_id IS NULL)
);

CREATE TABLE cotizacion_detalle (
    id               BIGSERIAL PRIMARY KEY,
    cotizacion_id    BIGINT        NOT NULL REFERENCES cotizacion(id) ON DELETE CASCADE,
    producto_id      BIGINT        NOT NULL REFERENCES producto(id),
    presentacion_id  BIGINT        NOT NULL,
    descripcion      VARCHAR(200)  NOT NULL,
    cantidad         NUMERIC(14,3) NOT NULL,
    factor           NUMERIC(14,3) NOT NULL,
    cantidad_base    NUMERIC(14,3) NOT NULL,
    precio_unitario  NUMERIC(12,2) NOT NULL,                       -- con IGV, editable al cotizar
    descuento        NUMERIC(12,2) NOT NULL DEFAULT 0,
    subtotal         NUMERIC(12,2) NOT NULL,
    CONSTRAINT fk_cotizacion_det_presentacion FOREIGN KEY (presentacion_id, producto_id)
        REFERENCES presentacion(id, producto_id),
    CONSTRAINT uq_cotizacion_det_presentacion UNIQUE (cotizacion_id, presentacion_id),
    CONSTRAINT ck_cotizacion_det CHECK (cantidad > 0 AND factor > 0 AND cantidad_base > 0
                                        AND precio_unitario >= 0 AND descuento >= 0 AND subtotal >= 0),
    CONSTRAINT ck_cotizacion_det_base CHECK (cantidad_base = ROUND(cantidad * factor, 3))
);

CREATE INDEX ix_cotizacion_ubicacion_estado ON cotizacion(ubicacion_id, estado);
CREATE INDEX ix_cotizacion_cliente ON cotizacion(cliente_id);
CREATE INDEX ix_cotizacion_detalle ON cotizacion_detalle(cotizacion_id);

CREATE SEQUENCE cotizacion_codigo_seq START WITH 1 INCREMENT BY 1;
ALTER SEQUENCE cotizacion_codigo_seq OWNED BY cotizacion.codigo;
