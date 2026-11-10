package com.ferreteria.dto.catalogo;

import com.ferreteria.entity.EscalaPrecio;

import java.math.BigDecimal;

/** Desde cantidadMinima (en la presentacion) cada una cuesta precio. */
public record EscalaPrecioResponse(BigDecimal cantidadMinima, BigDecimal precio) {

    public static EscalaPrecioResponse desde(EscalaPrecio e) {
        return new EscalaPrecioResponse(e.getCantidadMinima(), e.getPrecio());
    }
}
