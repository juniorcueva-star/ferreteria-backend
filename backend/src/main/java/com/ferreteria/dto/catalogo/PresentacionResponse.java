package com.ferreteria.dto.catalogo;

import com.ferreteria.entity.Presentacion;

import java.math.BigDecimal;

public record PresentacionResponse(Long id, String nombre, BigDecimal factor, BigDecimal precioVenta,
                                   String codigoBarras, boolean principal, boolean activo) {

    public static PresentacionResponse desde(Presentacion p) {
        return new PresentacionResponse(p.getId(), p.getNombre(), p.getFactor(), p.getPrecioVenta(),
                p.getCodigoBarras(), p.isPrincipal(), p.isActivo());
    }
}
