ALTER TABLE venta ADD COLUMN cotizacion_id BIGINT REFERENCES cotizacion(id);
ALTER TABLE venta ADD CONSTRAINT uq_venta_cotizacion UNIQUE (cotizacion_id);
