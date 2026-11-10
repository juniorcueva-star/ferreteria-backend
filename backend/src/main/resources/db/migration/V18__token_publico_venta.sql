-- Enlace publico del PDF de la nota de venta para enviarlo por WhatsApp (igual que la cotizacion): un token imposible
-- de adivinar que se crea la primera vez que se pide el enlace.
ALTER TABLE venta ADD COLUMN token_publico VARCHAR(64) UNIQUE;
