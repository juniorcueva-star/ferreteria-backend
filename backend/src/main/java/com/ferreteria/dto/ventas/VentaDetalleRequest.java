package com.ferreteria.dto.ventas;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Linea de la venta. El precio se toma de la presentacion (no lo envia el cliente).
 *
 * @param cantidad  en la presentacion elegida (Ej: 2 millares, 2.5 kilos)
 * @param descuento monto descontado a la linea (opcional), no puede superar su importe
 * @param ajustePorcentaje descuento (negativo) o incremento (positivo), no se combina con descuento monetario
 */
public record VentaDetalleRequest(
        @NotNull Long presentacionId,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000") @Digits(integer = 11, fraction = 3)
        BigDecimal cantidad,
        @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal descuento,
        @DecimalMin("-100") @Digits(integer = 8, fraction = 4) BigDecimal ajustePorcentaje) {

    public VentaDetalleRequest(Long presentacionId, BigDecimal cantidad, BigDecimal descuento) {
        this(presentacionId, cantidad, descuento, null);
    }
}
