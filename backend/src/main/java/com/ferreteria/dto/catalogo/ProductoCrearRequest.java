package com.ferreteria.dto.catalogo;

import com.ferreteria.entity.enums.UnidadBase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Producto nuevo con al menos una presentacion. Si ninguna se marca como principal, la primera lo sera.
 */
public record ProductoCrearRequest(
        @NotBlank @Size(max = 30) String codigo,
        @NotBlank @Size(max = 150) String nombre,
        @Size(max = 300) String descripcion,
        @Size(max = 80) String marca,
        @NotNull Long categoriaId,
        @NotNull UnidadBase unidadBase,
        @NotEmpty @Size(max = 20) List<@Valid @NotNull PresentacionRequest> presentaciones) {
}
