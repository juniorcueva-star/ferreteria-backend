-- Sprint 17.3: el historial empieza al aplicar esta migracion; no se inventan precios previos.
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

-- NULL conserva la semantica historica: la linea no uso ajuste porcentual.
-- Negativo = descuento; positivo = incremento. El descuento no puede superar 100 %.
ALTER TABLE venta_detalle
    ADD COLUMN ajuste_porcentaje NUMERIC(12,4),
    ADD CONSTRAINT ck_venta_det_ajuste_porcentaje CHECK (ajuste_porcentaje IS NULL OR ajuste_porcentaje >= -100);
