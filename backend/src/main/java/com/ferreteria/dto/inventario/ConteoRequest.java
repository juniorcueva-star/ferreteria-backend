package com.ferreteria.dto.inventario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inventario fisico: lo que se conto de cada producto en una ubicacion (unidad base). El sistema crea los ajustes
 * por las diferencias.
 *
 * @param ubicacionId el ALMACENERO la omite (su almacen); el ADMIN la indica
 * @param observacion se agrega al motivo de los ajustes (Ej: "Conteo de fin de mes")
 */
public record ConteoRequest(
        Long ubicacionId,
        @Size(max = 200) String observacion,
        @NotEmpty @Size(max = 300) List<@Valid @NotNull Linea> detalles) {

    public record Linea(
            @NotNull Long productoId,
            @NotNull @DecimalMin("0") @DecimalMax("1000000") @Digits(integer = 11, fraction = 3) BigDecimal cantidadContada) {
    }
}
