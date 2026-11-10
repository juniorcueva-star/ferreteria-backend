package com.ferreteria.dto.catalogo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Precio por mayor de una presentacion; reemplaza las escalas que tenia (lista vacia = sin precio por mayor).
 * Ej: {"escalas":[{"cantidadMinima":100,"precio":0.40},{"cantidadMinima":500,"precio":0.35}]}.
 */
public record EscalasPrecioRequest(@NotNull @Size(max = 5) List<@Valid @NotNull Escala> escalas) {

    /**
     * @param cantidadMinima desde cuantas (en la presentacion) se cobra este precio; mayor que 1
     * @param precio         precio por presentacion con IGV; menor que el de lista
     */
    public record Escala(
            @NotNull @DecimalMin(value = "1", inclusive = false) @Digits(integer = 11, fraction = 3) BigDecimal cantidadMinima,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 10, fraction = 2) BigDecimal precio) {
    }
}
