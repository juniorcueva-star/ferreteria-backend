package com.ferreteria.dto.inventario;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * @param stockMinimo en unidad base; por debajo o igual a este valor el producto aparece en "stock bajo"
 */
public record StockMinimoRequest(
        @NotNull Long productoId,
        @NotNull Long ubicacionId,
        @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal stockMinimo) {
}
