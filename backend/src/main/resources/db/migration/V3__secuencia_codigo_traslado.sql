-- =====================================================================
-- V3: correlativo para el codigo de los traslados (TR-000001, TR-000002...)
-- Una secuencia de PostgreSQL entrega numeros unicos aunque dos traslados
-- se registren al mismo tiempo. Queda "OWNED BY" la columna codigo para que
-- se elimine junto con la tabla.
-- =====================================================================
CREATE SEQUENCE traslado_codigo_seq START WITH 1 INCREMENT BY 1;
ALTER SEQUENCE traslado_codigo_seq OWNED BY traslado.codigo;
