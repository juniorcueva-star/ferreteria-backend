package com.ferreteria.dto.encargos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Encargo de un producto sin stock.
 *
 * @param ubicacionId     tienda; el VENDEDOR puede omitirla (su tienda), el ADMIN debe indicarla
 * @param precioUnitario  precio acordado con IGV por presentacion (puede llevar recargo)
 * @param costoReferencia lo que cuesta conseguirlo; si se omite se usa el costo de la ultima compra (si existe).
 *                        El precio no puede ser menor que el costo
 */
public record EncargoRequest(
        Long ubicacionId,
        Long clienteId,
        @NotNull Long presentacionId,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000") @Digits(integer = 11, fraction = 3)
        BigDecimal cantidad,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal precioUnitario,
        @DecimalMin("0") @Digits(integer = 8, fraction = 4) BigDecimal costoReferencia,
        @Size(max = 250) String observacion) {
}
