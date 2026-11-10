-- Precio por mayor (pedido del dueno): una presentacion puede tener escalas por cantidad.
-- Ej: Unidad a S/ 0.50; desde 100 a S/ 0.40; desde 500 a S/ 0.35. Se aplica la escala mas alta que se alcanza.
CREATE TABLE escala_precio (
    id              BIGSERIAL PRIMARY KEY,
    presentacion_id BIGINT        NOT NULL REFERENCES presentacion(id) ON DELETE CASCADE,
    cantidad_minima NUMERIC(14,3) NOT NULL,             -- en la presentacion (Ej: 100 unidades, 3 cajas)
    precio          NUMERIC(12,2) NOT NULL,             -- precio por presentacion con IGV
    CONSTRAINT uq_escala_precio UNIQUE (presentacion_id, cantidad_minima),
    CONSTRAINT ck_escala_cantidad CHECK (cantidad_minima > 1),
    CONSTRAINT ck_escala_precio CHECK (precio > 0)
);

CREATE INDEX ix_escala_precio_presentacion ON escala_precio(presentacion_id);

-- De donde salio el precio de cada linea: lista, por mayor u oferta (la oferta llega en la siguiente migracion);
-- se imprime en la nota y la cotizacion para que el cliente vea el beneficio.
ALTER TABLE venta_detalle ADD COLUMN tipo_precio VARCHAR(10) NOT NULL DEFAULT 'LISTA'
    CONSTRAINT ck_venta_det_tipo_precio CHECK (tipo_precio IN ('LISTA', 'MAYOR', 'OFERTA'));
ALTER TABLE cotizacion_detalle ADD COLUMN tipo_precio VARCHAR(10) NOT NULL DEFAULT 'LISTA'
    CONSTRAINT ck_cotizacion_det_tipo_precio CHECK (tipo_precio IN ('LISTA', 'MAYOR', 'OFERTA'));
