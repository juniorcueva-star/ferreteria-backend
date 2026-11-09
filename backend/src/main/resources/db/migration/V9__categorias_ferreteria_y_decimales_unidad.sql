-- =====================================================================
-- V9: categorias reales de ferreteria y decimales por unidad de medida
-- * Categorias tomadas de la pantalla del sistema actual del cliente.
--   No se borran las de V2; se omite una si ya existe con el mismo nombre
--   (sin importar mayusculas), porque el nombre es unico.
-- * unidad_medida guarda cuantos decimales acepta y un factor sugerido,
--   para que el formulario de presentaciones los proponga al elegir la unidad.
-- =====================================================================

INSERT INTO categoria (nombre)
SELECT v.nombre
FROM (VALUES ('Pernos'), ('Tuercas'), ('Remaches pop'), ('Autoroscantes'),
             ('Tornillos'), ('Tirafones'), ('Brocas'), ('Bridas')) AS v(nombre)
WHERE NOT EXISTS (SELECT 1 FROM categoria c WHERE lower(c.nombre) = lower(v.nombre));

-- 0 = solo cantidades enteras; 3 = acepta hasta 3 decimales (kilos y metros).
ALTER TABLE unidad_medida
    ADD COLUMN decimales       INTEGER       NOT NULL DEFAULT 0,
    ADD COLUMN factor_sugerido NUMERIC(14,3),
    ADD CONSTRAINT ck_unidad_medida_decimales CHECK (decimales BETWEEN 0 AND 3),
    ADD CONSTRAINT ck_unidad_medida_factor CHECK (factor_sugerido IS NULL OR factor_sugerido > 0);

UPDATE unidad_medida SET decimales = 3 WHERE codigo IN ('KILO', 'MEDIO_KILO', 'METRO');

-- Factor tipico en unidades base. NULL = depende del producto (caja, rollo, paquete...).
UPDATE unidad_medida u SET factor_sugerido = v.factor
FROM (VALUES ('UNIDAD', 1), ('DOCENA', 12), ('MEDIO_CIENTO', 50), ('CIENTO', 100), ('CUARTO_CIENTO', 25),
             ('GRUESA', 144), ('MILLAR', 1000), ('PAR', 2), ('KILO', 1), ('MEDIO_KILO', 0.5), ('METRO', 1))
     AS v(codigo, factor)
WHERE u.codigo = v.codigo;
