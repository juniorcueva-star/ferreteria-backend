package com.ferreteria.dto.catalogo;

import com.ferreteria.entity.HistorialPrecioPresentacion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record HistorialPrecioResponse(Long id, BigDecimal precioAnterior, BigDecimal precioNuevo, String usuario,
                                      OffsetDateTime fecha) {

    public static HistorialPrecioResponse desde(HistorialPrecioPresentacion h) {
        return new HistorialPrecioResponse(h.getId(), h.getPrecioAnterior(), h.getPrecioNuevo(),
                h.getUsuario() == null ? null : h.getUsuario().getUsername(), h.getFecha());
    }
}
