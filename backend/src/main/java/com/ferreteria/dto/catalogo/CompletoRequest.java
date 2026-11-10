package com.ferreteria.dto.catalogo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Completo nuevo o editado (solo ADMIN).
 *
 * @param nombre       Ej: "Completo", "Con tuerca", "Con tornillos"
 * @param precioVenta  precio con IGV por cada completo; si se omite se usa la suma de las piezas (precio sugerido)
 * @param componentes  piezas: presentacion y cantidad por cada completo (Ej: 1 perno, 1 tuerca, 1 arandela)
 */
public record CompletoRequest(
        @NotBlank @Size(max = 50) String nombre,
        @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal precioVenta,
        Boolean activo,
        @NotEmpty @Size(max = 10) List<@Valid @NotNull Componente> componentes) {

    public record Componente(
            @NotNull Long presentacionId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100000") @Digits(integer = 6, fraction = 3)
            BigDecimal cantidad) {
    }
}
