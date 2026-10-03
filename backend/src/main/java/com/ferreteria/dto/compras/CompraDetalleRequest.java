package com.ferreteria.dto.compras;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Linea de la compra.
 *
 * @param presentacionId opcional; si se omite, la cantidad esta en unidad base (unidades, kilos o metros)
 * @param cantidad       cantidad comprada en esa presentacion (Ej: 3 cajas)
 * @param precioUnitario precio pagado por cada presentacion (Ej: S/ 45.00 por caja), tal como figura en el comprobante
 */
public record CompraDetalleRequest(
        @NotNull Long productoId,
        Long presentacionId,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000") @Digits(integer = 11, fraction = 3)
        BigDecimal cantidad,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 4) BigDecimal precioUnitario) {
}
