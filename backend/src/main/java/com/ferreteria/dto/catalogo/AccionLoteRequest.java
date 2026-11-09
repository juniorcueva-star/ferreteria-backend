package com.ferreteria.dto.catalogo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Accion sobre varios productos a la vez.
 *
 * @param productoIds de 1 a 200 productos
 * @param categoriaId obligatorio solo para ASIGNAR_CATEGORIA
 */
public record AccionLoteRequest(
        @NotNull @Size(min = 1, max = 200) List<@NotNull Long> productoIds,
        @NotNull AccionLote accion,
        Long categoriaId) {

    public enum AccionLote { ASIGNAR_CATEGORIA, ACTIVAR, DESACTIVAR }
}
