package com.ferreteria.dto.ventas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Abono a una venta al fiado. Puede combinar metodos (Ej: parte en efectivo y parte por Yape).
 * La suma no puede superar el saldo pendiente.
 */
public record AbonoRequest(@NotEmpty @Size(max = 10) List<@Valid @NotNull PagoRequest> pagos) {
}
