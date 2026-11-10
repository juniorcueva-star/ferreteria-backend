-- Completos (pedido del dueno): un producto se vende junto con otras piezas.
-- Ej: "Perno hexagonal 3/8 x 2 - Completo" = 1 perno + 1 tuerca 3/8 + 1 arandela 3/8. No tiene stock propio:
-- al venderlo se descuenta cada pieza.
CREATE TABLE completo (
    id            BIGSERIAL PRIMARY KEY,
    producto_id   BIGINT        NOT NULL REFERENCES producto(id),
    nombre        VARCHAR(50)   NOT NULL,
    precio_venta  NUMERIC(12,2) NOT NULL,
    activo        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_completo_nombre UNIQUE (producto_id, nombre),
    CONSTRAINT ck_completo_precio CHECK (precio_venta >= 0)
);

-- Piezas de un completo: una presentacion de un producto y su cantidad por cada completo
CREATE TABLE completo_componente (
    id               BIGSERIAL PRIMARY KEY,
    completo_id      BIGINT        NOT NULL REFERENCES completo(id) ON DELETE CASCADE,
    producto_id      BIGINT        NOT NULL,
    presentacion_id  BIGINT        NOT NULL,
    cantidad         NUMERIC(14,3) NOT NULL,
    CONSTRAINT fk_completo_comp_presentacion FOREIGN KEY (presentacion_id, producto_id)
        REFERENCES presentacion(id, producto_id),
    CONSTRAINT uq_completo_comp UNIQUE (completo_id, presentacion_id),
    CONSTRAINT ck_completo_comp_cantidad CHECK (cantidad > 0)
);
CREATE INDEX idx_completo_producto ON completo (producto_id);

-- Una linea de venta o de cotizacion es de una presentacion o de un completo (nunca de ambos).
-- En las lineas de completo: factor 1 y cantidad_base = cantidad de completos; "composicion" guarda las piezas
-- tal como eran al vender (Ej: "1 Perno (Unidad) + 1 Tuerca (Unidad)").
ALTER TABLE venta_detalle ALTER COLUMN presentacion_id DROP NOT NULL;
ALTER TABLE venta_detalle ADD COLUMN completo_id BIGINT REFERENCES completo(id);
ALTER TABLE venta_detalle ADD COLUMN composicion VARCHAR(300);
ALTER TABLE venta_detalle ADD CONSTRAINT ck_venta_det_presentacion_o_completo
    CHECK ((presentacion_id IS NULL) <> (completo_id IS NULL));

ALTER TABLE cotizacion_detalle ALTER COLUMN presentacion_id DROP NOT NULL;
ALTER TABLE cotizacion_detalle ADD COLUMN completo_id BIGINT REFERENCES completo(id);
ALTER TABLE cotizacion_detalle ADD COLUMN composicion VARCHAR(300);
ALTER TABLE cotizacion_detalle ADD CONSTRAINT ck_cotizacion_det_presentacion_o_completo
    CHECK ((presentacion_id IS NULL) <> (completo_id IS NULL));
ALTER TABLE cotizacion_detalle ADD CONSTRAINT uq_cotizacion_det_completo UNIQUE (cotizacion_id, completo_id);
