-- Ofertas (pedido del dueno): una presentacion puede tener un precio de oferta entre dos fechas (inclusive, hora de
-- Lima). Mientras esta vigente, la caja y la cotizacion cobran ese precio y lo marcan como OFERTA.
ALTER TABLE presentacion
    ADD COLUMN precio_oferta NUMERIC(12,2),
    ADD COLUMN oferta_desde  DATE,
    ADD COLUMN oferta_hasta  DATE;

ALTER TABLE presentacion
    ADD CONSTRAINT ck_presentacion_oferta_completa CHECK (
        (precio_oferta IS NULL AND oferta_desde IS NULL AND oferta_hasta IS NULL)
        OR (precio_oferta IS NOT NULL AND oferta_desde IS NOT NULL AND oferta_hasta IS NOT NULL)),
    ADD CONSTRAINT ck_presentacion_oferta_precio CHECK (precio_oferta IS NULL OR precio_oferta > 0),
    ADD CONSTRAINT ck_presentacion_oferta_fechas CHECK (oferta_desde IS NULL OR oferta_desde <= oferta_hasta);

-- Para listar rapido las ofertas vigentes
CREATE INDEX ix_presentacion_oferta ON presentacion(oferta_desde, oferta_hasta) WHERE precio_oferta IS NOT NULL;
