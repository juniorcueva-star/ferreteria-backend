-- Auditoria (pedido del dueno): quien cambio precios, stock, productos, usuarios o empresas, y cuando.
-- Solo se agregan filas: nunca se editan ni se borran.
CREATE TABLE auditoria (
    id           BIGSERIAL PRIMARY KEY,
    fecha        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    usuario_id   BIGINT       REFERENCES usuario(id),
    entidad      VARCHAR(20)  NOT NULL,
    entidad_id   BIGINT,
    accion       VARCHAR(20)  NOT NULL,
    descripcion  VARCHAR(500) NOT NULL,
    CONSTRAINT ck_auditoria_entidad CHECK (entidad IN
        ('PRODUCTO', 'PRECIO', 'STOCK', 'VENTA', 'COMPRA', 'USUARIO', 'EMPRESA'))
);

CREATE INDEX idx_auditoria_fecha ON auditoria (fecha DESC);
CREATE INDEX idx_auditoria_entidad ON auditoria (entidad, entidad_id);
CREATE INDEX idx_auditoria_usuario ON auditoria (usuario_id);
