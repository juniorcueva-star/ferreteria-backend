package com.ferreteria.dto.reposicion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pedido de reposicion de una tienda al almacen.
 *
 * @param ubicacionId tienda que pide; el VENDEDOR la omite (su tienda), el ADMIN la indica
 * @param detalles    productos y cantidades en unidad base
 */
public record SolicitudReposicionRequest(
        Long ubicacionId,
        @Size(max = 300) String observacion,
        @NotEmpty @Size(max = 100) List<@Valid @NotNull Linea> detalles) {

    public record Linea(
            @NotNull Long productoId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000") @Digits(integer = 11, fraction = 3)
            BigDecimal cantidad) {
    }
}
