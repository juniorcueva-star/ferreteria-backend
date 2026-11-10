package com.ferreteria.dto.inventario;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Donde esta un producto dentro de una ubicacion.
 *
 * @param ubicacionFisica Ej: "Pasillo 3 - Estante B"; vacio para quitarla
 */
public record UbicacionFisicaRequest(@NotNull Long productoId, Long ubicacionId,
                                     @Size(max = 40) String ubicacionFisica) {
}
