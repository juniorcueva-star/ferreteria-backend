package com.ferreteria.dto.ventas;

import com.ferreteria.entity.MetodoPago;

public record MetodoPagoResponse(Long id, String codigo, String nombre, boolean requiereReferencia,
                                 boolean efectivo, boolean activo) {

    public static MetodoPagoResponse desde(MetodoPago m) {
        return new MetodoPagoResponse(m.getId(), m.getCodigo(), m.getNombre(), m.isRequiereReferencia(),
                m.isEfectivo(), m.isActivo());
    }
}
