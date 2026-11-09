-- =====================================================================
-- V8: catalogo y busqueda (fase 1 de mejoras)
-- Atributos del producto (tipo, acabado, medida), catalogo de unidades
-- de venta, abreviaturas para buscar y decimales por presentacion.
-- =====================================================================

-- Atributos separados para pernos, tornillos, arandelas, etc.
-- termino_busqueda guarda el texto normalizado (sin acentos, minusculas)
-- con cada palabra entre espacios, para buscar por inicio de palabra.
ALTER TABLE producto
    ADD COLUMN tipo             VARCHAR(60),
    ADD COLUMN acabado          VARCHAR(60),
    ADD COLUMN medida           VARCHAR(60),
    ADD COLUMN termino_busqueda VARCHAR(500) NOT NULL DEFAULT ' ';

-- Decimales que acepta la presentacion al vender (0 = solo enteros).
ALTER TABLE presentacion
    ADD COLUMN decimales INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_presentacion_decimales CHECK (decimales BETWEEN 0 AND 3);

UPDATE presentacion p
SET decimales = 3
FROM producto pr
WHERE pr.id = p.producto_id
  AND pr.unidad_base IN ('KILO', 'METRO');

-- Backfill del indice de busqueda de los productos ya cargados.
UPDATE producto
SET termino_busqueda = ' ' || translate(lower(concat_ws(' ', codigo, nombre, marca)), 'áéíóúüñ', 'aeiouun') || ' ';

-- Catalogo de unidades de venta (lo que ven en la pantalla al asignar unidades).
CREATE TABLE unidad_medida (
    id      BIGSERIAL PRIMARY KEY,
    codigo  VARCHAR(30) NOT NULL UNIQUE,
    nombre  VARCHAR(60) NOT NULL,
    activo  BOOLEAN     NOT NULL DEFAULT TRUE
);

INSERT INTO unidad_medida (codigo, nombre) VALUES
    ('UNIDAD',        'Unidad'),
    ('DOCENA',        'Docena'),
    ('MEDIO_CIENTO',  'Medio ciento'),
    ('CIENTO',        'Ciento'),
    ('CUARTO_CIENTO', 'Cuarto de ciento'),
    ('GRUESA',        'Gruesa'),
    ('MILLAR',        'Millar'),
    ('PAR',           'Par'),
    ('CAJA',          'Caja'),
    ('PAQUETE',       'Paquete'),
    ('ROLLO',         'Rollo'),
    ('JUEGO',         'Juego'),
    ('BOLSA',         'Bolsa'),
    ('GALON',         'Galon'),
    ('KILO',          'Kilo'),
    ('MEDIO_KILO',    'Medio kilo'),
    ('METRO',         'Metro');

-- Abreviaturas que usa el personal al buscar (per = perno, hex = hexagonal...).
-- Se guardan normalizadas (minusculas, sin acentos).
CREATE TABLE sinonimo_busqueda (
    id           BIGSERIAL PRIMARY KEY,
    abreviatura  VARCHAR(30) NOT NULL UNIQUE,
    termino      VARCHAR(60) NOT NULL,
    CONSTRAINT ck_sinonimo_distinto CHECK (abreviatura <> termino)
);

INSERT INTO sinonimo_busqueda (abreviatura, termino) VALUES
    ('per',       'perno'),
    ('hex',       'hexagonal'),
    ('zin',       'zincado'),
    ('galv',      'galvanizado'),
    ('tor',       'tornillo'),
    ('tuer',      'tuerca'),
    ('aran',      'arandela'),
    ('pl',        'plana'),
    ('rem',       'remache'),
    ('autorosc',  'autoroscante');
