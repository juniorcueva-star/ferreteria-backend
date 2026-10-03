package com.ferreteria.dto.catalogo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Forma de vender el producto. Ej: {"nombre":"Ciento","factor":100,"precioVenta":8.00}.
 *
 * @param factor      cuantas unidades base contiene (Ej: Ciento = 100, Medio kilo = 0.5)
 * @param precioVenta precio con IGV incluido
 * @param principal   la que se muestra por defecto; solo una por producto
 */
public record PresentacionRequest(
        @NotBlank @Size(max = 50) String nombre,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3) BigDecimal factor,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal precioVenta,
        @Size(max = 50) String codigoBarras,
        Boolean principal,
        Boolean activo) {
}
