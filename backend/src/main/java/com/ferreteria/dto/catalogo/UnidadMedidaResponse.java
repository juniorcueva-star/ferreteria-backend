package com.ferreteria.dto.catalogo;

import com.ferreteria.entity.UnidadMedida;

public record UnidadMedidaResponse(Long id, String codigo, String nombre) {

    public static UnidadMedidaResponse desde(UnidadMedida unidad) {
        return new UnidadMedidaResponse(unidad.getId(), unidad.getCodigo(), unidad.getNombre());
    }
}
