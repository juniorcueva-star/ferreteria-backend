package com.ferreteria.dto.caja;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * @param ubicacionId    tienda; el VENDEDOR puede omitirla (abre en su tienda), el ADMIN debe indicarla
 * @param montoApertura  sencillo con el que empieza el turno
 */
public record AperturaCajaRequest(
        Long ubicacionId,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal montoApertura) {
}
