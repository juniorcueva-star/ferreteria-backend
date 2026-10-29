package com.ferreteria.dto.catalogo;

import com.ferreteria.entity.HistorialPrecioPresentacion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record HistorialPrecioResponse(Long id, Long productoId, Long presentacionId, String presentacionNombre,
                                      BigDecimal precioAnterior, BigDecimal precioNuevo, OffsetDateTime fecha,
                                      String usuario) {
    public static HistorialPrecioResponse desde(HistorialPrecioPresentacion historial) {
        return new HistorialPrecioResponse(historial.getId(), historial.getProducto().getId(),
                historial.getPresentacion().getId(), historial.getPresentacion().getNombre(),
                historial.getPrecioAnterior(), historial.getPrecioNuevo(), historial.getFecha(),
                historial.getUsuario() == null ? null : historial.getUsuario().getUsername());
    }
}
