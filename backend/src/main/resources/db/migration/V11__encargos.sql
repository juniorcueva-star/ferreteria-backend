-- =====================================================================
-- V11: encargos (punto 3 del encargado)
-- Cuando un cliente pide algo que no hay en ninguna ubicacion, el vendedor
-- registra un encargo con un precio propio (puede llevar un recargo de 10 %
-- o mas) para conseguirlo en otra tienda o con un proveedor. El encargo NO
-- toca el stock: al entregarlo se crea la venta y recien ahi se descuenta.
-- =====================================================================

CREATE TABLE encargo (
    id                BIGSERIAL PRIMARY KEY,
    codigo            VARCHAR(20)   NOT NULL UNIQUE,              -- Ej: EN-000001
    ubicacion_id      BIGINT        NOT NULL REFERENCES ubicacion(id),
    cliente_id        BIGINT        REFERENCES cliente(id),
    producto_id       BIGINT        NOT NULL REFERENCES producto(id),
    presentacion_id   BIGINT        NOT NULL,
    cantidad          NUMERIC(14,3) NOT NULL,
    precio_unitario   NUMERIC(12,2) NOT NULL,                     -- con IGV, por presentacion
    costo_referencia  NUMERIC(12,4),                              -- lo que cuesta conseguirlo (si se sabe)
    observacion       VARCHAR(300),
    estado            VARCHAR(10)   NOT NULL DEFAULT 'PENDIENTE',
    venta_id          BIGINT        REFERENCES venta(id),
    usuario_id        BIGINT        NOT NULL REFERENCES usuario(id),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_encargo_presentacion FOREIGN KEY (presentacion_id, producto_id)
        REFERENCES presentacion(id, producto_id),
    CONSTRAINT ck_encargo_estado CHECK (estado IN ('PENDIENTE', 'ENTREGADO', 'ANULADO')),
    CONSTRAINT ck_encargo_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_encargo_precio CHECK (precio_unitario >= 0),
    -- Regla D7: si se conoce el costo, el precio no puede ser menor
    CONSTRAINT ck_encargo_precio_costo CHECK (costo_referencia IS NULL OR precio_unitario >= costo_referencia),
    -- Un encargo entregado siempre apunta a su venta
    CONSTRAINT ck_encargo_entregado CHECK (estado <> 'ENTREGADO' OR venta_id IS NOT NULL)
);

CREATE INDEX ix_encargo_ubicacion_estado ON encargo(ubicacion_id, estado);

-- Correlativo del codigo EN-000001, igual que el de traslados (V3)
CREATE SEQUENCE encargo_codigo_seq START WITH 1 INCREMENT BY 1;
ALTER SEQUENCE encargo_codigo_seq OWNED BY encargo.codigo;
