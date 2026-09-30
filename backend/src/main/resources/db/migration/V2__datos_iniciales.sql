-- =====================================================================
-- Sistema de Gestion Ferretera - Datos iniciales (Flyway V2)
-- Catalogos fijos que el sistema necesita para funcionar
-- =====================================================================

INSERT INTO metodo_pago (codigo, nombre, requiere_referencia, es_efectivo) VALUES
    ('EFECTIVO',      'Efectivo',               FALSE, TRUE),
    ('YAPE',          'Yape',                   TRUE,  FALSE),
    ('PLIN',          'Plin',                   TRUE,  FALSE),
    ('DEPOSITO',      'Deposito bancario',      TRUE,  FALSE),
    ('TRANSFERENCIA', 'Transferencia bancaria', TRUE,  FALSE),
    ('TARJETA',       'Tarjeta debito/credito', TRUE,  FALSE),
    ('OTRO_QR',       'Otro pago QR',           TRUE,  FALSE);

INSERT INTO categoria (nombre) VALUES
    ('Pernos y tuercas'),
    ('Clavos y tornillos'),
    ('Bisagras y cerrajeria'),
    ('Cadenas y cables'),
    ('Herramientas'),
    ('Electricidad'),
    ('Gasfiteria'),
    ('Pinturas'),
    ('Otros');
