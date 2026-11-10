-- Pedidos de reposicion (mejora de tienda y almacen): la tienda pide al almacen los productos que se le acaban;
-- el almacenero lo atiende con un traslado (puede enviar menos) o lo rechaza. El pedido no mueve stock: lo mueve el
-- traslado.
CREATE SEQUENCE solicitud_reposicion_codigo_seq;

CREATE TABLE solicitud_reposicion (
    id              BIGSERIAL PRIMARY KEY,
    codigo          VARCHAR(20)  NOT NULL UNIQUE,                 -- Ej: SR-000001
    ubicacion_id    BIGINT       NOT NULL REFERENCES ubicacion(id), -- tienda que pide
    estado          VARCHAR(10)  NOT NULL DEFAULT 'PENDIENTE',
    observacion     VARCHAR(300),
    usuario_id      BIGINT       NOT NULL REFERENCES usuario(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    atendida_por    BIGINT       REFERENCES usuario(id),
    atendida_at     TIMESTAMPTZ,
    motivo          VARCHAR(300),                                -- de rechazo o anulacion
    traslado_id     BIGINT       UNIQUE REFERENCES traslado(id),
    CONSTRAINT ck_solicitud_estado CHECK (estado IN ('PENDIENTE', 'ATENDIDA', 'RECHAZADA', 'ANULADA')),
    CONSTRAINT ck_solicitud_atendida CHECK (estado <> 'ATENDIDA' OR traslado_id IS NOT NULL)
);
CREATE INDEX idx_solicitud_estado ON solicitud_reposicion (estado, ubicacion_id);

-- Cantidades en unidad base del producto
CREATE TABLE solicitud_reposicion_detalle (
    id             BIGSERIAL PRIMARY KEY,
    solicitud_id   BIGINT        NOT NULL REFERENCES solicitud_reposicion(id) ON DELETE CASCADE,
    producto_id    BIGINT        NOT NULL REFERENCES producto(id),
    cantidad       NUMERIC(14,3) NOT NULL,
    CONSTRAINT uq_solicitud_producto UNIQUE (solicitud_id, producto_id),
    CONSTRAINT ck_solicitud_cantidad CHECK (cantidad > 0)
);
