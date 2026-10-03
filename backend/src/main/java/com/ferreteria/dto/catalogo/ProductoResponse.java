package com.ferreteria.dto.catalogo;

import com.ferreteria.entity.Producto;
import com.ferreteria.entity.enums.UnidadBase;

import java.util.List;

public record ProductoResponse(Long id, String codigo, String nombre, String descripcion, String marca,
                               Long categoriaId, String categoriaNombre, UnidadBase unidadBase, String imagenUrl,
                               boolean activo, List<PresentacionResponse> presentaciones) {

    public static ProductoResponse desde(Producto p) {
        return new ProductoResponse(p.getId(), p.getCodigo(), p.getNombre(), p.getDescripcion(), p.getMarca(),
                p.getCategoria().getId(), p.getCategoria().getNombre(), p.getUnidadBase(), p.getImagenUrl(),
                p.isActivo(), p.getPresentaciones().stream().map(PresentacionResponse::desde).toList());
    }
}
