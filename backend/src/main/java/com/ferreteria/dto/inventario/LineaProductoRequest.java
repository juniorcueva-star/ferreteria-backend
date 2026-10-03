package com.ferreteria.dto.inventario;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Producto y cantidad para traslados y ajustes.
 *
 * @param presentacionId opcional; si se omite la cantidad esta en unidad base
 */
public record LineaProductoRequest(
        @NotNull Long productoId,
        Long presentacionId,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000") @Digits(integer = 11, fraction = 3)
        BigDecimal cantidad) {
}
