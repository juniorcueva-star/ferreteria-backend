package com.ferreteria.dto.cotizaciones;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Cotizacion nueva o editada (solo en BORRADOR).
 *
 * @param ubicacionId  tienda; el VENDEDOR puede omitirla (su tienda), el ADMIN debe indicarla
 * @param clienteId    opcional
 * @param diasValidez  dias que vale la cotizacion desde hoy (por defecto 7)
 */
public record CotizacionRequest(
        Long ubicacionId,
        Long clienteId,
        @Min(1) @Max(90) Integer diasValidez,
        @Size(max = 300) String observacion,
        @NotEmpty @Size(max = 100) List<@Valid @NotNull CotizacionDetalleRequest> detalles) {
}
