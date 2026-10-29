CREATE TABLE cotizacion (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(40) NOT NULL UNIQUE,
    ubicacion_id BIGINT NOT NULL REFERENCES ubicacion(id),
    cliente_id BIGINT NOT NULL REFERENCES cliente(id),
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    estado VARCHAR(10) NOT NULL DEFAULT 'BORRADOR',
    moneda VARCHAR(3) NOT NULL DEFAULT 'PEN',
    observacion VARCHAR(300),
    subtotal NUMERIC(12,2) NOT NULL,
    igv NUMERIC(12,2) NOT NULL,
    descuento NUMERIC(12,2) NOT NULL DEFAULT 0,
    total NUMERIC(12,2) NOT NULL,
    CONSTRAINT ck_cotizacion_estado CHECK (estado IN ('BORRADOR', 'EMITIDA', 'ANULADA')),
    CONSTRAINT ck_cotizacion_moneda CHECK (moneda = 'PEN'),
    CONSTRAINT ck_cotizacion_montos CHECK (subtotal >= 0 AND igv >= 0 AND descuento >= 0 AND total >= 0),
    CONSTRAINT ck_cotizacion_total CHECK (total = subtotal + igv)
);

CREATE TABLE cotizacion_detalle (
    id BIGSERIAL PRIMARY KEY,
    cotizacion_id BIGINT NOT NULL REFERENCES cotizacion(id) ON DELETE CASCADE,
    producto_id BIGINT NOT NULL REFERENCES producto(id),
    presentacion_id BIGINT NOT NULL,
    descripcion VARCHAR(200) NOT NULL,
    cantidad NUMERIC(14,3) NOT NULL,
    factor NUMERIC(14,3) NOT NULL,
    cantidad_base NUMERIC(14,3) NOT NULL,
    precio_unitario NUMERIC(12,2) NOT NULL,
    descuento NUMERIC(12,2) NOT NULL DEFAULT 0,
    ajuste_porcentaje NUMERIC(12,4),
    subtotal NUMERIC(12,2) NOT NULL,
    CONSTRAINT fk_cotizacion_det_presentacion FOREIGN KEY (presentacion_id, producto_id) REFERENCES presentacion(id, producto_id),
    CONSTRAINT ck_cotizacion_det CHECK (cantidad > 0 AND factor > 0 AND cantidad_base > 0 AND precio_unitario >= 0 AND descuento >= 0 AND subtotal >= 0),
    CONSTRAINT ck_cotizacion_det_ajuste CHECK (ajuste_porcentaje IS NULL OR ajuste_porcentaje >= -100)
);

CREATE INDEX ix_cotizacion_ubicacion_fecha ON cotizacion(ubicacion_id, fecha DESC);
CREATE INDEX ix_cotizacion_cliente ON cotizacion(cliente_id);
CREATE INDEX ix_cotizacion_detalle ON cotizacion_detalle(cotizacion_id);
