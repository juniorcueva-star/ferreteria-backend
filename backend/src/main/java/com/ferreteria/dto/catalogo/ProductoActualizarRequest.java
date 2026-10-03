package com.ferreteria.dto.catalogo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos editables del producto. La unidad base no se puede cambiar porque el stock ya esta guardado en ella.
 */
public record ProductoActualizarRequest(
        @NotBlank @Size(max = 30) String codigo,
        @NotBlank @Size(max = 150) String nombre,
        @Size(max = 300) String descripcion,
        @Size(max = 80) String marca,
        @NotNull Long categoriaId,
        @NotNull Boolean activo) {
}
