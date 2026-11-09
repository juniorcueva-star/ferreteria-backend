-- =====================================================================
-- V10: precio sugerido por presentacion e historial de precios
-- * El precio comercial de cada presentacion lo decide el negocio, pero el
--   sistema propone uno: costo de referencia x (1 + ganancia %) + adicional.
--   costo_referencia = costo por unidad base de la ultima compra x factor;
--   se actualiza al registrar cada compra. precio_sugerido es informativo:
--   el precio final (precio_venta) solo cambia cuando el usuario lo confirma.
-- * historial_precio_presentacion guarda cada cambio del precio final
--   (adaptada de la V4 de la rama mejoras, sin sus cambios en venta_detalle).
-- =====================================================================

ALTER TABLE presentacion
    ADD COLUMN costo_referencia    NUMERIC(12,4),
    ADD COLUMN porcentaje_ganancia NUMERIC(8,4),
    ADD COLUMN monto_adicional     NUMERIC(12,2),
    ADD COLUMN precio_sugerido     NUMERIC(12,2),
    ADD CONSTRAINT ck_presentacion_costo_ref CHECK (costo_referencia IS NULL OR costo_referencia >= 0),
    ADD CONSTRAINT ck_presentacion_ganancia CHECK (porcentaje_ganancia IS NULL OR porcentaje_ganancia >= 0),
    ADD CONSTRAINT ck_presentacion_adicional CHECK (monto_adicional IS NULL OR monto_adicional >= 0),
    ADD CONSTRAINT ck_presentacion_sugerido CHECK (precio_sugerido IS NULL OR precio_sugerido >= 0);

-- Costo de referencia inicial: ultima compra registrada de cada producto (si la hay)
UPDATE presentacion p
SET costo_referencia = ROUND(u.costo_unitario * p.factor, 4)
FROM (
    SELECT DISTINCT ON (d.producto_id) d.producto_id, d.costo_unitario
    FROM compra_detalle d
    JOIN compra c ON c.id = d.compra_id
    WHERE c.estado = 'REGISTRADA'
    ORDER BY d.producto_id, c.created_at DESC, d.id DESC
) u
WHERE u.producto_id = p.producto_id;

-- El historial empieza al aplicar esta migracion: no se inventan precios previos
CREATE TABLE historial_precio_presentacion (
    id                BIGSERIAL PRIMARY KEY,
    producto_id       BIGINT        NOT NULL,
    presentacion_id   BIGINT        NOT NULL,
    precio_anterior   NUMERIC(12,2) NOT NULL,
    precio_nuevo      NUMERIC(12,2) NOT NULL,
    usuario_id        BIGINT        REFERENCES usuario(id),
    fecha             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_historial_precio_presentacion FOREIGN KEY (presentacion_id, producto_id)
        REFERENCES presentacion(id, producto_id),
    CONSTRAINT ck_historial_precio_distinto CHECK (precio_anterior <> precio_nuevo),
    CONSTRAINT ck_historial_precio_no_negativo CHECK (precio_anterior >= 0 AND precio_nuevo >= 0)
);

CREATE INDEX ix_historial_precio_presentacion_fecha
    ON historial_precio_presentacion(presentacion_id, fecha DESC);
