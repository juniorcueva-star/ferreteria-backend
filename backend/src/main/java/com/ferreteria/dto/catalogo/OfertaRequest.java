package com.ferreteria.dto.catalogo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Oferta de una presentacion. Ej: {"precio":0.40,"desde":"2026-11-10","hasta":"2026-11-30"}.
 *
 * @param precio precio de oferta con IGV; menor que el de lista
 * @param desde  primer dia de la oferta (hora de Lima)
 * @param hasta  ultimo dia de la oferta, inclusive; no anterior a hoy
 */
public record OfertaRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 10, fraction = 2) BigDecimal precio,
        @NotNull LocalDate desde,
        @NotNull LocalDate hasta) {
}
