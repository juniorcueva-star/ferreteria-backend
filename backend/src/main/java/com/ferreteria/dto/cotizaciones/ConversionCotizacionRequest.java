package com.ferreteria.dto.cotizaciones;

import com.ferreteria.dto.ventas.PagoRequest;
import com.ferreteria.entity.enums.CondicionVenta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Datos de la nota de venta que se crea al convertir la cotizacion, como en cualquier venta.
 *
 * @param clienteId si se omite se usa el cliente de la cotizacion (obligatorio para CREDITO)
 */
public record ConversionCotizacionRequest(
        @NotNull CondicionVenta condicion,
        Long clienteId,
        LocalDate fechaVencimiento,
        @Size(max = 10) List<@Valid @NotNull PagoRequest> pagos) {
}
