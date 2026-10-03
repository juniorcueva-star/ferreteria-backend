package com.ferreteria.dto.catalogo;

import com.ferreteria.entity.Categoria;

public record CategoriaResponse(Long id, String nombre, boolean activo) {

    public static CategoriaResponse desde(Categoria c) {
        return new CategoriaResponse(c.getId(), c.getNombre(), c.isActivo());
    }
}
