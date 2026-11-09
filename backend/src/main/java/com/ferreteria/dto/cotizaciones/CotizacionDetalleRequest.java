package com.ferreteria.dto.cotizaciones;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Linea de la cotizacion.
 *
 * @param precioUnitario precio con IGV por presentacion; si se omite se usa el precio de lista
 * @param descuento      monto descontado a la linea (opcional)
 */
public record CotizacionDetalleRequest(
        @NotNull Long presentacionId,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000") @Digits(integer = 11, fraction = 3)
        BigDecimal cantidad,
        @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal precioUnitario,
        @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal descuento) {
}
