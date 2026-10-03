package com.ferreteria.dto.comun;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motivo obligatorio para anular una compra, venta o traslado (queda en el historial).
 */
public record AnulacionRequest(@NotBlank @Size(min = 5, max = 250) String motivo) {
}
