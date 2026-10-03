package com.ferreteria.dto.caja;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * @param efectivoContado dinero en efectivo que el vendedor cuenta fisicamente al cerrar
 */
public record CierreCajaRequest(
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal efectivoContado) {
}
