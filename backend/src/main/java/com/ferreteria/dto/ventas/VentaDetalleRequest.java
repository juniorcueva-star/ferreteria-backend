package com.ferreteria.dto.ventas;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Linea de la venta: de una presentacion o de un completo (solo uno). El precio se toma de la lista (no lo envia
 * el cliente).
 *
 * @param completoId completo del producto (Ej: perno + tuerca + arandela); se vende por unidades enteras
 * @param cantidad  en la presentacion elegida (Ej: 2 millares, 2.5 kilos)
 * @param descuento monto descontado a la linea (opcional), no puede superar su importe
 */
public record VentaDetalleRequest(
        Long presentacionId,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000") @Digits(integer = 11, fraction = 3)
        BigDecimal cantidad,
        @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal descuento,
        Long completoId) {

    public VentaDetalleRequest(Long presentacionId, BigDecimal cantidad, BigDecimal descuento) {
        this(presentacionId, cantidad, descuento, null);
    }
}
