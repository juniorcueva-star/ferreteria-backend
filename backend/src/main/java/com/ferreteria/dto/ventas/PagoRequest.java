package com.ferreteria.dto.ventas;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Pago con un metodo. Se pueden combinar varios (pago mixto).
 *
 * @param metodoPago      codigo del metodo: EFECTIVO, YAPE, PLIN, DEPOSITO, TRANSFERENCIA, TARJETA, OTRO_QR
 * @param numeroOperacion obligatorio para los metodos que no son efectivo
 */
public record PagoRequest(
        @NotBlank @Size(max = 20) String metodoPago,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 10, fraction = 2) BigDecimal monto,
        @Size(max = 50) String numeroOperacion) {
}
