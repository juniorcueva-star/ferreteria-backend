package com.ferreteria.dto.cotizaciones;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record CotizacionRequest(Long ubicacionId, @NotNull Long clienteId, @Size(max=300) String observacion,
    @NotEmpty @Size(max=100) List<@NotNull @Valid CotizacionDetalleRequest> detalles) {}
