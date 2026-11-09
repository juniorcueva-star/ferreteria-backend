-- Datos de la empresa que se imprimen en la cotizacion y en la nota de venta (pedido del dueno):
-- telefono o WhatsApp, lema (Ej: "Pernos y afines") y cuentas bancarias para depositos.
ALTER TABLE empresa ADD COLUMN telefono VARCHAR(60);
ALTER TABLE empresa ADD COLUMN lema VARCHAR(100);
ALTER TABLE empresa ADD COLUMN datos_bancarios VARCHAR(300);
