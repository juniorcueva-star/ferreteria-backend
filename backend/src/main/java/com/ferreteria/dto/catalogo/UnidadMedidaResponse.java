package com.ferreteria.dto.catalogo;

import com.ferreteria.entity.UnidadMedida;

import java.math.BigDecimal;

/**
 * Unidad de venta para armar presentaciones.
 *
 * @param decimales      cuantos decimales acepta (0 = solo enteros)
 * @param factorSugerido unidades base que suele contener; null si depende del producto (caja, rollo...)
 */
public record UnidadMedidaResponse(Long id, String codigo, String nombre, int decimales, BigDecimal factorSugerido) {

    public static UnidadMedidaResponse desde(UnidadMedida unidad) {
        return new UnidadMedidaResponse(unidad.getId(), unidad.getCodigo(), unidad.getNombre(), unidad.getDecimales(),
                unidad.getFactorSugerido());
    }
}
