package com.ferreteria.dto.inventario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @param origenId el ALMACENERO puede omitirlo (sale de su almacen); el ADMIN debe indicarlo
 */
public record TrasladoRequest(
        Long origenId,
        @NotNull Long destinoId,
        @Size(max = 300) String observacion,
        @NotEmpty @Size(max = 200) List<@Valid @NotNull LineaProductoRequest> detalles) {
}
