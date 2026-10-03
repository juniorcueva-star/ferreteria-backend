package com.ferreteria.dto.inventario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Carga del stock con el que se empieza a usar el sistema. Solo se permite para productos que aun no
 * tienen movimientos en esa ubicacion.
 */
public record InventarioInicialRequest(
        @NotNull Long ubicacionId,
        @NotEmpty @Size(max = 500) List<@Valid @NotNull LineaProductoRequest> detalles) {
}
