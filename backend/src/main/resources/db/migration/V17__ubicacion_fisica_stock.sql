-- Ubicacion fisica de cada producto dentro de una ubicacion (Ej: "Pasillo 3 - Estante B"), para encontrarlo rapido
-- en el almacen o en la trastienda.
ALTER TABLE stock ADD COLUMN ubicacion_fisica VARCHAR(40);
