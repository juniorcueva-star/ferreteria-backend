package com.ferreteria.dto.encargos;

import com.ferreteria.dto.ventas.PagoRequest;
import com.ferreteria.entity.enums.CondicionVenta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Datos de la venta que se crea al entregar el encargo, como en cualquier venta.
 *
 * @param clienteId si se omite se usa el cliente del encargo (obligatorio para CREDITO)
 */
public record EntregaEncargoRequest(
        @NotNull CondicionVenta condicion,
        Long clienteId,
        LocalDate fechaVencimiento,
        @Size(max = 10) List<@Valid @NotNull PagoRequest> pagos) {
}
